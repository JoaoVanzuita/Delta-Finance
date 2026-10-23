package com.deltafinance.shared.errors;

import com.deltafinance.shared.logging.SensitiveDataMasker;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Traduz exceções para ProblemDetail sem expor stack trace, SQL, segredo ou resposta do provedor.
 *
 * <p>O {@code requestId} permite correlacionar a resposta com o log interno correspondente.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final Logger LOG = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  private final ProblemFactory problems;

  public GlobalExceptionHandler(ProblemFactory problems) {
    this.problems = problems;
  }

  @ExceptionHandler(ApiException.class)
  public ResponseEntity<ProblemDetail> handleApiException(
      ApiException exception, HttpServletRequest request) {
    ProblemDetail problem;
    if (exception instanceof ValidationException validationException) {
      problem = problems.validation(validationException, request.getRequestURI());
    } else {
      problem =
          problems.problem(
              exception.status(),
              exception.code(),
              exception.getMessage(),
              request.getRequestURI());
    }

    ResponseEntity.BodyBuilder builder = ResponseEntity.status(exception.status());
    if (exception instanceof TooManyRequestsException tooManyRequests) {
      builder.header(HttpHeaders.RETRY_AFTER, String.valueOf(tooManyRequests.retryAfterSeconds()));
    }
    return builder.body(problem);
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ProblemDetail> handleBeanValidation(
      MethodArgumentNotValidException exception, HttpServletRequest request) {
    List<ValidationException.FieldError> errors =
        exception.getBindingResult().getFieldErrors().stream().map(this::toFieldError).toList();
    return handleApiException(new ValidationException(errors), request);
  }

  @ExceptionHandler(HandlerMethodValidationException.class)
  public ResponseEntity<ProblemDetail> handleMethodValidation(
      HandlerMethodValidationException exception, HttpServletRequest request) {
    List<ValidationException.FieldError> errors =
        exception.getParameterValidationResults().stream()
            .flatMap(
                result ->
                    result.getResolvableErrors().stream()
                        .map(
                            error ->
                                new ValidationException.FieldError(
                                    result.getMethodParameter().getParameterName(),
                                    safeMessage(error.getDefaultMessage()))))
            .toList();
    return handleApiException(new ValidationException(errors), request);
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<ProblemDetail> handleUnreadableBody(
      HttpMessageNotReadableException exception, HttpServletRequest request) {
    return handleApiException(
        new ApiException(
            HttpStatus.BAD_REQUEST,
            ErrorCode.INVALID_REQUEST,
            "Corpo da requisição inválido ou malformado."),
        request);
  }

  @ExceptionHandler({
    MissingServletRequestParameterException.class,
    MethodArgumentTypeMismatchException.class
  })
  public ResponseEntity<ProblemDetail> handleBadRequest(
      Exception exception, HttpServletRequest request) {
    return handleApiException(
        new ApiException(
            HttpStatus.BAD_REQUEST, ErrorCode.INVALID_REQUEST, "Parâmetro de requisição inválido."),
        request);
  }

  @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
  public ResponseEntity<ProblemDetail> handleMethodNotAllowed(
      HttpRequestMethodNotSupportedException exception, HttpServletRequest request) {
    return handleApiException(
        new ApiException(
            HttpStatus.METHOD_NOT_ALLOWED, ErrorCode.INVALID_REQUEST, "Método não permitido."),
        request);
  }

  @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
  public ResponseEntity<ProblemDetail> handleUnsupportedMediaType(
      HttpMediaTypeNotSupportedException exception, HttpServletRequest request) {
    return handleApiException(
        new ApiException(
            HttpStatus.UNSUPPORTED_MEDIA_TYPE,
            ErrorCode.INVALID_REQUEST,
            "Tipo de conteúdo não suportado."),
        request);
  }

  @ExceptionHandler(NoResourceFoundException.class)
  public ResponseEntity<ProblemDetail> handleNoResource(
      NoResourceFoundException exception, HttpServletRequest request) {
    return handleApiException(new NotFoundException(), request);
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ProblemDetail> handleUnexpected(
      Exception exception, HttpServletRequest request) {
    // O resumo é sanitizado; o stack completo fica em DEBUG para não vazar
    // valores financeiros/SQL em logs de produção (T029).
    LOG.error(
        "Erro inesperado ao processar requisição {} {}: {}",
        request.getMethod(),
        request.getRequestURI(),
        SensitiveDataMasker.mask(
            exception.getClass().getSimpleName() + ": " + exception.getMessage()));
    LOG.debug("Detalhes do erro inesperado", exception);
    return handleApiException(
        new ApiException(
            HttpStatus.INTERNAL_SERVER_ERROR,
            ErrorCode.INTERNAL_ERROR,
            "Erro interno. Tente novamente mais tarde."),
        request);
  }

  private ValidationException.FieldError toFieldError(FieldError fieldError) {
    return new ValidationException.FieldError(
        fieldError.getField(), safeMessage(fieldError.getDefaultMessage()));
  }

  private static String safeMessage(String message) {
    return message == null || message.isBlank() ? "Valor inválido" : message;
  }
}
