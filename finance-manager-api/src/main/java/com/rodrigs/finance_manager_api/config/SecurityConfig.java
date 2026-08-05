package com.rodrigs.finance_manager_api.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

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
                                "/api/v1/auth/**",
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
                .build(); // Build the SecurityFilterChain and return it
    }
}
