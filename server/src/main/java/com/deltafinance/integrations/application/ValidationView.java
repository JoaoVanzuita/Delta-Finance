package com.deltafinance.integrations.application;

/** Resultado de validação de credenciais sem persistência. */
public record ValidationView(boolean valid, String message, boolean itemValidated) {}
