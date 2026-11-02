# Informe: consumo de cómputo de Neon (FlowColombia)

Fecha: revisión sobre el código de `vibecolombia` (rama actual, HEAD `b1f414c` + cambios sin commitear).

---

## 1. Resumen

El problema **no es que la app haga muchas ventas**: es que la base de datos **nunca se
duerme**. El plan gratuito de Neon solo suspende la compute cuando la base pasa 5 minutos
**sin actividad**, y esta aplicación mantiene una conexión abierta de forma permanente.
Resultado: la compute está encendida 24/7 y las 100 CU-hours del mes se agotan en ~2 semanas.

Ya dejé aplicadas las correcciones de código de mayor impacto (sección 6). Falta un ajuste
de 2 minutos en la consola de Neon (sección 7), que es tan importante como el código.

---

## 2. Cómo se cobra Neon (la matemática del problema)

Plan Free (según la documentación oficial):

| Recurso | Límite |
|---|---|
| Cómputo | **100 CU-hours por proyecto al mes** |
| Scale to zero | automático tras **5 min** de inactividad (no se puede desactivar en Free) |
| Autoscaling | hasta 2 CU |

La cuenta es simple: **CU-hours = horas encendidas × CU de la máquina**.

| Escenario | CU-hours / mes | Se agota en |
|---|---|---|
| Encendida 24/7 a 0.25 CU | 720 h × 0.25 = **180** | ~16 días |
| Encendida 24/7 a 1 CU | 720 h × 1 = **720** | ~4 días |
| Encendida 2 h/día a 0.25 CU | 60 h × 0.25 = **15** | nunca |

