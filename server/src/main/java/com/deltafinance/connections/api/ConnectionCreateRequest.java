package com.deltafinance.connections.api;

import jakarta.validation.constraints.NotBlank;

public record ConnectionCreateRequest(
    @NotBlank(message = "Informe o itemId da conexão.") String itemId) {}
