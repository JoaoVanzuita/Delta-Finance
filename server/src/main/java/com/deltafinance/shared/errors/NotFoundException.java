package com.deltafinance.shared.errors;

import org.springframework.http.HttpStatus;

/** Recurso inexistente ou pertencente a outro usuário (HTTP 404, sem revelar existência). */
public class NotFoundException extends ApiException {

  public NotFoundException() {
    super(HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND, "Recurso não encontrado.");
  }

  public NotFoundException(String message) {
    super(HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND, message);
  }
}
