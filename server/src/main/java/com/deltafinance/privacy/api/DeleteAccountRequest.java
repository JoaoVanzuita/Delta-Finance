package com.deltafinance.privacy.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Confirmação explícita de exclusão da conta. */
public record DeleteAccountRequest(
    @NotBlank(message = "Informe sua senha.") @Size(max = 128, message = "Senha inválida.") String password,
    @NotBlank(message = "Digite EXCLUIR para confirmar.") String confirmation) {}
