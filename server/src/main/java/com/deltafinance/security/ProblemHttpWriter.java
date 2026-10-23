package com.deltafinance.security;

import com.deltafinance.shared.errors.ErrorCode;
import com.deltafinance.shared.errors.ProblemFactory;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/** Escreve respostas de erro da camada de segurança no mesmo formato da API. */
@Component
public class ProblemHttpWriter {

  private final ProblemFactory problemFactory;
  private final JsonMapper jsonMapper;

  public ProblemHttpWriter(ProblemFactory problemFactory, JsonMapper jsonMapper) {
    this.problemFactory = problemFactory;
    this.jsonMapper = jsonMapper;
  }

  public void write(HttpServletResponse response, HttpStatus status, String code, String detail)
      throws IOException {
    ProblemDetail problem = problemFactory.problem(status, code, detail, null);
    response.setStatus(status.value());
    response.setContentType("application/problem+json");
    response.setCharacterEncoding("UTF-8");
    jsonMapper.writeValue(response.getOutputStream(), problem);
  }

  public void unauthenticated(HttpServletResponse response) throws IOException {
    write(response, HttpStatus.UNAUTHORIZED, ErrorCode.UNAUTHENTICATED, "Autenticação necessária.");
  }
}
