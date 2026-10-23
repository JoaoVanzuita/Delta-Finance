package com.deltafinance.auth.api;

import jakarta.validation.constraints.NotBlank;

/** Corpo de login. */
public record LoginRequest(
    @NotBlank(message = ValidationMessages.USERNAME_REQUIRED) String username,
    @NotBlank(message = ValidationMessages.PASSWORD_REQUIRED) String password) {}
