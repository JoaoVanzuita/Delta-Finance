package com.deltafinance.auth.application;

/** Comando de troca de senha. */
public record PasswordChangeCommand(String currentPassword, String newPassword) {}
