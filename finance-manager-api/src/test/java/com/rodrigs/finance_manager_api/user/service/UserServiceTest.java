package com.rodrigs.finance_manager_api.user.service;

import com.rodrigs.finance_manager_api.auth.JwtService;
import com.rodrigs.finance_manager_api.config.JwtProperties;
import com.rodrigs.finance_manager_api.shared.exception.EmailAlreadyRegisteredException;
import com.rodrigs.finance_manager_api.shared.exception.InvalidCredentialsException;
import com.rodrigs.finance_manager_api.user.dto.LoginRequestDTO;
import com.rodrigs.finance_manager_api.user.dto.LoginResponseDTO;
import com.rodrigs.finance_manager_api.user.dto.RegisterUserRequestDTO;
import com.rodrigs.finance_manager_api.user.dto.UserResponseDTO;
import com.rodrigs.finance_manager_api.user.entity.User;
import com.rodrigs.finance_manager_api.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.lang.reflect.Field;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private JwtService jwtService;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final JwtProperties jwtProperties = new JwtProperties("finance-manager-api-test", 3600, "test-secret");

    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(userRepository, passwordEncoder, jwtService, jwtProperties);
    }

    @Test
    void shouldRegisterUserWithHashedPassword() {
        RegisterUserRequestDTO request = new RegisterUserRequestDTO(
                " Rodrigo ", "RODRIGO@EMAIL.COM", "senha1234"
        );
        User savedUser = userWithId();
        savedUser.setName("Rodrigo");
        savedUser.setEmail("rodrigo@email.com");
        savedUser.setPasswordHash(passwordEncoder.encode("senha1234"));

        when(userRepository.existsByEmailIgnoreCase("rodrigo@email.com")).thenReturn(false);
        when(userRepository.saveAndFlush(any(User.class))).thenReturn(savedUser);

        UserResponseDTO response = userService.registerUser(request);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(userCaptor.capture());
        User persistedUser = userCaptor.getValue();

        assertThat(persistedUser.getName()).isEqualTo("Rodrigo");
        assertThat(persistedUser.getEmail()).isEqualTo("rodrigo@email.com");
        assertThat(persistedUser.getPasswordHash()).isNotEqualTo("senha1234");
        assertThat(passwordEncoder.matches("senha1234", persistedUser.getPasswordHash())).isTrue();
        assertThat(response.email()).isEqualTo("rodrigo@email.com");
    }

    @Test
    void shouldRejectDuplicateEmail() {
        RegisterUserRequestDTO request = new RegisterUserRequestDTO(
                "Rodrigo", "rodrigo@email.com", "senha1234"
        );
        when(userRepository.existsByEmailIgnoreCase("rodrigo@email.com")).thenReturn(true);

        assertThatThrownBy(() -> userService.registerUser(request))
                .isInstanceOf(EmailAlreadyRegisteredException.class);

        verify(userRepository, never()).saveAndFlush(any(User.class));
    }

    @Test
    void shouldLoginWithValidCredentials() {
        User user = userWithId();
        user.setPasswordHash(passwordEncoder.encode("senha1234"));
        when(userRepository.findByEmailIgnoreCase("rodrigo@email.com")).thenReturn(Optional.of(user));
        when(jwtService.generateAccessToken(user)).thenReturn("token-gerado");

        LoginResponseDTO response = userService.loginUser(
                new LoginRequestDTO("RODRIGO@EMAIL.COM", "senha1234")
        );

        assertThat(response.accessToken()).isEqualTo("token-gerado");
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresIn()).isEqualTo(3600);
        verify(jwtService).generateAccessToken(user);
    }

    @Test
    void shouldRejectInvalidPassword() {
        User user = userWithId();
        user.setPasswordHash(passwordEncoder.encode("senha-correta"));
        when(userRepository.findByEmailIgnoreCase("rodrigo@email.com")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> userService.loginUser(
                new LoginRequestDTO("rodrigo@email.com", "senha-incorreta")
        )).isInstanceOf(InvalidCredentialsException.class);

        verifyNoInteractions(jwtService);
    }

    @Test
    void shouldRejectUnknownEmail() {
        when(userRepository.findByEmailIgnoreCase("ausente@email.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.loginUser(
                new LoginRequestDTO("ausente@email.com", "senha1234")
        )).isInstanceOf(InvalidCredentialsException.class);

        verifyNoInteractions(jwtService);
    }

    private User userWithId() {
        User user = new User("Rodrigo", "rodrigo@email.com", "hashed-password");

        try {
            Field idField = User.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(user, UUID.randomUUID());
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Could not prepare test user", exception);
        }

        return user;
    }
}
