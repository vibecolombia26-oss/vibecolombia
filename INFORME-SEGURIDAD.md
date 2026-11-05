# Informe de Seguridad — Tarea 1

Sesión: blindaje de rutas públicas, rotación de credenciales y CSRF.
Estado: **código aplicado y compilando** (`mvn compile` → `BUILD SUCCESS`).
Pendiente de tu lado: rotar la contraseña en Neon y definir las variables de entorno.

---

## 1. Resumen ejecutivo

Se cerraron las 6 brechas detectadas. Las tres críticas eran:

1. **`.anyRequest().permitAll()`**: la seguridad era una lista negra. Cualquier
   ruta, controlador o endpoint nuevo nacía público por defecto.
2. **`/api/chat/todos` público**: exponía **todos** los mensajes de clientes con
   sus números de teléfono a cualquiera que abriera la URL.
3. **Contraseña del admin con valor por defecto en el repositorio**: si
   `ADMIN_PASSWORD` no estaba definida en el hosting, el panel quedaba accesible
   con una contraseña que está publicada en GitHub.

Además: CSRF estaba **desactivado** y había **tres borrados por `GET`**
(`/admin/eliminar/{id}`, `/admin/eliminar-resena/{id}`, `/admin/eliminar-pedido/{id}`),
es decir, un enlace o una imagen en un correo podían borrar productos con la
sesión del admin abierta.

---

## 2. Cambios aplicados

| Archivo | Cambio |
|---|---|
| `config/SecurityConfig.java` | Reescrito: lista blanca explícita, `anyRequest().authenticated()`, CSRF activo, cabeceras de seguridad (CSP, HSTS, `frame-ancestors 'none'`, `Referrer-Policy`), sesión de 30 min con `migrateSession` + `maximumSessions(1)`, logout real, `admin.password` sin valor por defecto, 401 JSON para `/api/**` |
| `config/RateLimitFilter.java` | **Nuevo.** Límite por IP en memoria: login 8/min, alta de pedidos 10/min, seguimiento y chat 30/min. Cero dependencias nuevas, cero consultas a Neon |
| `PedidoController.java` | Código de pedido aleatorio (`SecureRandom`, 32¹⁰ combinaciones) en lugar de `FLOW-<epoch ms>`, que era enumerable. Los pedidos antiguos siguen consultándose igual |
| `AdminController.java` | `eliminar-pedido` pasa de `GET` a `POST`; `/admin/chats` deja de enviar la tabla completa de mensajes al modelo (era una consulta duplicada e inútil: la vista ya los pide por AJAX) |
| `ProductoController.java` | `eliminar` y `eliminar-resena` pasan de `GET` a `POST` |
| `application.properties` | `admin.password=${ADMIN_PASSWORD}` (sin fallback), `forward-headers-strategy=framework`, sesión 30 min, cookies `HttpOnly` + `SameSite=Lax` + `Secure` configurable |
| `admin-layout.html` | Metas con el token CSRF + helper `window.adminFetch` (añade la cabecera CSRF a todo `fetch` del panel). Soporte `data-confirm-form` en el modal. **Botón "Salir"** en la topbar (antes no había forma de cerrar sesión) |
| `admin-chats.html` | Usa `adminFetch` y muestra "sesión expirada" si el backend responde 401 |
| `admin-form.html` / `admin-panel.html` / `admin-pedidos.html` | Los 4 borrados pasan de `<a>` a `<form method="post">` con token CSRF |
| `admin-login.html` | Token CSRF en el formulario, aviso de bloqueo por exceso de intentos |
| `.env.example` | **Nuevo.** Plantilla de variables de entorno |
| `.gitignore` | `.env` ignorado. Se corrigió además el archivo, que tenía bytes NUL y por eso `data/` no se estaba aplicando |

---

## 3. Lo que tienes que hacer tú (en este orden)

### 3.1 Antes de desplegar: definir `ADMIN_PASSWORD`

La aplicación **ya no arranca** sin esa variable (fail-fast intencionado). En el
hosting, añade:

```
ADMIN_PASSWORD = <contraseña larga y única>
SESSION_COOKIE_SECURE = true     # porque sirves por HTTPS
```

Si despliegas sin ella verás `Could not resolve placeholder 'admin.password'`.
Es el comportamiento correcto: mejor eso que un panel con contraseña pública.

### 3.2 Rotar la contraseña de Neon

1. Neon Console → tu proyecto → **Roles** → `neondb_owner` → **Reset password**.
2. Copia la nueva contraseña y actualiza `DATABASE_PASSWORD` en el hosting.
3. Redeploy. La app reconecta sola (Hikari valida cada conexión).

> La contraseña anterior (`npg_...`) sigue en el historial de git de un repo
> público, así que **hay que considerarla comprometida**. Rotarla la inutiliza.
> Si además quieres limpiar el historial: `git filter-repo --path src/main/resources/application.properties --invert-paths`
> (o BFG) y luego `git push --force-with-lease`.

### 3.3 Recomendado

- No dejes el SQL Editor de Neon abierto (mantiene la compute despierta).
- Si algún día añades un monitor de disponibilidad, apúntalo a `/health`.

