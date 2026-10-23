package com.deltafinance.connections.api;

import jakarta.validation.constraints.NotBlank;

public record ConnectionUpdateRequest(
    @NotBlank(message = "Informe o novo itemId da conexão.") String itemId) {}
