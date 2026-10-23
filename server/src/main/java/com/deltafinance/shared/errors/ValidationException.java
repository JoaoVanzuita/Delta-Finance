package com.deltafinance.shared.errors;

import java.util.List;
import org.springframework.http.HttpStatus;

/** Erro de validação com detalhes por campo (HTTP 422). */
public class ValidationException extends ApiException {

  public record FieldError(String field, String message) {}

  private final List<FieldError> errors;

  public ValidationException(List<FieldError> errors) {
    super(
        HttpStatus.UNPROCESSABLE_ENTITY,
        ErrorCode.VALIDATION_FAILED,
        "Um ou mais campos são inválidos.");
    this.errors = List.copyOf(errors);
  }

  public ValidationException(String field, String message) {
    this(List.of(new FieldError(field, message)));
  }

  public List<FieldError> errors() {
    return errors;
  }
}
