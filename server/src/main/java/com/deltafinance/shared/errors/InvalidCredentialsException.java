package com.deltafinance.shared.errors;

import org.springframework.http.HttpStatus;

/** Credenciais inválidas, sem distinguir usuário inexistente de senha incorreta (HTTP 401). */
public class InvalidCredentialsException extends ApiException {

  public InvalidCredentialsException() {
    super(HttpStatus.UNAUTHORIZED, ErrorCode.INVALID_CREDENTIALS, "Usuário ou senha inválidos.");
  }
}
