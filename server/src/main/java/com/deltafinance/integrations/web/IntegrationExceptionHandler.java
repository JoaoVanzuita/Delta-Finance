package com.deltafinance.integrations.web;

import com.deltafinance.integrations.infrastructure.PluggyCredentialException;
import com.deltafinance.integrations.infrastructure.PluggyException;
import com.deltafinance.integrations.infrastructure.PluggyRateLimitedException;
import com.deltafinance.integrations.infrastructure.PluggyUnauthorizedException;
import com.deltafinance.shared.errors.ErrorCode;
import com.deltafinance.shared.errors.ProblemFactory;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Traduz falhas do provedor para 502 sanitizado.
 *
 * <p>Nunca repassa corpo bruto, status interno, segredo ou mensagem do provedor ao cliente.
 */
@RestControllerAdvice
public class IntegrationExceptionHandler {

  private final ProblemFactory problemFactory;

  public IntegrationExceptionHandler(ProblemFactory problemFactory) {
    this.problemFactory = problemFactory;
  }

  @ExceptionHandler({PluggyCredentialException.class, PluggyUnauthorizedException.class})
  public ResponseEntity<ProblemDetail> credentials(
      PluggyException exception, HttpServletRequest request) {
    return response(
        HttpStatus.BAD_GATEWAY,
        ErrorCode.PROVIDER_AUTH_FAILED,
        "Não foi possível validar as credenciais com o provedor.",
        request,
        null);
  }

  @ExceptionHandler(PluggyRateLimitedException.class)
  public ResponseEntity<ProblemDetail> rateLimited(
      PluggyRateLimitedException exception, HttpServletRequest request) {
    return response(
        HttpStatus.BAD_GATEWAY,
        ErrorCode.PROVIDER_RATE_LIMITED,
        "O provedor está limitando requisições. Tente novamente em instantes.",
        request,
        exception.retryAfter().toSeconds());
  }

  @ExceptionHandler(PluggyException.class)
  public ResponseEntity<ProblemDetail> unavailable(
      PluggyException exception, HttpServletRequest request) {
    return response(
        HttpStatus.BAD_GATEWAY,
        ErrorCode.PROVIDER_UNAVAILABLE,
        "O provedor está indisponível no momento.",
        request,
        null);
  }

  private ResponseEntity<ProblemDetail> response(
      HttpStatus status,
      String code,
      String detail,
      HttpServletRequest request,
      Long retryAfterSeconds) {
    ProblemDetail problem = problemFactory.problem(status, code, detail, request.getRequestURI());
    ResponseEntity.BodyBuilder builder = ResponseEntity.status(status);
    if (retryAfterSeconds != null) {
      builder.header(HttpHeaders.RETRY_AFTER, String.valueOf(Math.max(1, retryAfterSeconds)));
    }
    return builder.body(problem);
  }
}
