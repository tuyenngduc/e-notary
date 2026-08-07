package com.actvn.enotary.config;

import com.actvn.enotary.exception.ErrorCode;
import com.actvn.enotary.exception.ErrorResponse;
import com.actvn.enotary.security.JwtFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Set;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;

import static org.springframework.security.config.Customizer.withDefaults;

@Configuration
@EnableWebSecurity
public class SecurityConfig {
    private final JwtFilter jwtFilter;
    private final ObjectMapper objectMapper;

    @Value("${app.cors.allowed-origins}")
    private String allowedOriginsProperty;

    public SecurityConfig(JwtFilter jwtFilter, ObjectMapper objectMapper) {
        this.jwtFilter = jwtFilter;
        this.objectMapper = objectMapper;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http)
            throws Exception {

        http.cors(withDefaults())
                .csrf(AbstractHttpConfigurer::disable)
                .headers(headers -> headers.frameOptions(frameOptions -> frameOptions.sameOrigin()))
                .sessionManagement(sess ->
                        sess.sessionCreationPolicy(
                                SessionCreationPolicy.STATELESS))
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint((request, response, authException) ->
                                writeSecurityError(
                                        response,
                                        request,
                                        HttpStatus.UNAUTHORIZED,
                                        ErrorCode.INVALID_AUTHENTICATION.getMessage()
                                ))
                        .accessDeniedHandler((request, response, accessDeniedException) ->
                                writeSecurityError(
                                        response,
                                        request,
                                        HttpStatus.FORBIDDEN,
                                        ErrorCode.UNAUTHORIZED_ACCESS.getMessage()
                                )))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/login", "/api/auth/refresh").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/templates/*/view", "/api/templates/*/download").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/documents/*/view").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/documents/*/pages/*/image").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/public/documents/verify").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/video/verify-token").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/video/room/**").permitAll()
                        .requestMatchers("/ws/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/users").permitAll()
                        .requestMatchers("/error").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/requests").hasRole("CLIENT")
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/profile/**").authenticated()
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtFilter,
                        UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        List<String> allowedOrigins = Arrays.stream(allowedOriginsProperty.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isBlank())
                .toList();

        if (allowedOrigins.isEmpty()) {
            allowedOrigins = List.of(
                    "http://localhost:5173",
                    "http://127.0.0.1:5173",
                    "http://192.168.*:5173",
                    "http://192.168.*:5173",
                    "http://192.168.*.*:5173",
                    "http://10.*:5173",
                    "http://10.*.*.*:5173",
                    "http://172.16.*:5173",
                    "http://172.17.*:5173",
                    "http://172.18.*:5173",
                    "http://172.19.*:5173",
                    "http://172.2*.*:5173",
                    "http://172.3*.*:5173"
            );
        }

        // If config looks like local development (has localhost), also allow LAN IPv4 addresses of this machine.
        // This fixes phone testing where the origin is http://192.168.x.x:5173.
        if (allowedOrigins.stream().anyMatch(origin -> origin.contains("localhost") || origin.contains("127.0.0.1"))) {
            allowedOrigins = withLocalLanOrigins(allowedOrigins, 5173);
        }

        boolean hasWildcard = allowedOrigins.stream().anyMatch(origin -> origin.contains("*"));
        if (hasWildcard) {
            configuration.setAllowedOriginPatterns(allowedOrigins);
        } else {
            configuration.setAllowedOrigins(allowedOrigins);
        }
        configuration.setAllowedMethods(List.of(
                "GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"
        ));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setExposedHeaders(Arrays.asList(
                "Authorization",
                "Content-Type",
                "X-Requested-With",
                "Content-Disposition"
        ));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    private List<String> withLocalLanOrigins(List<String> base, int port) {
        Set<String> merged = new LinkedHashSet<>(base);
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces != null && interfaces.hasMoreElements()) {
                NetworkInterface networkInterface = interfaces.nextElement();
                if (!networkInterface.isUp() || networkInterface.isLoopback() || networkInterface.isVirtual()) {
                    continue;
                }

                Enumeration<InetAddress> addresses = networkInterface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress address = addresses.nextElement();
                    if (address instanceof Inet4Address inet4) {
                        String ip = inet4.getHostAddress();
                        merged.add("http://" + ip + ":" + port);
                    }
                }
            }
        } catch (Exception ignored) {
            // Best-effort only.
        }
        return new ArrayList<>(merged);
    }

    private void writeSecurityError(
            jakarta.servlet.http.HttpServletResponse response,
            jakarta.servlet.http.HttpServletRequest request,
            HttpStatus status,
            String message
    ) throws java.io.IOException {
        response.setStatus(status.value());
        response.setCharacterEncoding(java.nio.charset.StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        ErrorResponse body = ErrorResponse.of(
                status.value(),
                status.getReasonPhrase(),
                message,
                request.getRequestURI()
        );

        response.getWriter().write(objectMapper.writeValueAsString(body));
    }

    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}
