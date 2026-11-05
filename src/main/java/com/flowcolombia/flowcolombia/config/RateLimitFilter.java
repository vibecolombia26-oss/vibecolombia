package com.flowcolombia.flowcolombia.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Límite de peticiones por IP (ventana deslizante de un minuto).
 *
 * <p>Protege tres cosas concretas:</p>
 * <ol>
 *   <li><strong>Fuerza bruta en el login</strong> del panel.</li>
 *   <li><strong>Enumeración de pedidos</strong> en {@code /api/pedido/**}
 *       (antes los códigos eran predecibles).</li>
 *   <li><strong>Spam</strong> en el chat de clientes.</li>
 * </ol>
 *
 * <p>Todo se guarda en memoria del proceso: <strong>cero consultas a Neon</strong>
 * y cero dependencias nuevas. Es suficiente para una tienda de bajo tráfico;
 * si algún día hay varias instancias, cada una llevaría su propia cuenta.</p>
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter implements Ordered {

    /** Ventana de conteo: un minuto. */
    private static final long VENTANA_MS = 60_000L;

    /** Tope de IPs vigiladas a la vez, para que el mapa no crezca sin control. */
    private static final int MAX_IPS = 10_000;

    /** Forma de una regla: método, ruta, máximo por minuto y tipo de respuesta. */
    private record Regla(String metodo, String ruta, boolean exacta, int maximo, boolean api) {
    }

    private static final Regla[] REGLAS = {
            // Fuerza bruta sobre el login: 8 intentos por minuto y por IP.
            new Regla(HttpMethod.POST.name(), "/admin/login", true, 8, false),
            // Alta de pedidos: 10 por minuto (evita spam de pedidos falsos).
            new Regla(HttpMethod.POST.name(), "/api/pedido", true, 10, true),
            // Consulta de seguimiento / chat: 30 por minuto (enumeración).
            new Regla(HttpMethod.GET.name(), "/api/pedido/", false, 30, true),
            new Regla(HttpMethod.GET.name(), "/api/chat/", false, 30, true),
            new Regla(HttpMethod.POST.name(), "/api/chat/", false, 30, true)
    };

    private final Map<String, Deque<Long>> ventanas = new ConcurrentHashMap<>();

    /**
     * Por delante de la cadena de Spring Security (orden -100) y por detrás de
     * los filtros de infraestructura, para que el trabajo de autenticación no
     * llegue a ejecutarse cuando la petición ya va a ser rechazada.
     */
    @Override
    public int getOrder() {
        return -101;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        Regla regla = reglaPara(request.getMethod(), request.getRequestURI());
        if (regla == null) {
            filterChain.doFilter(request, response);
            return;
        }

        String clave = regla.ruta() + '|' + ipDe(request);
        if (!permitir(clave, regla.maximo())) {
            rechazar(request, response, regla);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private Regla reglaPara(String metodo, String uri) {
        if (metodo == null || uri == null) {
            return null;
        }
        for (Regla regla : REGLAS) {
            if (!metodo.equals(regla.metodo())) {
                continue;
            }
            boolean coincide = regla.exacta()
                    ? uri.equals(regla.ruta())
                    : uri.startsWith(regla.ruta());
            if (coincide) {
                return regla;
            }
        }
        return null;
    }

    /**
     * Ventana deslizante: descarta las marcas de más de un minuto y decide.
     */
    private boolean permitir(String clave, int maximo) {
        long ahora = System.currentTimeMillis();
        if (ventanas.size() > MAX_IPS) {
            ventanas.clear();
        }

        Deque<Long> marcas = ventanas.computeIfAbsent(clave, ignorada -> new ArrayDeque<>());
        synchronized (marcas) {
            while (!marcas.isEmpty() && ahora - marcas.peekFirst() > VENTANA_MS) {
                marcas.pollFirst();
            }
            if (marcas.size() >= maximo) {
                return false;
            }
            marcas.addLast(ahora);
            return true;
        }
    }

    private void rechazar(HttpServletRequest request,
                          HttpServletResponse response,
                          Regla regla) throws IOException {

        response.setHeader("Retry-After", "60");

        if (regla.api()) {
            response.setStatus(429);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write("{\"error\":\"Demasiadas solicitudes. Espera un minuto.\"}");
            return;
        }

        // Login: vuelve al formulario con un aviso, sin filtrar si el usuario
        // existía o no.
        response.setStatus(HttpServletResponse.SC_FOUND);
        response.sendRedirect(request.getContextPath() + "/admin-login?bloqueado=true");
    }

    /**
     * IP del cliente.
     *
     * <p>Con {@code server.forward-headers-strategy=framework} el filtro de
     * cabeceras reenviadas ya reescribe {@code getRemoteAddr()} con la IP real
     * del cliente, así que no confiamos en {@code X-Forwarded-For} a mano
     * (sería falsificable por el cliente).</p>
     */
    private String ipDe(HttpServletRequest request) {
        String ip = request.getRemoteAddr();
        return (ip == null || ip.isBlank()) ? "anon" : ip;
    }
}
