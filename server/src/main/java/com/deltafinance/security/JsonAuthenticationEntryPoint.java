package com.deltafinance.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

/** 401 em JSON para APIs, em vez de redirecionar para uma página de login. */
@Component
public class JsonAuthenticationEntryPoint implements AuthenticationEntryPoint {

  private final ProblemHttpWriter writer;

  public JsonAuthenticationEntryPoint(ProblemHttpWriter writer) {
    this.writer = writer;
  }

  @Override
  public void commence(
      HttpServletRequest request,
      HttpServletResponse response,
      AuthenticationException authException)
      throws IOException, ServletException {
    writer.unauthenticated(response);
  }
}
