package com.deltafinance.security;

import com.deltafinance.shared.audit.AuditEventType;
import com.deltafinance.shared.audit.AuditService;
import com.deltafinance.shared.errors.ErrorCode;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.stereotype.Component;

/** 403 em JSON; falhas de CSRF recebem código próprio, sem detalhes internos. */
@Component
public class JsonAccessDeniedHandler implements AccessDeniedHandler {

  private static final int MAX_PATH_LENGTH = 200;

  private final ProblemHttpWriter writer;
  private final AuditService auditService;

  public JsonAccessDeniedHandler(ProblemHttpWriter writer, AuditService auditService) {
    this.writer = writer;
    this.auditService = auditService;
  }

  @Override
  public void handle(
      HttpServletRequest request,
      HttpServletResponse response,
      AccessDeniedException accessDeniedException)
      throws IOException, ServletException {
    if (accessDeniedException instanceof CsrfException) {
      audit(AuditEventType.CSRF_REJECTED, request);
      writer.write(
          response,
          HttpStatus.FORBIDDEN,
          ErrorCode.CSRF_INVALID,
          "Requisição rejeitada: token CSRF ausente ou inválido.");
      return;
    }
    audit(AuditEventType.AUTHORIZATION_DENIED, request);
    writer.write(response, HttpStatus.FORBIDDEN, ErrorCode.ACCESS_DENIED, "Acesso negado.");
  }

  /** Auditoria auxiliar: nunca impede a resposta 403 nem inclui corpo/segredo. */
  private void audit(AuditEventType eventType, HttpServletRequest request) {
    try {
      auditService.record(
          authenticatedUserId(),
          eventType,
          "request",
          null,
          Map.of("method", request.getMethod(), "path", safePath(request)));
    } catch (RuntimeException ignored) {
      // Falha de auditoria não pode transformar 403 em 500.
    }
  }

  private static String safePath(HttpServletRequest request) {
    String path = request.getRequestURI();
    if (path == null) {
      return "";
    }
    return path.length() <= MAX_PATH_LENGTH ? path : path.substring(0, MAX_PATH_LENGTH);
  }

  private static UUID authenticatedUserId() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication != null
        && authentication.isAuthenticated()
        && authentication.getPrincipal() instanceof UserPrincipal principal) {
      return principal.id();
    }
    return null;
  }
}
