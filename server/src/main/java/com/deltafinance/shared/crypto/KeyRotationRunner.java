package com.deltafinance.shared.crypto;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

/**
 * Execução operacional única da rotação de chave (T176).
 *
 * <p>Só é ativada com {@code --delta.crypto.rotation.run=true}; após a rotação o processo encerra
 * com código 0 (sucesso) ou 1 (falhas). Nunca é agendada automaticamente, porque a rotação exige a
 * nova versão ativa no key ring e janela operacional controlada.
 */
@Component
@ConditionalOnProperty(prefix = "delta.crypto.rotation", name = "run", havingValue = "true")
public class KeyRotationRunner implements ApplicationRunner {

  private final KeyRotationJob job;
  private final ConfigurableApplicationContext context;

  public KeyRotationRunner(KeyRotationJob job, ConfigurableApplicationContext context) {
    this.job = job;
    this.context = context;
  }

  @Override
  public void run(ApplicationArguments args) {
    KeyRotationJob.RotationResult result = job.rotate();
    System.out.printf(
        "Rotação concluída: targetVersion=%d rotated=%d failed=%d%n",
        result.targetVersion(), result.rotated(), result.failed());
    int exitCode = result.failed() == 0 ? 0 : 1;
    System.exit(SpringApplication.exit(context, () -> exitCode));
  }
}
