package co.edu.univalle.palmira.config;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import jakarta.servlet.http.HttpServletResponse;
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
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private static final String[] ADMIN = {"ROLE_ADMIN", "ADMIN"};
    private static final String[] ADMIN_Y_COORDINADOR =
            {"ROLE_ADMIN", "ADMIN", "ROLE_COORDINADOR", "COORDINADOR"};
    private static final String[] ADMIN_Y_DOCENTE =
        {"ROLE_ADMIN", "ADMIN", "ROLE_DOCENTE", "DOCENTE"};

    @Value("${cors.allowed-origins:http://localhost:5173,http://localhost:3000}")
    private String allowedOrigins;

    @Value("${auth.jwt.secret:}")
    private String jwtSecret;

    private static void escribirError(HttpServletResponse res, int status, String message)
            throws IOException {
        res.setStatus(status);
        res.setContentType("application/json;charset=UTF-8");
        res.getWriter().write("{\"message\":\"" + message + "\"}");
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        JwtGrantedAuthoritiesConverter authoritiesConverter = new JwtGrantedAuthoritiesConverter();
        authoritiesConverter.setAuthoritiesClaimName("authorities");
        authoritiesConverter.setAuthorityPrefix("");

        JwtAuthenticationConverter authenticationConverter = new JwtAuthenticationConverter();
        authenticationConverter.setJwtGrantedAuthoritiesConverter(authoritiesConverter);

                        http.cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Rutas públicas: autenticación, Swagger y health
                        .requestMatchers("/api/auth/**", "/swagger-ui/**", "/swagger-ui.html",
                                "/v3/api-docs/**", "/actuator/health", "/error").permitAll()

<<<<<<< HEAD
                        // 0. Mi perfil (GET): cualquier usuario autenticado (Admin, Coordinador o Docente).
                        // DEBE ir antes de las reglas /** porque gana la primera que coincide.
                        .requestMatchers(HttpMethod.GET, "/api/docentes/me", "/api/coordinadores/me").authenticated()

                        // Otros métodos sobre el perfil propio (p. ej. PUT): según el rol
                        .requestMatchers("/api/coordinadores/me").hasAnyAuthority(ADMIN_Y_COORDINADOR)
                        .requestMatchers("/api/docentes/me").hasAnyAuthority(ADMIN_Y_DOCENTE)

                        // Períodos: exclusivo Administrador
                        .requestMatchers("/api/periodos/**").hasAnyAuthority(ADMIN)

                        // 1. Módulo Plantilla (Docentes y Coordinadores): exclusivo Administrador
                        .requestMatchers("/api/coordinadores/**").hasAnyAuthority(ADMIN)
                        .requestMatchers("/api/docentes/**").hasAnyAuthority(ADMIN)

                        // 2. Asignaturas, mallas, programas, facultades y catálogos:
                        // Coordinadores solo pueden consultar (GET)
                        .requestMatchers(HttpMethod.GET, "/api/asignaturas/**", "/api/pensum/**",
                                "/api/programas/**", "/api/facultades/**", "/api/catalogos/**")
                                .hasAnyAuthority(ADMIN_Y_COORDINADOR)

                        // 3. Creaciones y modificaciones: exclusivo Administrador
                        .requestMatchers(HttpMethod.POST, "/api/**").hasAnyAuthority(ADMIN)
                        .requestMatchers(HttpMethod.PUT, "/api/**").hasAnyAuthority(ADMIN)
                        .requestMatchers(HttpMethod.PATCH, "/api/**").hasAnyAuthority(ADMIN)
                        .requestMatchers(HttpMethod.DELETE, "/api/**").hasAnyAuthority(ADMIN)

                        // 4. Cualquier otra petición a la API: exclusivo Administrador
                        .requestMatchers("/api/**").hasAnyAuthority(ADMIN)
=======
                        // 1. Módulo Plantilla: solo el administrador gestiona usuarios.
                        .requestMatchers("/api/coordinadores/**").hasAnyAuthority("ROLE_ADMIN", "ADMIN")
                        .requestMatchers("/api/docentes/**").hasAnyAuthority("ROLE_ADMIN", "ADMIN")

                        // 2. Períodos: los tres roles consultan; solo el administrador modifica.
                        .requestMatchers(HttpMethod.GET, "/api/periodos/**")
                                .hasAnyAuthority("ROLE_ADMIN", "ADMIN", "ROLE_COORDINADOR", "COORDINADOR", "ROLE_DOCENTE", "DOCENTE")
                        .requestMatchers("/api/periodos/**").hasAnyAuthority("ROLE_ADMIN", "ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/disponibilidades/**")
                                .hasAnyAuthority("ROLE_DOCENTE", "DOCENTE")
                        .requestMatchers(HttpMethod.POST, "/api/disponibilidades/**")
                                .hasAnyAuthority("ROLE_DOCENTE", "DOCENTE")

                        // 3. Asignaturas y Mallas: el coordinador puede consultar.
                        .requestMatchers(HttpMethod.GET, "/api/asignaturas/**", "/api/pensum/**", "/api/programas/**", "/api/facultades/**", "/api/catalogos/**")
                                .hasAnyAuthority("ROLE_ADMIN", "ADMIN", "ROLE_COORDINADOR", "COORDINADOR")

                        // 4. Creaciones y modificaciones sobre cualquier otro recurso: administrador.
                        .requestMatchers(HttpMethod.POST, "/api/**").hasAnyAuthority("ROLE_ADMIN", "ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/**").hasAnyAuthority("ROLE_ADMIN", "ADMIN")
                        .requestMatchers(HttpMethod.PATCH, "/api/**").hasAnyAuthority("ROLE_ADMIN", "ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/**").hasAnyAuthority("ROLE_ADMIN", "ADMIN")

                        // 5. Cualquier otra petición a la API: administrador.
                        .requestMatchers("/api/**").hasAnyAuthority("ROLE_ADMIN", "ADMIN")
>>>>>>> acad0f207f1fcd5f2a84230b3809d1317a59c054
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth -> oauth
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(authenticationConverter))
                        .authenticationEntryPoint((req, res, ex) ->
                                escribirError(res, 401, "Sesión no válida o expirada"))
                        .accessDeniedHandler((req, res, ex) ->
                                escribirError(res, 403, "No tiene permisos para esta acción")));

        return http.build();
    }

    @Bean
    public SecretKey jwtSecretKey() {
        byte[] keyBytes = jwtSecret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            throw new IllegalStateException("JWT_SECRET debe tener al menos 32 bytes (256 bits)");
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
        List<String> origins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim).toList();
        configuration.setAllowedOrigins(origins);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}