Los ~16 días del primer escenario coinciden exactamente con tu síntoma ("se acaba antes de
completar el mes"). Es la firma clásica de un pool de conexiones que mantiene la base despierta.

**Y ojo con el detalle importante:** Neon cobra también los 5 minutos de "cola" después de
cada consulta. Por eso un *ping* cada 30 segundos no cuesta 30 segundos: cuesta **las 24 horas
del día encendidas**.

---

## 3. Causa principal: el pool de conexiones nunca llega a cero

`src/main/resources/application.properties` (antes de los cambios):

```properties
spring.datasource.hikari.maximum-pool-size=3
spring.datasource.hikari.minimum-idle=1      # ← PROBLEMA
```

HikariCP mantiene siempre `minimum-idle` conexiones abiertas y **solo cierra las que sobran
por encima de ese número**. Con `minimum-idle=1` hay una conexión abierta las 24 horas: Neon
ve actividad permanente, no se suspende y consume ~180 CU-hours al mes.

Es probablemente lo que cambió con la "actualización": en cuanto el hosting dejó de dormir la
app (o se cambió de plan/servicio), ese `minimum-idle=1` pasó de ser irrelevante a ser la
causa del agotamiento.

---

## 4. Segunda causa: sondeos (polling) automáticos al chat

Estos bucles se ejecutan **mientras la pestaña esté abierta**, sin que nadie haga nada:

| Archivo | Qué hacía | Frecuencia | Coste |
|---|---|---|---|
| `templates/admin-chats.html` | `GET /api/chat/todos` → `findAll()` de **toda** la tabla de mensajes | cada **10 s** | ~8.640 consultas/día. Basta con dejar el panel abierto para que Neon no se duerma nunca |
| `templates/index.html` | `GET /api/chat/mensajes` tras abrir el chat | cada **3 s**, sin parar ni al cerrar el chat | 20 consultas/min por visitante con el chat abierto |

Cada uno de esos pings **reinicia el contador de 5 minutos** de Neon.

---

## 5. Tercera causa: consultas de más en cada visita

No todas son graves, pero cada consulta extra es un *round-trip* más contra una base remota
(en Neon cada conexión nueva implica handshake TLS + arranque de backend).

| Pantalla | Consultas antes | Detalle |
|---|---|---|
| Home `/` | **4** | `findAll()` de TODA la tabla de productos solo para sacar 4 categorías distintas; `findBySku("2169621")` de un producto destacado **que la plantilla no usa**; `count(*)` total **que la plantilla no usa** |
| Ficha `/producto/{id}` | **4** | `findDetalleById` + `findWithVariantes` (ejecutada solo para "calentar" variantes) + reseñas aprobadas + carga perezosa de la colección `resenas` solo para calcular promedio/nº de estrellas |
| `/api/pedido/{codigo}` y `/api/pedido/telefono/{x}` | 1 pero **brutal** | `pedidoRepository.findAll()` + filtrado en Java: descargaba **toda** la tabla de pedidos cada vez que un cliente consulta su estado |
| `/api/variante/detalle` | **2** | `findBySku` + carga perezosa de `variante.getProducto()` |
| `/admin/chats` | `findAll()` | tabla de mensajes completa |
| Arranque (cada despliegue) | **1 + 2N** | `DataMigrationRunner` recorría todos los productos y hacía 2 consultas por producto |

---

## 6. Cambios que ya apliqué

Compila correctamente (`mvn compile` → `BUILD SUCCESS`). Todo es reversible con `git diff`.

### 6.1 `application.properties` — el arreglo decisivo

```properties
spring.datasource.hikari.minimum-idle=0          # el pool cierra TODO al quedar inactivo
spring.datasource.hikari.maximum-pool-size=3
spring.datasource.hikari.idle-timeout=30000      # 30 s y la conexión se cierra
spring.datasource.hikari.keepalive-time=0        # sin pings que despierten la base
spring.datasource.hikari.connection-timeout=30000

spring.jpa.properties.hibernate.jdbc.batch_size=20
spring.jpa.properties.hibernate.order_inserts=true
spring.jpa.properties.hibernate.order_updates=true
spring.jpa.show-sql=false
```

Efecto: cuando no hay tráfico, en ~30 s el pool queda vacío y a los 5 minutos Neon se suspende.
Coste: la primera visita tras un rato inactivo tarda ~1-2 s más (cold start de Neon).

### 6.2 Consultas eliminadas o acotadas

- `ProductoRepository.findCategoriasDistintas()` → sustituye al `findAll()` de la home
  (`ProductoService.listarCategorias`).
- `WebController.home()` → eliminadas la consulta del producto destacado y el `COUNT(*)`
  (no se usaban en `index.html`): **4 → 2 consultas por visita a la home**.
- `ResenaRepository.estadisticasAprobadas()` + `ResenaService.estadisticasAprobadas()` →
  promedio y total de estrellas en **una consulta agregada**, sin cargar la colección de reseñas.
- `PedidoRepository.findFirstByCodigoOrderByIdDesc` / `findFirstByTelefonoOrderByIdDesc` →
  `/api/pedido/*` deja de descargar la tabla completa de pedidos.
- `VarianteProductoRepository.findBySku` con `@EntityGraph(attributePaths = "producto")` →
  el carrito resuelve la variante y su producto en **1 consulta**.
- `MensajeRepository.findTop200ByOrderByFechaDesc()` → `/api/chat/todos` ya no devuelve la
  tabla entera.
- `DataMigrationRunner` → pregunta primero qué productos faltan por migrar
  (`findIdsSinImagenes` / `findIdsSinVariantes`) y solo procesa esos. En un arranque normal:
  **3 consultas en vez de 1 + 2N**, y sin reescribir nada.

### 6.3 Front-end: los sondeos dejan de gastar cómputo

- `index.html`: el chat consulta cada **15 s** (10 s mientras el cliente escribe), **se detiene
  al cerrar el chat** y **no consulta si la pestaña está en segundo plano** (se reanuda al volver).
  Antes eran 3 s fijos para siempre.
- `admin-chats.html`: refresco cada **30 s**, pausado si la pestaña está oculta o el admin está
  escribiendo. Antes: 10 s fijos.

### 6.4 Nuevo endpoint `/health` sin tocar la base

`HealthController` responde `{"status":"ok"}` **sin** abrir conexión. Úsalo como health check
del hosting o de cualquier monitor de disponibilidad. **Nunca** apuntes un monitor a `/`,
porque la home consulta la base y cada ping impide que Neon se duerma.

---

## 7. Lo que tienes que hacer en la consola de Neon (2 minutos)

1. **Fijar el tamaño de la compute en 0.25 CU.**
   Project → *Branches* → rama `main` → *Edit compute* → autoscaling máximo **0.25 CU**
   (o "Fixed size 0.25 CU"). Si autoscaling puede subir a 2 CU, un pico quema las CU-hours
   8 veces más rápido. Esta app no necesita más.
2. **Confirmar que solo existe una rama activa** (o que las ramas de preview están
   suspendidas). El consumo de CU-hours es **por proyecto**, así que varias ramas activas
   comparten tu único presupuesto de 100 CU-hours.
3. **Cerrar el SQL Editor / DBeaver / pgAdmin** cuando no los uses. Una conexión abierta con
   auto-refresh mantiene la compute despierta igual que la app.
4. **Revisar el consumo real**: Project → *Monitoring* / *Usage* → gráfico de compute.
   Deberías ver caídas a cero durante la noche. Si no las ves después de desplegar, algo sigue
   consultando la base.
5. Si usas la cadena **pooled** (`...-pooler...`) del dashboard de Neon, mejor: el pooler
   abarata el abrir/cerrar conexiones que ahora hacemos más a menudo. Si el driver JDBC diera
   problemas con *prepared statements*, añade `&prepareThreshold=0` a la URL.

---

## 8. Índices recomendados (opcional, pero ayuda)

Sin índices, varias consultas hacen *sequential scan* sobre tablas que irán creciendo. Menos
tiempo de consulta = menos segundos de compute facturados. Ejecútalos una vez en el SQL Editor:

```sql
CREATE INDEX IF NOT EXISTS idx_producto_categoria   ON producto (categoria);
CREATE INDEX IF NOT EXISTS idx_pedido_codigo        ON pedidos (codigo);
CREATE INDEX IF NOT EXISTS idx_pedido_telefono      ON pedidos (telefono);
CREATE INDEX IF NOT EXISTS idx_mensaje_codigo       ON mensajes (codigo_pedido);
CREATE INDEX IF NOT EXISTS idx_resena_producto      ON resenas (producto_id, aprobado);
CREATE INDEX IF NOT EXISTS idx_variante_producto    ON variante_producto (producto_id);
CREATE INDEX IF NOT EXISTS idx_imagen_producto      ON producto_imagen (producto_id);
```

---

## 9. Siguientes pasos (no aplicados, requieren probar en el entorno)

1. **`spring.jpa.open-in-view=false`.** Hoy está activado (valor por defecto) y el
   `EntityManager` queda abierto durante el renderizado de Thymeleaf: cada carga perezosa
   accidental es una consulta más, y la conexión se retiene mientras se pinta el HTML.
   *No lo activé porque rompería* `${producto.variantes}` en `admin-form.html`,
   `${producto.promedioCalificacion}` y el `getVariantes()` de `/admin/api/producto/{id}`;
   hay que hacer esos `fetch` explícitos primero.
2. **`spring.jpa.hibernate.ddl-auto=update` → `validate`.** `update` hace un montón de
   consultas de metadatos y posibles `ALTER` en **cada arranque** (y un `ALTER` mantiene la
   compute despierta). Cambia a `validate` cuando la migración a `producto_imagen` /
   `variante_producto` esté consolidada.
3. **`spring.thymeleaf.cache=true`** en producción: recompila menos plantillas (CPU del
   servidor, no de Neon, pero suma).
4. **Caché de catálogo** (Caffeine o `@Cacheable` en `listarCategorias` / `listarPorCategoria`,
   con invalidación al guardar un producto): una visita a la home podría no tocar la base.
5. **Hosting:** si el servidor está "siempre encendido", revisa que **no** haya un cron o un
   uptime monitor apuntando a `/`. Apúntalo a `/health`.

### Cómo verificar que funcionó

Después de desplegar, mira el gráfico de compute de Neon:

- **Antes:** línea continua a ~0.25 CU las 24 h.
- **Esperado:** actividad en bloques, con la compute en cero durante la noche.
- Consumo sano para una tienda de bajo tráfico: **≤ 1 CU-hour/día** (≈ 30 al mes, con el
  presupuesto de 100).

---

## 10. ⚠️ Seguridad: rota la contraseña de Neon

El archivo `application.properties` estuvo commiteado con la cadena de conexión y la
contraseña completas (commit `65d130b`, "Guardar configuracion de base de datos"), y **el
repositorio es público** (`github.com/vibecolombia26-oss/vibecolombia`). Esa credencial debe
considerarse comprometida:

1. Neon Console → *Roles* → restablecer la contraseña de `neondb_owner`.
2. Actualizar `DATABASE_URL` / `DATABASE_PASSWORD` en las variables de entorno del hosting.
3. Esta rama de trabajo ya elimina los valores por defecto del archivo (`${DATABASE_URL}`),
   pero **el historial de git sigue teniéndolos**. Al menos rota la contraseña; si además
   quieres limpiar el historial, usa `git filter-repo` o BFG y luego fuerza el push.
