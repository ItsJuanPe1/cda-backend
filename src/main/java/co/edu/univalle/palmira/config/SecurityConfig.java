package co.edu.univalle.palmira.config;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {
    @Value("${cors.allowed-origins:http://localhost:5173,http://localhost:3000}")
    private String allowedOrigins;

    @Value("${auth.jwt.secret:cda-backend-secret-key-univalle-palmira-2026-secure-token-32b}")
    private String jwtSecret;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        JwtGrantedAuthoritiesConverter authoritiesConverter = new JwtGrantedAuthoritiesConverter();
        authoritiesConverter.setAuthoritiesClaimName("authorities");
        authoritiesConverter.setAuthorityPrefix("");
        JwtAuthenticationConverter authenticationConverter = new JwtAuthenticationConverter();
        authenticationConverter.setJwtGrantedAuthoritiesConverter(authoritiesConverter);

        http.cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Rutas públicas de autenticación y documentación Swagger
                        .requestMatchers("/api/auth/**", "/swagger-ui/**", "/swagger-ui.html",
                                "/v3/api-docs/**", "/actuator/health", "/error")
                        .permitAll()

                        // 0. Mi perfil: cualquier usuario autenticado (Admin, Coordinador o Docente)
                        // puede consultar SU perfil de docente. Debe ir ANTES de la regla general
                        // de /api/docentes/** porque se evalúa en orden y gana la primera que coincide.
                        .requestMatchers(HttpMethod.GET, "/api/docentes/me", "/api/coordinadores/me").authenticated()

                        // 1. Módulo Plantilla (Docentes y Coordinadores):
                        // Exclusivo para Administrador (Rol 1). Coordinadores y Docentes NO pueden ver
                        // ni modificar la plantilla.
                        .requestMatchers("/api/coordinadores/**").hasAnyAuthority("ROLE_ADMIN", "ADMIN")
                        .requestMatchers("/api/docentes/**").hasAnyAuthority("ROLE_ADMIN", "ADMIN")

                        // 2. Asignaturas y Mallas Curriculares (Pensum / Programas / Facultades /
                        // Catálogos):
                        // Coordinadores (Rol 2) SOLO pueden VER (GET) asignaturas y mallas
                        // curriculares.
                        // Docentes (Rol 3) NO tienen permiso de lectura ni escritura sobre las
                        // funciones actuales.
                        .requestMatchers(HttpMethod.GET, "/api/asignaturas/**", "/api/pensum/**", "/api/programas/**",
                                "/api/facultades/**", "/api/catalogos/**")
                        .hasAnyAuthority("ROLE_ADMIN", "ADMIN", "ROLE_COORDINADOR", "COORDINADOR")

                        // 3. Creaciones y Modificaciones (POST, PUT, PATCH, DELETE) sobre cualquier
                        // recurso:
                        // Exclusivo para Administrador (Rol 1).
                        .requestMatchers(HttpMethod.POST, "/api/**").hasAnyAuthority("ROLE_ADMIN", "ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/**").hasAnyAuthority("ROLE_ADMIN", "ADMIN")
                        .requestMatchers(HttpMethod.PATCH, "/api/**").hasAnyAuthority("ROLE_ADMIN", "ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/**").hasAnyAuthority("ROLE_ADMIN", "ADMIN")

                        // 4. Cualquier otra petición a la API: Exclusivo para Administrador (Rol 1)
                        .requestMatchers("/api/**").hasAnyAuthority("ROLE_ADMIN", "ADMIN")
                        .anyRequest().authenticated())
                .oauth2ResourceServer(
                        oauth -> oauth.jwt(jwt -> jwt.jwtAuthenticationConverter(authenticationConverter)));
        return http.build();
    }

    @Bean
    public SecretKey jwtSecretKey() {
        String secret = (jwtSecret != null && !jwtSecret.isBlank())
                ? jwtSecret
                : "cda-backend-secret-key-univalle-palmira-2026-secure-token-32b";
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            secret = "cda-backend-secret-key-univalle-palmira-2026-secure-token-32b";
            keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        }
        return new SecretKeySpec(keyBytes, "HmacSHA256");
    }

    @Bean
    public JwtEncoder jwtEncoder(SecretKey jwtSecretKey) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(jwtSecretKey));
    }

    @Bean
    public JwtDecoder jwtDecoder(SecretKey jwtSecretKey) {
        return NimbusJwtDecoder.withSecretKey(jwtSecretKey).macAlgorithm(MacAlgorithm.HS256).build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        List<String> origins = Arrays.stream(allowedOrigins.split(",")).map(String::trim).toList();
        configuration.setAllowedOrigins(origins);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        configuration.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
