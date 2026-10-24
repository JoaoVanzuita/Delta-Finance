package com.deltafinance.auth.application;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

/**
 * Execução operacional da recuperação assistida de senha (T197).
 *
 * <p>Ativada apenas quando {@code delta.operator.reset.target} está definido, o que só acontece no
 * comando explícito de {@code deploy/scripts/reset-password.sh}. O processo não atende HTTP ({@code
 * --spring.main.web-application-type=none}) e encerra em seguida.
 *
 * <p>A senha temporária é escrita uma única vez em stdout para o operador; não vai para log
 * estruturado, auditoria, banco ou arquivo.
 */
@Component
@ConditionalOnProperty(prefix = "delta.operator.reset", name = "target")
public class AssistedPasswordResetRunner implements ApplicationRunner {

  private final AssistedPasswordResetService service;
  private final ConfigurableApplicationContext context;
  private final String target;
  private final String token;
  private final String operator;

  public AssistedPasswordResetRunner(
      AssistedPasswordResetService service,
      ConfigurableApplicationContext context,
      @Value("${delta.operator.reset.target}") String target,
      @Value("${delta.operator.reset.token:}") String token,
      @Value("${delta.operator.reset.operator:operator}") String operator) {
    this.service = service;
    this.context = context;
    this.target = target;
    this.token = token;
    this.operator = operator;
  }

  @Override
  public void run(ApplicationArguments args) {
    int exitCode = 0;
    try {
      AssistedPasswordResetService.ResetResult result = service.reset(target, token, operator);
      System.out.println("Recuperação assistida concluída.");
      System.out.printf(
          "usuario=%s sessoesRevogadas=%d%n", result.username(), result.sessionsRevoked());
      System.out.printf("senhaTemporaria=%s%n", result.temporaryPassword());
      System.out.println(
          "Entregue a senha por canal seguro e oriente a troca imediata. Ela não será exibida novamente.");
    } catch (RuntimeException exception) {
      System.err.printf("Recuperação assistida falhou: %s%n", exception.getClass().getSimpleName());
      exitCode = 1;
    }
    int finalExitCode = exitCode;
    System.exit(SpringApplication.exit(context, () -> finalExitCode));
  }
}
