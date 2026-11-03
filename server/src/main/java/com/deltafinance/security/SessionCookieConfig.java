package com.deltafinance.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.DefaultCookieSerializer;

/**
 * Cookie de sessão explícito (não depende apenas da configuração do container).
 *
 * <p>Em {@code tls}, usa o prefixo {@code __Host-}, {@code HttpOnly}, {@code Secure}, {@code
 * SameSite=Lax} e {@code Path=/}. Em {@code lan-http}, usa {@code delta_session} sem {@code
 * Secure}, porque o prefixo {@code __Host-} exige HTTPS e o navegador rejeitaria o cookie em HTTP.
 */
@Configuration
public class SessionCookieConfig {

  @Bean
  CookieSerializer sessionCookieSerializer(
      @Value("${delta.deployment.mode:tls}") String deploymentMode,
      @Value("${server.servlet.session.cookie.name:__Host-delta_session}") String name,
      @Value("${server.servlet.session.cookie.http-only:true}") boolean httpOnly,
      @Value("${server.servlet.session.cookie.secure:true}") boolean secure,
      @Value("${server.servlet.session.cookie.same-site:lax}") String sameSite,
      @Value("${server.servlet.session.cookie.path:/}") String path) {
    boolean lanHttp = "lan-http".equalsIgnoreCase(deploymentMode.trim());
    DefaultCookieSerializer serializer = new DefaultCookieSerializer();
    serializer.setCookieName(lanHttp ? "delta_session" : name);
    serializer.setUseHttpOnlyCookie(httpOnly);
    serializer.setUseSecureCookie(!lanHttp && secure);
    serializer.setSameSite(sameSite);
    serializer.setCookiePath(path);
    return serializer;
  }
}
