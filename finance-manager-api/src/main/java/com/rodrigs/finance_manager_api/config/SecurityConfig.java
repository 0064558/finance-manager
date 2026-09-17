package com.rodrigs.finance_manager_api.config;

import com.rodrigs.finance_manager_api.auth.JwtAuthenticationFilter;
import com.rodrigs.finance_manager_api.auth.JwtService;
import com.rodrigs.finance_manager_api.shared.exception.ProblemDetailFactory;
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
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

@Configuration
public class SecurityConfig {

    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;
    private final ProblemDetailFactory problemDetailFactory;

    public SecurityConfig(
            JwtService jwtService,
            UserRepository userRepository,
            ObjectMapper objectMapper,
            ProblemDetailFactory problemDetailFactory
    ) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
        this.problemDetailFactory = problemDetailFactory;
    }

    @Bean
    // PasswordEncoder bean that uses BCrypt for password hashing
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    // SecurityFilterChain bean that configures the security settings
    public SecurityFilterChain securityFilterChain(HttpSecurity http, CorsConfigurationSource corsConfigurationSource) throws Exception {
        return http
                .cors(cors -> cors.configurationSource(corsConfigurationSource)) // Liga as configurações CORS definidas no bean corsConfigurationSource
                .csrf(csrf -> csrf.disable()) // Desabilita CSRF (Cross-Site Request Forgery) para simplificar a configuração de segurança
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)) // Seta a política de criação de sessão para STATELESS, indicando que o servidor não manterá estado de sessão entre requisições
                .authorizeHttpRequests(auth -> auth // Configura as regras de autorização para diferentes endpoints
                        // Permite todas as requisições para os endpoints de registro, login, documentação da API e monitoramento
                        .requestMatchers(
                                "/api/v1/auth/register",
                                "/api/v1/auth/login",
                                "/api-docs/**",
                                "/swagger-ui.html",
                                "/swagger-ui/**",
                                "/actuator/**",
                                "/error"
                        ).permitAll() // Permite acesso sem autenticação para os endpoints especificados
                        // Requer autenticação para todas as outras requisições
                        .anyRequest().authenticated()
                )
                .httpBasic(httpBasic -> httpBasic.disable()) // Desabilita autenticação HTTP básica
                .formLogin(formLogin -> formLogin.disable()) // Desabilita autenticação via formulário de login
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint((request, response, authException) ->
                                // Escreve detalhes do problema na resposta HTTP
                                writeProblemDetail(
                                        response,
                                        HttpStatus.UNAUTHORIZED,
                                        "Unauthorized",
                                        "Authentication is required to access this resource.",
                                        "AUTHENTICATION_REQUIRED",
                                        request
                                ))
                        .accessDeniedHandler((request, response, accessDeniedException) ->
                                writeProblemDetail(
                                        response,
                                        HttpStatus.FORBIDDEN,
                                        "Forbidden",
                                        "You do not have permission to access this resource.",
                                        "ACCESS_DENIED",
                                        request
                                ))
                )
                // Adiciona o filtro de autenticação JWT antes do filtro de autenticação padrão do Spring Security
                .addFilterBefore(
                        new JwtAuthenticationFilter(jwtService, userRepository),
                        UsernamePasswordAuthenticationFilter.class
                )
                .build(); // Constroi o SecurityFilterChain com as configurações definidas acima
    }

    // Ajuda a escrever detalhes do problema na resposta HTTP
    private void writeProblemDetail(
            HttpServletResponse response,
            HttpStatus status,
            String title,
            String detail,
            String code,
            jakarta.servlet.http.HttpServletRequest request
    ) throws java.io.IOException {
        ProblemDetail problemDetail = problemDetailFactory.create(status, title, detail, code, request);

        response.setStatus(status.value());
        response.setContentType("application/problem+json");
        objectMapper.writeValue(response.getOutputStream(), problemDetail);
    }


    // Indica que este método produz um bean gerenciado pelo Spring. Um bean é um objeto que é instanciado, montado e gerenciado pelo contêiner Spring.
    @Bean
    // Configura a origem, métodos e cabeçalhos permitidos para requisições CORS
    public CorsConfigurationSource corsConfigurationSource(CorsProperties corsProperties) {
        // Cria uma nova configuração CORS
        CorsConfiguration configuration = new CorsConfiguration();

        // Define as origens permitidas para requisições CORS com base nas propriedades fornecidas
        configuration.setAllowedOrigins(corsProperties.allowedOrigins());
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Content-Type", "Authorization"));

        // Cria uma fonte de configuração CORS baseada em URL e registra a configuração para os endpoints da API
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);

        // Retorna a fonte de configuração CORS para ser usada pelo Spring Security
        return source;
    }
}
