package com.deltafinance;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;

/**
 * Ponto de entrada do Delta Finance.
 *
 * <p>{@link UserDetailsServiceAutoConfiguration} é excluído de propósito: as credenciais são
 * validadas pelo módulo {@code auth} (tarefa T025), nunca por um usuário em memória gerado pelo
 * Spring Boot cujo segredo apareceria nos logs.
 */
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class DeltaFinanceApplication {

  public static void main(String[] args) {
    SpringApplication.run(DeltaFinanceApplication.class, args);
  }
}
