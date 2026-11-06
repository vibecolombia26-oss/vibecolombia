package com.flowcolombia.flowcolombia.config;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Configuración de seguridad del escaparate y del panel.
 *
 * <p>Principios aplicados:</p>
 * <ul>
 *   <li><strong>Lista blanca explícita</strong>: solo lo declarado aquí es
 *       público. Cualquier ruta nueva (endpoint, controlador, estático) nace
 *       protegida por {@code anyRequest().authenticated()}.</li>
 *   <li><strong>CSRF activo</strong> en todo lo que use sesión.</li>
 *   <li><strong>Secretos fuera del código</strong>: la contraseña del admin
 *       viene de la variable de entorno {@code ADMIN_PASSWORD}, sin valor por
 *       defecto (si falta, la aplicación no arranca).</li>
 *   <li><strong>Sin coste extra en Neon</strong>: nada de esta configuración
 *       consulta la base de datos; la autenticación es en memoria.</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * Contraseña del administrador.
     *
     * <p>Sin valor por defecto a propósito: un {@code @Value} sin fallback
     * hace que Spring falle al arrancar si {@code ADMIN_PASSWORD} no está
     * definida. Es preferible un despliegue fallido a un panel abierto con
     * una contraseña que está publicada en el repositorio.</p>
     */
    @Value("${admin.password}")
    private String adminPassword;

    /** Rutas públicas del escaparate que deben poder verse sin sesión. */
    private static final String[] TIENDA_PUBLICA = {
            "/", "/producto/**", "/carrito", "/envios",
            "/seguimiento", "/privacidad", "/terminos", "/contacto"
    };

    /**
     * Endpoints públicos de escritura del escaparate.
     *
     * <p>Se excluyen de CSRF porque son una API sin estado (sin sesión, sin
     * privilegios) que consume el propio frontend: exigirles token rompería
     * el checkout, el chat del cliente y el asistente. No existe riesgo de
     * que se ejecuten "en nombre" de un administrador autenticado.</p>
     */
    private static final String[] API_PUBLICA_POST = {
            "/api/pedido",
            "/api/chat/enviar",
            "/api/variante/detalle",
            "/api/asistente"
    };

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {

        http
                // ------------------------------------------------------------
                // CSRF: activo. Antes estaba desactivado por completo, así que
                // un enlace malicioso podía borrar productos o cambiar estados
                // con la sesión abierta del administrador.
                // ------------------------------------------------------------
                .csrf(csrf -> csrf.ignoringRequestMatchers(API_PUBLICA_POST))

                // ------------------------------------------------------------
                // AUTORIZACIÓN: lista blanca explícita.
                // ------------------------------------------------------------
                .authorizeHttpRequests(auth -> auth

                        // Recursos estáticos y salud (no tocan base de datos)
                        .requestMatchers("/css/**", "/js/**", "/images/**", "/favicon.ico").permitAll()
                        .requestMatchers("/health", "/error").permitAll()

                        // SEO: los buscadores tienen que poder leerlos sin sesión
                        .requestMatchers("/sitemap.xml", "/robots.txt").permitAll()

                        // Verificación de dominio de Google Search Console.
                        // Sin esta línea, /googleXXXX.html cae en
                        // anyRequest().authenticated() → 302 al login y Google
                        // no puede verificar la propiedad del sitio.
                        .requestMatchers("/google*.html").permitAll()

                        // Login del administrador
                        .requestMatchers("/admin-login", "/admin/login").permitAll()

                        // Escaparate público (solo lectura)
                        .requestMatchers(HttpMethod.GET, TIENDA_PUBLICA).permitAll()

                        // API pública del escaparate: lista blanca endpoint a
                        // endpoint (antes todo "/api/**" era público, incluida
                        // la bandeja de consultas con los teléfonos de clientes)
                        .requestMatchers(HttpMethod.POST, API_PUBLICA_POST).permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/pedido/**", "/api/chat/mensajes").permitAll()

                        // Bandeja de consultas completa: SOLO administrador
                        .requestMatchers("/api/chat/todos").hasRole("ADMIN")

                        // Panel de administración completo
                        .requestMatchers("/admin/**").hasRole("ADMIN")

                        // Todo lo no declarado exige sesión.
                        // (Antes era permitAll() y cualquier ruta nueva nacía pública.)
                        .anyRequest().authenticated()
                )

                // ------------------------------------------------------------
                // FORMULARIO DE LOGIN
                // ------------------------------------------------------------
                .formLogin(form -> form
                        .loginPage("/admin-login")
                        .loginProcessingUrl("/admin/login")
                        .defaultSuccessUrl("/admin/panel", true)
                        .failureUrl("/admin-login?error=true")
                        .permitAll()
                )

                // ------------------------------------------------------------
                // LOGOUT (POST + CSRF; el botón vive en la topbar del panel)
                // ------------------------------------------------------------
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/admin-login?logout=true")
                        .invalidateHttpSession(true)
                        .clearAuthentication(true)
                        .deleteCookies("JSESSIONID")
                        .permitAll()
                )

                // ------------------------------------------------------------
                // SESIÓN: expira sola, cambia de ID al autenticar y permite
                // una única sesión de administrador (la nueva desplaza a la
                // anterior). Todo en memoria: cero consultas a Neon.
                // ------------------------------------------------------------
                .sessionManagement(session -> {
                    session.sessionFixation(fixation -> fixation.migrateSession());
                    session.maximumSessions(1).maxSessionsPreventsLogin(false);
                })

                // ------------------------------------------------------------
                // CABECERAS DE SEGURIDAD
                // ------------------------------------------------------------
                .headers(headers -> headers
                        .frameOptions(frame -> frame.deny())
                        .referrerPolicy(referrer -> referrer.policy(
                                ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                        .httpStrictTransportSecurity(hsts -> hsts
                                .includeSubDomains(true)
                                .maxAgeInSeconds(31_536_000))
                        // CSP: las plantillas usan estilos y manejadores inline,
                        // y el Píxel de Meta necesita cargar su script desde
                        // connect.facebook.net y enviar los eventos a
                        // facebook.com. Sin estas dos excepciones el píxel
                        // queda bloqueado en silencio (solo se ve en consola).
                        .contentSecurityPolicy(csp -> csp.policyDirectives(
                                "default-src 'self'; "
                                        + "img-src 'self' data: https:; "
                                        + "style-src 'self' 'unsafe-inline'; "
                                        + "script-src 'self' 'unsafe-inline' https://connect.facebook.net; "
                                        + "connect-src 'self' https://www.facebook.com https://connect.facebook.net; "
                                        + "font-src 'self' data:; "
                                        + "object-src 'none'; "
                                        + "base-uri 'self'; "
                                        + "form-action 'self'; "
                                        + "frame-ancestors 'none'"))
                )

                // ------------------------------------------------------------
                // API: si no hay sesión, responde 401 JSON en lugar de
                // redirigir a la página de login (el frontend lo entiende y
                // muestra "sesión expirada" en vez de romperse).
                // ------------------------------------------------------------
                .exceptionHandling(handling -> handling.authenticationEntryPoint(apiEntryPoint()));

        return http.build();
    }

    /**
     * Punto de entrada para peticiones no autenticadas: JSON en la API,
     * redirección al login en el resto del sitio.
     */
    private AuthenticationEntryPoint apiEntryPoint() {
        return (request, response, authException) -> {
            String uri = request.getRequestURI();
            if (uri != null && uri.startsWith("/api/")) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                response.setCharacterEncoding("UTF-8");
                response.getWriter().write("{\"error\":\"No autorizado\"}");
            } else {
                response.sendRedirect(request.getContextPath() + "/admin-login");
            }
        };
    }

    /**
     * Codificador utilizado para proteger la contraseña
     * del administrador.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Usuario administrador en memoria.
     *
     * <p>Usuario: <strong>admin</strong><br>
     * Contraseña: la definida en la variable de entorno
     * {@code ADMIN_PASSWORD}.</p>
     */
    @Bean
    public UserDetailsService userDetailsService() {

        UserDetails admin = User.builder()
                .username("admin")
                .password(passwordEncoder().encode(adminPassword))
                .roles("ADMIN")
                .build();

        return new InMemoryUserDetailsManager(admin);
    }

    /**
     * Permite que /admin-login cargue directamente
     * la plantilla admin-login.html.
     */
    @Bean
    public WebMvcConfigurer adminLoginViewController() {

        return new WebMvcConfigurer() {

            @Override
            public void addViewControllers(ViewControllerRegistry registry) {
                registry.addViewController("/admin-login")
                        .setViewName("admin-login");
            }
        };
    }
}
