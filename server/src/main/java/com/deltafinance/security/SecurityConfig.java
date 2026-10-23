package com.deltafinance.security;

import com.deltafinance.shared.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.annotation.web.configurers.RequestCacheConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;

/**
 * Configuração de segurança da aplicação.
 *
 * <ul>
 *   <li>sessão por cookie {@code __Host-delta_session}, 30 min de inatividade (Spring Session) e 12
 *       h absolutas ({@link AbsoluteSessionTimeoutFilter});
 *   <li>CSRF por cookie {@code XSRF-TOKEN} + header {@code X-XSRF-TOKEN};
 *   <li>headers de segurança e CSP restritiva;
 *   <li>401/403 em JSON (nunca redirect/HTML);
 *   <li>logout explícito no controller, com invalidação de sessão.
 * </ul>
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@EnableConfigurationProperties(SessionProperties.class)
public class SecurityConfig {

  private static final String CSP =
      "default-src 'self'; "
          + "script-src 'self'; "
          + "style-src 'self' 'unsafe-inline'; "
          + "img-src 'self' data:; "
          + "font-src 'self' data:; "
          + "connect-src 'self'; "
          + "form-action 'self'; "
          + "frame-ancestors 'none'; "
          + "base-uri 'self'; "
          + "object-src 'none'";

  @Bean
  SecurityContextRepository securityContextRepository() {
    return new HttpSessionSecurityContextRepository();
  }

  @Bean
  SecurityFilterChain securityFilterChain(
      HttpSecurity http,
      SecurityContextRepository securityContextRepository,
      ProblemHttpWriter problemWriter,
      Clock clock,
      SessionProperties sessionProperties)
      throws Exception {

    CookieCsrfTokenRepository csrfTokenRepository = CookieCsrfTokenRepository.withHttpOnlyFalse();
    csrfTokenRepository.setCookieCustomizer(
        cookie -> cookie.path("/").sameSite("Lax").secure(true));

    http.csrf(
            csrf ->
                csrf.csrfTokenRepository(csrfTokenRepository)
                    .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()))
        .securityContext(
            context ->
                context
                    .securityContextRepository(securityContextRepository)
                    .requireExplicitSave(true))
        .sessionManagement(
            session -> session.sessionFixation(fixation -> fixation.changeSessionId()))
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers(
                        "/api/v1/auth/csrf", "/api/v1/auth/register", "/api/v1/auth/login")
                    .permitAll()
                    .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info")
                    .permitAll()
                    .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .exceptionHandling(
            exceptions ->
                exceptions
                    .authenticationEntryPoint(new JsonAuthenticationEntryPoint(problemWriter))
                    .accessDeniedHandler(new JsonAccessDeniedHandler(problemWriter)))
        .headers(
            headers ->
                headers
                    .contentSecurityPolicy(csp -> csp.policyDirectives(CSP))
                    .frameOptions(HeadersConfigurer.FrameOptionsConfig::deny)
                    .httpStrictTransportSecurity(
                        hsts -> hsts.includeSubDomains(true).maxAgeInSeconds(31_536_000))
                    .referrerPolicy(
                        referrer -> referrer.policy(ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                    .permissionsPolicyHeader(
                        policy ->
                            policy.policy("camera=(), microphone=(), geolocation=(), payment=()")))
        // API sem páginas protegidas: não guardar requisição para redirect (evita sessão anônima).
        .requestCache(RequestCacheConfigurer::disable)
        .logout(AbstractHttpConfigurer::disable)
        .httpBasic(AbstractHttpConfigurer::disable)
        .formLogin(AbstractHttpConfigurer::disable)
        .addFilterAfter(new CsrfCookieFilter(), CsrfFilter.class)
        .addFilterAfter(
            new AbsoluteSessionTimeoutFilter(
                clock, sessionProperties.getAbsoluteTimeout(), problemWriter),
            SecurityContextHolderFilter.class);

    return http.build();
  }
}
