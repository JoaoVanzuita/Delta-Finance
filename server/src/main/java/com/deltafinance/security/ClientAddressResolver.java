package com.deltafinance.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Resolve o endereço do cliente para limitação de tentativas.
 *
 * <p>O primeiro valor de {@code X-Forwarded-For} é controlado pelo cliente e não pode ser usado
 * como identidade. Com um número fixo de proxies confiáveis, o cliente real é a entrada
 * imediatamente à esquerda dos proxies conhecidos; por isso a leitura é feita da direita para a
 * esquerda. {@code 0} ignora o cabeçalho (deploy sem proxy).
 */
@Component
public class ClientAddressResolver {

  private static final String FORWARDED_FOR = "X-Forwarded-For";

  private final int trustedProxyHops;

  public ClientAddressResolver(
      @Value("${delta.security.trusted-proxy-hops:2}") int trustedProxyHops) {
    this.trustedProxyHops = Math.max(0, trustedProxyHops);
  }

  public String resolve(HttpServletRequest request) {
    if (trustedProxyHops == 0) {
      return request.getRemoteAddr();
    }
    String header = request.getHeader(FORWARDED_FOR);
    if (header == null || header.isBlank()) {
      return request.getRemoteAddr();
    }
    String[] entries = header.split(",");
    int index = entries.length - trustedProxyHops;
    if (index < 0) {
      return request.getRemoteAddr();
    }
    String candidate = entries[index].trim();
    return candidate.isEmpty() ? request.getRemoteAddr() : candidate;
  }
}
