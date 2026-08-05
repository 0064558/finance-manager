package com.rodrigs.finance_manager_api.config;

import com.rodrigs.finance_manager_api.auth.JwtAuthenticationFilter;
import com.rodrigs.finance_manager_api.auth.JwtService;
import com.rodrigs.finance_manager_api.user.repository.UserRepository;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.time.OffsetDateTime;

@Configuration
public class SecurityConfig {

    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    public SecurityConfig(JwtService jwtService, UserRepository userRepository, ObjectMapper objectMapper) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
    }

    @Bean
    // PasswordEncoder bean that uses BCrypt for password hashing
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    // SecurityFilterChain bean that configures the security settings
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable()) // Disable CSRF protection for stateless APIs
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)) // Set session management to stateless
                .authorizeHttpRequests(auth -> auth // Configure authorization rules
                        // Permit all requests to the specified endpoints (authentication and API documentation)
                        .requestMatchers(
                                "/api/v1/auth/register",
                                "/api/v1/auth/login",
                                "/api-docs/**",
                                "/swagger-ui.html",
                                "/swagger-ui/**",
                                "/error"
                        ).permitAll() // Allow all requests to the specified endpoints
                        // Require authentication for any other request
                        .anyRequest().authenticated()
                )
                .httpBasic(httpBasic -> httpBasic.disable()) // Disable HTTP Basic authentication and form login
                .formLogin(formLogin -> formLogin.disable()) // Disable form login
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint((request, response, authException) ->
                                writeProblemDetail(
                                        response,
                                        HttpStatus.UNAUTHORIZED,
                                        "Unauthorized",
                                        "Authentication is required to access this resource.",
                                        "AUTHENTICATION_REQUIRED",
                                        request.getRequestURI()
                                ))
                        .accessDeniedHandler((request, response, accessDeniedException) ->
                                writeProblemDetail(
                                        response,
                                        HttpStatus.FORBIDDEN,
                                        "Forbidden",
                                        "You do not have permission to access this resource.",
                                        "ACCESS_DENIED",
                                        request.getRequestURI()
                                ))
                )
                // Add the custom JWT authentication filter before the UsernamePasswordAuthenticationFilter
                .addFilterBefore(
                        new JwtAuthenticationFilter(jwtService, userRepository),
                        UsernamePasswordAuthenticationFilter.class
                )
                .build(); // Build the SecurityFilterChain and return it
    }

    private void writeProblemDetail(
            HttpServletResponse response,
            HttpStatus status,
            String title,
            String detail,
            String code,
            String path
    ) throws java.io.IOException {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(status, detail);
        problemDetail.setTitle(title);
        problemDetail.setType(URI.create("about:blank"));
        problemDetail.setInstance(URI.create(path));
        problemDetail.setProperty("code", code);
        problemDetail.setProperty("timestamp", OffsetDateTime.now());

        response.setStatus(status.value());
        response.setContentType("application/problem+json");
        objectMapper.writeValue(response.getOutputStream(), problemDetail);
    }
}
