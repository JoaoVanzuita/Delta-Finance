package com.deltafinance.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Baseline de segurança do esqueleto.
 *
 * <p>Nega todo acesso por padrão e libera apenas os endpoints de health usados pelos health checks
 * do Compose/CI. Sessão, CSRF, headers, login e logout são implementados na tarefa T024; até lá
 * nenhum endpoint de negócio existe. Manter {@code denyAll} garante que um endpoint adicionado sem
 * autorização explícita falhe fechado.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

  @Bean
  SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http.authorizeHttpRequests(
            auth ->
                auth.requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info")
                    .permitAll()
                    .anyRequest()
                    .denyAll())
        .httpBasic(AbstractHttpConfigurer::disable)
        .formLogin(AbstractHttpConfigurer::disable)
        .logout(AbstractHttpConfigurer::disable);
    return http.build();
  }
}
