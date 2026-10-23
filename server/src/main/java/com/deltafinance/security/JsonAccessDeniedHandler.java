package com.deltafinance.security;

import com.deltafinance.shared.errors.ErrorCode;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.stereotype.Component;

/** 403 em JSON; falhas de CSRF recebem código próprio, sem detalhes internos. */
@Component
public class JsonAccessDeniedHandler implements AccessDeniedHandler {

  private final ProblemHttpWriter writer;

  public JsonAccessDeniedHandler(ProblemHttpWriter writer) {
    this.writer = writer;
  }

  @Override
  public void handle(
      HttpServletRequest request,
      HttpServletResponse response,
      AccessDeniedException accessDeniedException)
      throws IOException, ServletException {
    if (accessDeniedException instanceof CsrfException) {
      writer.write(
          response,
          HttpStatus.FORBIDDEN,
          ErrorCode.CSRF_INVALID,
          "Requisição rejeitada: token CSRF ausente ou inválido.");
      return;
    }
    writer.write(response, HttpStatus.FORBIDDEN, ErrorCode.ACCESS_DENIED, "Acesso negado.");
  }
}
