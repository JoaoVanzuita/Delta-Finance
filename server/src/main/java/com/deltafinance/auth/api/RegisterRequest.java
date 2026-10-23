package com.deltafinance.auth.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Corpo de cadastro conforme o contrato OpenAPI. */
public record RegisterRequest(
    @NotBlank(message = ValidationMessages.USERNAME_REQUIRED) @Size(min = 3, max = 40, message = ValidationMessages.USERNAME_SIZE) String username,
    @NotBlank(message = ValidationMessages.PASSWORD_REQUIRED) @Size(min = 12, max = 128, message = ValidationMessages.PASSWORD_SIZE) String password) {}
