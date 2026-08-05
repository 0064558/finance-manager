package com.rodrigs.finance_manager_api.auth;

import java.util.UUID;

public record AuthenticatedUser(
        UUID id,
        String email
) {
}
