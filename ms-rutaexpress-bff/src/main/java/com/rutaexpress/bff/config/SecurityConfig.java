package com.rutaexpress.bff.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

    @Value("${security.azure.enabled:false}")
    private boolean azureAuthEnabled;

    @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri:https://login.microsoftonline.com/b7bd70fc-34e6-4f0c-80cc-e0909f96d096/discovery/v2.0/keys}")
    private String jwkSetUri;

    /**
     * Decodificador explicito para tokens JWT (Microsoft Entra ID / Azure AD).
     * Valida la firma criptografica asimetrica (RSA256) contra el endpoint publico JWKS.
     */
    @Bean
    public JwtDecoder jwtDecoder() {
        return NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .cors(Customizer.withDefaults())
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));

        if (azureAuthEnabled) {
            http
                .authorizeHttpRequests(auth -> auth
                    // Endpoints públicos y de diagnóstico
                    .requestMatchers("/api/bff/health", "/api/health", "/health", "/actuator/**", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/bff/catalog/**", "/api/catalog/**").permitAll()
                    .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                    // Roles definidos para RutaExpress:
                    // 1. Admin: Administra catálogo y tarifas (Crear, Modificar, Eliminar)
                    .requestMatchers(HttpMethod.POST, "/api/bff/catalog/**", "/api/catalog/**").hasRole("Admin")
                    .requestMatchers(HttpMethod.PUT, "/api/bff/catalog/**", "/api/catalog/**").hasRole("Admin")
                    .requestMatchers(HttpMethod.DELETE, "/api/bff/catalog/**", "/api/catalog/**").hasRole("Admin")

                    // 2. Despachador y Admin: Cambian estado de envíos (Despachador SOLO puede cambiar estado)
                    .requestMatchers(HttpMethod.PUT, "/api/bff/shipments/*/status", "/api/shipments/*/status").hasAnyRole("Despachador", "Admin")

                    // 3. Cliente y Admin: Crean nuevos envíos
                    .requestMatchers(HttpMethod.POST, "/api/bff/shipments/**", "/api/shipments/**").hasAnyRole("Cliente", "Admin")

                    // 4. Cliente, Despachador, Auditor y Admin: Consultan envíos (lectura)
                    .requestMatchers(HttpMethod.GET, "/api/bff/shipments/**", "/api/shipments/**").hasAnyRole("Cliente", "Despachador", "Auditor", "Admin")

                    // 5. Admin: Eliminación / Cancelación de envíos
                    .requestMatchers(HttpMethod.DELETE, "/api/bff/shipments/**", "/api/shipments/**").hasRole("Admin")

                    // 6. Auditor y Admin: Consulta timeline y auditoría
                    .requestMatchers("/api/bff/audit/**", "/api/audit/**").hasAnyRole("Auditor", "Admin")

                    // 7. Admin: Métricas operacionales y gestión de colas RabbitMQ
                    .requestMatchers("/api/bff/rabbitmq/**", "/api/rabbitmq/**").hasRole("Admin")

                    .anyRequest().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                    .jwt(jwt -> jwt
                        .decoder(jwtDecoder())
                        .jwtAuthenticationConverter(new JwtRoleConverter())
                    )
                );
        } else {
            // Modo desarrollo local: permite interactuar con los microservicios sin requerir token real de Azure Entra ID
            http.authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        }

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        // Soporte completo para Frontend React (Vite 5173, CRA 3000, producción y AWS)
        configuration.setAllowedOriginPatterns(List.of("http://localhost:*", "http://127.0.0.1:*", "https://*.amazonaws.com", "*"));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS", "HEAD"));
        configuration.setAllowedHeaders(Arrays.asList(
                "Authorization", "Content-Type", "X-Requested-With", "Accept", "Origin",
                "Access-Control-Request-Method", "Access-Control-Request-Headers"
        ));
        configuration.setExposedHeaders(Arrays.asList("Authorization", "Content-Disposition"));
        configuration.setAllowCredentials(false);
        configuration.setMaxAge(3600L); // 1 hora de cache para pre-flight OPTIONS

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}