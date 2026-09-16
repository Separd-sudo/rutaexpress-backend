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

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .cors(Customizer.withDefaults())
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));

        if (azureAuthEnabled) {
            http
                .authorizeHttpRequests(auth -> auth
                    // Endpoints públicos
                    .requestMatchers("/api/bff/health", "/actuator/**", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/bff/catalog/**").permitAll()

                    // Roles definidos en la pauta
                    // Admin: Administra catálogo y tarifas
                    .requestMatchers(HttpMethod.POST, "/api/bff/catalog/**").hasRole("Admin")
                    .requestMatchers(HttpMethod.PUT, "/api/bff/catalog/**").hasRole("Admin")

                    // Despachador y Admin: Cambian estado de envíos
                    .requestMatchers(HttpMethod.PUT, "/api/bff/shipments/*/status").hasAnyRole("Despachador", "Admin")

                    // Cliente, Despachador y Admin: Crean y consultan envíos
                    .requestMatchers(HttpMethod.POST, "/api/bff/shipments/**").hasAnyRole("Cliente", "Despachador", "Admin")
                    .requestMatchers(HttpMethod.GET, "/api/bff/shipments/**").hasAnyRole("Cliente", "Despachador", "Admin", "Auditor")

                    // Auditor y Admin: Consulta timeline y auditoría
                    .requestMatchers("/api/bff/audit/**").hasAnyRole("Auditor", "Admin")

                    .anyRequest().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                    .jwt(jwt -> jwt.jwtAuthenticationConverter(new JwtRoleConverter()))
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
        // Soporte para Frontend React (Vite 5173, CRA 3000, Next.js 3000)
        configuration.setAllowedOriginPatterns(List.of("http://localhost:*", "https://*.amazonaws.com", "*"));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(Arrays.asList("Authorization", "Content-Type", "X-Requested-With", "Accept", "Origin"));
        configuration.setExposedHeaders(List.of("Authorization"));
        configuration.setAllowCredentials(false);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}