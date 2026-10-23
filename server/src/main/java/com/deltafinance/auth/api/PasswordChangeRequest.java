package com.deltafinance.auth.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Corpo de troca de senha. */
public record PasswordChangeRequest(
    @NotBlank(message = ValidationMessages.CURRENT_PASSWORD_REQUIRED) String currentPassword,
    @NotBlank(message = ValidationMessages.NEW_PASSWORD_REQUIRED) @Size(min = 12, max = 128, message = ValidationMessages.NEW_PASSWORD_SIZE) String newPassword) {}
