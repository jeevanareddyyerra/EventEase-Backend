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
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import org.springframework.beans.factory.annotation.Value;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Value("${cors.allowed-origins:http://localhost:5173}")
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
        List<String> origins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .collect(Collectors.toList());
        configuration.setAllowedOrigins(origins.isEmpty() ? List.of("http://localhost:5173") : origins);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
