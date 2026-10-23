package com.deltafinance.integrations.api;

import jakarta.validation.constraints.NotBlank;

/** Credenciais write-only da integração pessoal. */
public record IntegrationCredentialsRequest(
    @NotBlank(message = "Informe o Client ID.") String clientId,
    @NotBlank(message = "Informe o Client Secret.") String clientSecret) {}
