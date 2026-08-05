package com.rodrigs.finance_manager_api.user.controller;

import com.rodrigs.finance_manager_api.user.dto.RegisterUserRequestDTO;
import com.rodrigs.finance_manager_api.user.dto.UserResponseDTO;
import com.rodrigs.finance_manager_api.user.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    // Endpoint to register a new user
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED) // Set the response status to 201 Created
    public UserResponseDTO registerUser(
            @Valid // Validate the request body
            @RequestBody RegisterUserRequestDTO request // Bind the request body to the DTO
    ) {
        // Call the service layer to handle user registration and return the response DTO
        return userService.registerUser(request);
    }
}
