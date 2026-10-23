package com.deltafinance.security;

import com.deltafinance.shared.time.Clock;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Impõe o limite absoluto de 12 horas da sessão.
 *
 * <p>A inatividade de 30 minutos é tratada pelo próprio Spring Session. Este filtro complementa com
 * um teto absoluto, independente de atividade: uma sessão mais antiga é invalidada e a API responde
 * 401.
 */
public class AbsoluteSessionTimeoutFilter extends OncePerRequestFilter {

  private final Clock clock;
  private final Duration absoluteTimeout;
  private final ProblemHttpWriter writer;

  public AbsoluteSessionTimeoutFilter(
      Clock clock, Duration absoluteTimeout, ProblemHttpWriter writer) {
    this.clock = clock;
    this.absoluteTimeout = absoluteTimeout;
    this.writer = writer;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    HttpSession session = request.getSession(false);
    if (session == null) {
      filterChain.doFilter(request, response);
      return;
    }

    Object startedAt = session.getAttribute(SessionAttributes.STARTED_AT);
    if (!(startedAt instanceof Instant started)
        || clock.now().isBefore(started.plus(absoluteTimeout))) {
      filterChain.doFilter(request, response);
      return;
    }

    try {
      session.invalidate();
    } catch (IllegalStateException ignored) {
      // Sessão já invalidada por outro filtro/requisição.
    }
    SecurityContextHolder.clearContext();

    if (request.getRequestURI().startsWith("/api/")) {
      writer.unauthenticated(response);
      return;
    }
    filterChain.doFilter(request, response);
  }
}