---

## 4. Matriz de acceso (después del cambio)

| Ruta | Método | Acceso |
|---|---|---|
| `/`, `/producto/**`, `/carrito`, `/envios`, `/seguimiento`, `/privacidad`, `/terminos`, `/contacto` | GET | Público |
| `/css/**`, `/js/**`, `/images/**`, `/favicon.ico` | GET | Público |
| `/health` | GET | Público (no toca la BD) |
| `/admin-login` | GET | Público |
| `/admin/login` | POST | Público + CSRF + 8 intentos/min |
| `/api/pedido` | POST | Público (sin CSRF) + 10/min |
| `/api/pedido/**`, `/api/chat/mensajes` | GET | Público + 30/min |
| `/api/chat/enviar`, `/api/variante/detalle`, `/api/asistente` | POST | Público (sin CSRF) + 30/min |
| **`/api/chat/todos`** | GET | **Solo ADMIN** |
| **`/admin/**`** | * | **Solo ADMIN** |
| Cualquier otra ruta | * | Autenticado · 401 JSON si es `/api/**`, redirección al login si no |

---

## 5. Cómo verificar (con la app corriendo)

```powershell
# La bandeja de consultas ya NO es pública → 401
Invoke-WebRequest http://localhost:8080/api/chat/todos -SkipHttpErrorCheck | Select-Object StatusCode

# El panel exige sesión → 302 al login
Invoke-WebRequest http://localhost:8080/admin/panel -SkipHttpErrorCheck | Select-Object StatusCode

# La tienda sigue abierta → 200
Invoke-WebRequest http://localhost:8080/ -SkipHttpErrorCheck | Select-Object StatusCode

# Consulta de seguimiento pública → 200/404 con JSON
Invoke-WebRequest "http://localhost:8080/api/pedido/FLOW-XXXXXXXXXX" -SkipHttpErrorCheck | Select-Object StatusCode

# Un borrado por GET ya no existe → 405
Invoke-WebRequest http://localhost:8080/admin/eliminar/1 -SkipHttpErrorCheck | Select-Object StatusCode
```

En el navegador, con sesión de admin abierta:

1. `Panel → Productos → Eliminar` → abre el modal, confirma y **borra** (si el
   token CSRF fallara, verías un 403).
2. `Productos → Eliminar producto` **dentro de la ficha de edición** → mismo
   resultado (usa el formulario hermano + `data-confirm-form`).
3. `Consultas → Enviar` una respuesta → debe guardar sin 403.
4. `Salir` (topbar) → vuelve al login.
5. Escribe mal la contraseña 9 veces → a partir de la novena verás el aviso de
   bloqueo temporal.

---

## 6. Decisiones y límites conocidos

- **Los 4 endpoints públicos de escritura quedan exentos de CSRF.** Son una API
  sin estado y sin privilegios que consume el propio frontend; exigirles token
  rompería checkout, chat y asistente. No permiten actuar "en nombre" del admin.
  Si en el futuro alguno requiere sesión, hay que añadirlo a la lista blanca de
  CSRF automáticamente (ya no estaría exento).
- **CSP con `'unsafe-inline'`**: las plantillas usan `onclick=` y `style=`
  inline. La CSP ya bloquea scripts, estilos, frames y conexiones externas, pero
  la protección contra XSS inline no es total. Migrar esos handlers a
  `flow-*.js` permitiría quitar `'unsafe-inline'` (tarea futura, no urgente).
- **El limitador por IP es por proceso**: si algún día hay 2+ instancias, cada
  una lleva su cuenta. Para una tienda con un solo contenedor es suficiente.
- **IP real detrás del proxy**: se apoya en `server.forward-headers-strategy=framework`.
  Si observas que todos los visitantes comparten límite, es que el proxy no está
  reenviando `X-Forwarded-For`; avísame y lo ajusto.
- **Códigos de pedido nuevos**: formato `FLOW-` + 10 caracteres sin `0/O/1/I`.
  Los que ya existen conservan su código y siguen localizándose igual.

---

## 7. Rollback

Todo está en el árbol de trabajo y compila:

```powershell
git checkout -- src/main/java/com/flowcolombia/flowcolombia/config/SecurityConfig.java
```

Los archivos nuevos (`RateLimitFilter.java`, `.env.example`) se borran sin más.
Si prefieres desactivar solo el límite de peticiones, borra
`config/RateLimitFilter.java` y recompila: el resto sigue funcionando.

---

## 8. Enganche con las tareas 2 y 3

- **Asistente IA**: ya trae su propio límite (30/min por IP) dentro de
  `AsistenteController`, así que no necesita nada más. El endpoint queda público
  en la lista blanca de arriba; si añades nuevas rutas del asistente, hay que
  declararlas explícitamente o quedarán protegidas (que es el comportamiento
  correcto).
- **Módulo de rentabilidad**: los nuevos endpoints de admin (costos, márgenes)
  nacerán **protegidos automáticamente** por `anyRequest().authenticated()` +
  `/admin/**`. No hay que tocar `SecurityConfig` para eso.
