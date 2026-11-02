package com.deltafinance.connections.api;

import jakarta.validation.constraints.NotBlank;

/** Tratamento padrão de saldos reservados para as contas da conexão. */
public record ConnectionReservedBalanceTreatmentRequest(
    @NotBlank(message = "Informe o tratamento do saldo reservado.") String treatment) {}
