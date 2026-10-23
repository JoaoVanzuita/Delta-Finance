package com.deltafinance.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.DefaultCookieSerializer;

/**
 * Cookie de sessão explícito (não depende apenas da configuração do container).
 *
 * <p>Nome com prefixo {@code __Host-}, {@code HttpOnly}, {@code Secure}, {@code SameSite=Lax} e
 * {@code Path=/}. Definir o bean também garante o mesmo comportamento em testes com MockMvc.
 */
@Configuration
public class SessionCookieConfig {

  @Bean
  CookieSerializer sessionCookieSerializer(
      @Value("${server.servlet.session.cookie.name:__Host-delta_session}") String name,
      @Value("${server.servlet.session.cookie.http-only:true}") boolean httpOnly,
      @Value("${server.servlet.session.cookie.secure:true}") boolean secure,
      @Value("${server.servlet.session.cookie.same-site:lax}") String sameSite,
      @Value("${server.servlet.session.cookie.path:/}") String path) {
    DefaultCookieSerializer serializer = new DefaultCookieSerializer();
    serializer.setCookieName(name);
    serializer.setUseHttpOnlyCookie(httpOnly);
    serializer.setUseSecureCookie(secure);
    serializer.setSameSite(sameSite);
    serializer.setCookiePath(path);
    return serializer;
  }
}
