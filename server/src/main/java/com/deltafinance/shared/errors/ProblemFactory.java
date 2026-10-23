package com.deltafinance.shared.errors;

import java.net.URI;
import java.util.List;
import java.util.Locale;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;

/** Constrói ProblemDetail (RFC 9457) consistente para erros da API. */
@Component
public class ProblemFactory {

  public static final String REQUEST_ID_KEY = "requestId";

  public ProblemDetail problem(HttpStatus status, String code, String detail, String path) {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
    problem.setTitle(status.getReasonPhrase());
    problem.setType(URI.create("urn:delta-finance:error:" + code.toLowerCase(Locale.ROOT)));
    if (path != null && !path.isBlank()) {
      problem.setInstance(URI.create(path));
    }
    problem.setProperty("code", code);
    String requestId = MDC.get(REQUEST_ID_KEY);
    if (requestId != null) {
      problem.setProperty("requestId", requestId);
    }
    return problem;
  }

  public ProblemDetail validation(ValidationException exception, String path) {
    ProblemDetail problem =
        problem(
            HttpStatus.UNPROCESSABLE_ENTITY,
            ErrorCode.VALIDATION_FAILED,
            exception.getMessage(),
            path);
    List<ValidationException.FieldError> errors = exception.errors();
    problem.setProperty(
        "errors",
        errors.stream()
            .map(error -> java.util.Map.of("field", error.field(), "message", error.message()))
            .toList());
    return problem;
  }
}
