package com.rodrigs.finance_manager_api.user.service;

import com.rodrigs.finance_manager_api.config.JwtProperties;
import com.rodrigs.finance_manager_api.auth.JwtService;
import com.rodrigs.finance_manager_api.shared.exception.OnboardingVersionLaterException;
import com.rodrigs.finance_manager_api.user.dto.*;
import com.rodrigs.finance_manager_api.user.entity.User;
import com.rodrigs.finance_manager_api.user.repository.UserRepository;
import com.rodrigs.finance_manager_api.shared.exception.EmailAlreadyRegisteredException;
import com.rodrigs.finance_manager_api.shared.exception.InvalidCredentialsException;
import com.rodrigs.finance_manager_api.shared.exception.UserNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.UUID;

@Service
public class UserService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final JwtProperties jwtProperties;

    // Constante para a versão atual do onboarding. Isso é útil para controlar o progresso do usuário no processo de onboarding e garantir que ele esteja atualizado com as mudanças na experiência do usuário.
    // Static serve para que a constante seja compartilhada entre todas as instâncias da classe UserService, garantindo consistência na versão do onboarding em toda a aplicação.
    private static final int CURRENT_ONBOARDING_VERSION = 1;

    // dependency injection
    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService, JwtProperties jwtProperties) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.jwtProperties = jwtProperties;
    }

    /* Register a new user.
    * @param request the user registration request
    * @return the registered user's response
    */
    @Transactional
    public UserResponseDTO registerUser(RegisterUserRequestDTO request) {
        // Normalize input before validation and persistence so equivalent values are treated equally.
        String name = request.name().trim();
        String email = request.email().trim().toLowerCase(Locale.ROOT);

        // Keep one account per email. The database unique index is still the final protection.
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new EmailAlreadyRegisteredException();
        }

        // Store only the BCrypt hash. The raw password must never be persisted or returned.
        String passwordHash = passwordEncoder.encode(request.password());

        // Build the entity using only user-controlled fields; id and timestamps are generated later.
        User user = new User(name, email, passwordHash);

        // Persist the user and use the managed result to read generated fields such as id and createdAt.
        User savedUser = userRepository.saveAndFlush(user);

        // Return the public API shape. Sensitive fields such as passwordHash stay inside the entity.
        return toResponse(savedUser);
    }

    /**
     * Log in a user.
     * @param request the login request
     * @return the login response
     */
    @Transactional(readOnly = true)
    public LoginResponseDTO loginUser(LoginRequestDTO request) {
        // Normalize email so case and surrounding spaces do not affect credential lookup.
        String email = request.email().trim().toLowerCase(Locale.ROOT);

        // Validate the user credentials and retrieve the user entity from the database.
        User user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(InvalidCredentialsException::new);

        // Check if the provided password matches the stored password hash using the password encoder.
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }

        // Generate a JWT access token for the authenticated user.
        String accessToken = jwtService.generateAccessToken(user);

        return new LoginResponseDTO(
                accessToken,
                "Bearer",
                jwtProperties.expirationSeconds(),
                toResponse(user)
        );
    }

    @Transactional(readOnly = true)
    public UserResponseDTO findAuthenticatedUser(UUID authenticatedUserId) {
        User user = userRepository.findById(authenticatedUserId)
                .orElseThrow(UserNotFoundException::new);

        return toResponse(user);
    }

    /**
     * Updates the onboarding version for the authenticated user.
     * @param authenticatedUserId the ID of the authenticated user
     * @param requestDTO the request containing the new onboarding version
     * @return the updated user response
     **/
    @Transactional
    public UserResponseDTO updateOnboardingVersion(UUID authenticatedUserId, UpdateOnboardingRequestDTO requestDTO) {
        // busca o usuário autenticado pelo ID fornecido. Se não for encontrado, lança uma exceção UserNotFoundException.
        User user = userRepository.findById(authenticatedUserId)
                .orElseThrow(UserNotFoundException::new);

        // Verifica se a versão de onboarding fornecida na solicitação é maior do que a versão atual definida na constante CURRENT_ONBOARDING_VERSION.
        // Se for maior, lança uma exceção OnboardingVersionLaterException.
        if (requestDTO.onboardingVersion() > CURRENT_ONBOARDING_VERSION) {
            throw new OnboardingVersionLaterException();
        }

        // Avança a versão de onboarding do usuário para a versão fornecida na solicitação.
        user.advanceOnboardingVersion(requestDTO.onboardingVersion());

        return toResponse(user);
    }

    private UserResponseDTO toResponse(User user) {
        return new UserResponseDTO(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getCreatedAt(),
                user.getOnboardingVersion()
        );
    }
}
