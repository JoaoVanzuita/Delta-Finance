package com.deltafinance.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Força a materialização do token CSRF para que o cookie {@code XSRF-TOKEN} seja emitido.
 *
 * <p>Spring Security 6+ usa {@code CsrfToken} de forma preguiçosa (deferred); sem esta leitura
 * explícita, o cookie pode não ser gravado em GETs e a SPA não teria como enviar o header.
 */
public class CsrfCookieFilter extends OncePerRequestFilter {

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    CsrfToken token = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
    if (token != null) {
      token.getToken();
    }
    filterChain.doFilter(request, response);
  }
}
