package com.flowcolombia.flowcolombia.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Value("${admin.password:FlowColombia2026*}")
    private String adminPassword;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {

        http
                .csrf(csrf -> csrf.disable())

                .authorizeHttpRequests(auth -> auth
                        // Páginas públicas
                        .requestMatchers(
                                "/",
                                "/producto/**",
                                "/carrito/**",
                                "/envios",
                                "/seguimiento",
                                "/privacidad",
                                "/terminos",
                                "/contacto",
                                "/api/**"
                        ).permitAll()

                        // Login del administrador
                        .requestMatchers(
                                "/admin-login",
                                "/admin/login"
                        ).permitAll()

                        // Recursos estáticos
                        .requestMatchers(
                                "/css/**",
                                "/js/**",
                                "/images/**",
                                "/favicon.ico"
                        ).permitAll()

                        // Todo el panel administrativo requiere autenticación
                        .requestMatchers("/admin/**").authenticated()

                        // Resto de rutas públicas
                        .anyRequest().permitAll()
                )

                .formLogin(form -> form
                        // Página personalizada de login
                        .loginPage("/admin-login")

                        // URL que procesa Spring Security
                        .loginProcessingUrl("/admin/login")

                        // Después de login correcto
                        .defaultSuccessUrl("/admin/panel", true)

                        // Si la contraseña es incorrecta
                        .failureUrl("/admin-login?error=true")

                        .permitAll()
                )

                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/admin-login?logout=true")
                        .permitAll()
                );

        return http.build();
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
     * Usuario:
     * admin
     *
     * Contraseña:
     * La definida en admin.password
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