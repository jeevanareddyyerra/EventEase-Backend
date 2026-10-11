package com.eventease.security;

import com.eventease.service.CustomUserDetailsService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.core.Ordered;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import org.springframework.beans.factory.annotation.Value;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final String DEFAULT_PRODUCTION_FRONTEND = "https://event-ease-frontend-chi.vercel.app";
    private static final String DEFAULT_LOCAL_FRONTEND = "http://localhost:5173";

    @Value("${cors.allowed-origins:http://localhost:5173,https://event-ease-frontend-chi.vercel.app}")
    private String allowedOrigins;

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final CustomUserDetailsService userDetailsService;
    private final JwtAuthenticationEntryPoint authenticationEntryPoint;
    private final CustomAccessDeniedHandler accessDeniedHandler;

    public SecurityConfig(
            JwtAuthenticationFilter jwtAuthenticationFilter,
            CustomUserDetailsService userDetailsService,
            JwtAuthenticationEntryPoint authenticationEntryPoint,
            CustomAccessDeniedHandler accessDeniedHandler
    ) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.userDetailsService = userDetailsService;
        this.authenticationEntryPoint = authenticationEntryPoint;
        this.accessDeniedHandler = accessDeniedHandler;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(AbstractHttpConfigurer::disable)
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler)
                )
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Preflight OPTIONS requests across all endpoints
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                        // Public authentication endpoints
                        .requestMatchers("/api/auth/**").permitAll()

                        // OpenAPI / Swagger UI documentation endpoints
                        .requestMatchers(
                                "/v3/api-docs/**",
                                "/v3/api-docs",
                                "/swagger-ui/**",
                                "/swagger-ui.html"
                        ).permitAll()

                        // Actuator health & info endpoints (public monitoring)
                        .requestMatchers(
                                "/actuator/health",
                                "/actuator/health/**",
                                "/actuator/info"
                        ).permitAll()

                        // 1. Admin & User management - Platform administration (ADMIN only)
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/users/**").hasRole("ADMIN")

                        // 2. Events
                        .requestMatchers(HttpMethod.GET, "/api/events/**").hasAnyRole("USER", "ORGANIZER", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/events/**").hasAnyRole("ORGANIZER", "ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/events/**").hasAnyRole("ORGANIZER", "ADMIN")
                        .requestMatchers(HttpMethod.PATCH, "/api/events/**").hasAnyRole("ORGANIZER", "ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/events/**").hasAnyRole("ORGANIZER", "ADMIN")

                        // 3. Volunteer Requirements
                        .requestMatchers(HttpMethod.GET, "/api/volunteer-requirements/**").hasAnyRole("USER", "ORGANIZER", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/volunteer-requirements/**").hasAnyRole("ORGANIZER", "ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/volunteer-requirements/**").hasAnyRole("ORGANIZER", "ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/volunteer-requirements/**").hasAnyRole("ORGANIZER", "ADMIN")

                        // 4. Event Documents
                        .requestMatchers(HttpMethod.GET, "/api/event-documents/**").hasAnyRole("USER", "ORGANIZER", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/event-documents/**").hasAnyRole("USER", "ORGANIZER", "ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/event-documents/**").hasAnyRole("USER", "ORGANIZER", "ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/event-documents/**").hasAnyRole("USER", "ORGANIZER", "ADMIN")

                        // 5. Registrations
                        .requestMatchers(HttpMethod.GET, "/api/registrations", "/api/registrations/").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/registrations/**").hasAnyRole("USER", "ORGANIZER", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/registrations/**").hasAnyRole("USER", "ORGANIZER", "ADMIN")
                        .requestMatchers(HttpMethod.PATCH, "/api/registrations/**").hasAnyRole("USER", "ORGANIZER", "ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/registrations/**").hasAnyRole("ORGANIZER", "ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/registrations/**").hasAnyRole("ORGANIZER", "ADMIN")

                        // 6. Attendance
                        .requestMatchers(HttpMethod.GET, "/api/attendance/**").hasAnyRole("USER", "ORGANIZER", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/attendance/**").hasAnyRole("USER", "ORGANIZER", "ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/attendance/**").hasAnyRole("USER", "ORGANIZER", "ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/attendance/**").hasAnyRole("USER", "ORGANIZER", "ADMIN")

                        // 7. Certificates
                        .requestMatchers(HttpMethod.GET, "/api/certificates/**").hasAnyRole("USER", "ORGANIZER", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/certificates/**").hasAnyRole("USER", "ORGANIZER", "ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/certificates/**").hasAnyRole("USER", "ORGANIZER", "ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/certificates/**").hasAnyRole("USER", "ORGANIZER", "ADMIN")

                        // 8. Teams
                        .requestMatchers(HttpMethod.GET, "/api/teams/**").hasAnyRole("USER", "ORGANIZER", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/teams/**").hasAnyRole("USER", "ORGANIZER", "ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/teams/**").hasAnyRole("USER", "ORGANIZER", "ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/teams/**").hasAnyRole("USER", "ORGANIZER", "ADMIN")

                        // 9. Team Members
                        .requestMatchers(HttpMethod.GET, "/api/team-members/**").hasAnyRole("USER", "ORGANIZER", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/team-members/**").hasAnyRole("USER", "ORGANIZER", "ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/team-members/**").hasAnyRole("USER", "ORGANIZER", "ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/team-members/**").hasAnyRole("USER", "ORGANIZER", "ADMIN")

                        // 10. AI Assistant
                        .requestMatchers(HttpMethod.POST, "/api/ai/assistant/chat").hasAnyRole("USER", "ORGANIZER", "ADMIN")
                        .requestMatchers("/api/ai/**").hasAnyRole("USER", "ORGANIZER", "ADMIN")

                        // Any other request under /api/** requires authentication
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().authenticated()
                )
                .authenticationProvider(authenticationProvider())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
        authProvider.setUserDetailsService(userDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder());
        return authProvider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        Set<String> origins = new LinkedHashSet<>();
        // 1. Explicitly allow the deployed production frontend origin
        origins.add(DEFAULT_PRODUCTION_FRONTEND);

        // 2. Read and parse any configured origins from CORS_ALLOWED_ORIGINS / cors.allowed-origins
        if (allowedOrigins != null && !allowedOrigins.isBlank()) {
            Arrays.stream(allowedOrigins.split(","))
                    .map(String::trim)
                    .map(origin -> origin.endsWith("/") ? origin.substring(0, origin.length() - 1) : origin)
                    .filter(origin -> !origin.isEmpty() && !origin.equals("*"))
                    .forEach(origins::add);
        }

        // 3. Keep local development origin as safe default
        origins.add(DEFAULT_LOCAL_FRONTEND);

        configuration.setAllowedOrigins(new ArrayList<>(origins));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of(
                "Authorization",
                "Content-Type",
                "X-Requested-With",
                "Accept",
                "Origin",
                "Access-Control-Request-Method",
                "Access-Control-Request-Headers"
        ));
        configuration.setExposedHeaders(List.of("Authorization", "Content-Type"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public FilterRegistrationBean<CorsFilter> corsFilterRegistrationBean() {
        FilterRegistrationBean<CorsFilter> bean = new FilterRegistrationBean<>(new CorsFilter(corsConfigurationSource()));
        bean.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return bean;
    }
}
