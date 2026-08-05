package com.rodrigs.finance_manager_api.auth;

import com.rodrigs.finance_manager_api.user.entity.User;
import com.rodrigs.finance_manager_api.user.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final UserRepository userRepository;

    public JwtAuthenticationFilter(JwtService jwtService, UserRepository userRepository) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        // Este filtro roda uma vez por requisição e tenta transformar um Bearer token válido
        // em uma autenticação reconhecida pelo Spring Security.
        String authorizationHeader = request.getHeader(AUTHORIZATION_HEADER);

        // Se não houver header Authorization, ou se ele não usar o formato Bearer,
        // a requisição continua anônima. Rotas protegidas serão bloqueadas depois pela SecurityFilterChain.
        if (authorizationHeader == null || !authorizationHeader.startsWith(BEARER_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        // Remove o prefixo "Bearer " e fica somente com a string do JWT.
        String token = authorizationHeader.substring(BEARER_PREFIX.length());

        // Token inválido, expirado ou malformado não autentica ninguém.
        // Deixamos a cadeia continuar sem SecurityContext para o Spring retornar 401 nas rotas protegidas.
        if (!jwtService.isTokenValid(token)) {
            filterChain.doFilter(request, response);
            return;
        }

        // O subject do JWT guarda o id do usuário autenticado.
        UUID userId = jwtService.extractUserId(token);

        // Buscamos o usuário no banco para garantir que o token aponta para um usuário ainda existente.
        userRepository.findById(userId).ifPresent(user -> authenticate(user));

        filterChain.doFilter(request, response);
    }

    private void authenticate(User user) {
        // Este objeto é a identidade interna da requisição autenticada.
        // Ele evita colocar a entidade JPA inteira, incluindo passwordHash, no SecurityContext.
        AuthenticatedUser authenticatedUser = new AuthenticatedUser(
                user.getId(),
                user.getEmail()
        );

        // O principal é quem está autenticado. Credentials ficam null porque a senha não é mais necessária
        // depois que o JWT foi validado. A lista vazia representa ausência de roles/permissões neste MVP.
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                        authenticatedUser,
                        null,
                        List.of()
                );

        // A partir daqui, esta requisição passa a ser considerada autenticada pelo Spring Security.
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
