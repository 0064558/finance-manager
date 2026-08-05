package com.rodrigs.finance_manager_api.user.service;

import com.rodrigs.finance_manager_api.user.dto.RegisterUserRequestDTO;
import com.rodrigs.finance_manager_api.user.dto.UserResponseDTO;
import com.rodrigs.finance_manager_api.user.entity.User;
import com.rodrigs.finance_manager_api.user.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
public class UserService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    // dependency injection
    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public UserResponseDTO registerUser(RegisterUserRequestDTO request) {
        // Normalize input before validation and persistence so equivalent values are treated equally.
        String name = request.name().trim();
        String email = request.email().trim().toLowerCase(Locale.ROOT);

        // Keep one account per email. The database unique index is still the final protection.
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new IllegalArgumentException("Email already exists");
        }

        // Store only the BCrypt hash. The raw password must never be persisted or returned.
        String passwordHash = passwordEncoder.encode(request.password());

        // Build the entity using only user-controlled fields; id and timestamps are generated later.
        User user = new User(name, email, passwordHash);

        // Persist the user and use the managed result to read generated fields such as id and createdAt.
        User savedUser = userRepository.save(user);

        // Return the public API shape. Sensitive fields such as passwordHash stay inside the entity.
        return new UserResponseDTO(
                savedUser.getId(),
                savedUser.getName(),
                savedUser.getEmail(),
                savedUser.getCreatedAt()
        );
    }
}
