package com.deltafinance.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.deltafinance.support.PostgresTestBase;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.MigrationInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Garante que o schema é aplicável em banco vazio, idempotente, restaurável a partir de snapshot da
 * versão anterior e que uma migração quebrada impede a aplicação de iniciar (portanto, o readiness
 * nunca fica verde). A baseline consolidada também não pode reintroduzir objetos legados.
 */
class MigrationIT extends PostgresTestBase {

  @Test
  void aplicaTodasAsMigracoesEmBancoVazio() {
    cleanDatabase();

    migrate();

    assertThat(currentVersion()).isEqualTo(ultimaVersao());
    assertThat(tableExists("app_user")).isTrue();
    assertThat(tableExists("login_attempt")).isTrue();
    assertThat(tableExists("audit_event")).isTrue();
    assertThat(tableExists("idempotency_key")).isTrue();
    assertThat(tableExists("spring_session")).isTrue();
    assertThat(tableExists("spring_session_attributes")).isTrue();
  }

  @Test
  void baselineNaoContemObjetosLegados() {
    cleanDatabase();

    migrateTo("001");

    // Garante que o teste não passe por vacuidade caso a baseline não seja aplicada.
    assertThat(currentVersion()).isEqualTo("001");
    assertThat(tableExists("app_user")).isTrue();
    assertThat(tableExists("invoice_payment")).isTrue();

    assertThat(tableExists("webhook_event")).isFalse();
    assertThat(tableExists("webhook_subscription")).isFalse();
    assertThat(tableExists("wealth_snapshot")).isFalse();
    assertThat(tableExists("wealth_snapshot_item")).isFalse();
    assertThat(columnExists("financial_goal", "initial_value_enc")).isFalse();
    assertThat(columnExists("invoice", "provider_bill_id_enc")).isFalse();
    assertThat(columnExists("invoice", "provider_bill_id_hash")).isFalse();
    assertThat(columnExists("invoice_item", "provider_item_id_enc")).isFalse();
    assertThat(columnExists("invoice_item", "provider_item_id_hash")).isFalse();
    assertThat(columnExists("invoice_payment", "provider_payment_id_enc")).isFalse();
    assertThat(columnExists("invoice_payment", "provider_payment_id_hash")).isFalse();
    assertThat(columnExists("idempotency_key", "response_resource_type")).isFalse();
    assertThat(columnExists("idempotency_key", "response_resource_id")).isFalse();
    assertThat(columnExists("idempotency_key", "response_body_hash")).isFalse();

    assertThat(constraintDefinition("invoice", "invoice_source_ck"))
        .contains("LOCAL")
        .doesNotContain("PROVIDER");
    assertThat(constraintDefinition("invoice_item", "invoice_item_source_ck"))
        .doesNotContain("PROVIDER_BILL");
    assertThat(constraintDefinition("invoice_payment", "invoice_payment_source_ck"))
        .doesNotContain("PROVIDER_BILL");
    assertThat(constraintDefinition("financial_goal", "financial_goal_type_ck"))
        .doesNotContain("DEBT_REDUCTION");
    assertThat(constraintDefinition("financial_goal", "financial_goal_rule_ck"))
        .doesNotContain("DEBT_REDUCTION");
    assertThat(constraintDefinition("goal_allocation", "goal_allocation_source_ck"))
        .doesNotContain("MANUAL_LIABILITY");
    assertThat(constraintDefinition("sync_job", "sync_job_type_ck"))
        .doesNotContain("PROCESS_WEBHOOK");
  }

  private String constraintDefinition(String table, String constraint) {
    return jdbcTemplate.queryForObject(
        """
        select pg_get_constraintdef(oid)
          from pg_constraint
         where conrelid = ('public.' || ?)::regclass and conname = ?
        """,
        String.class,
        table,
        constraint);
  }

  private boolean columnExists(String table, String column) {
    return Boolean.TRUE.equals(
        jdbcTemplate.queryForObject(
            """
            select exists (
              select 1 from information_schema.columns
               where table_schema = 'public' and table_name = ? and column_name = ?
            )
            """,
            Boolean.class,
            table,
            column));
  }

  @Test
  void reaplicarMigracoesNaoDeixaPendencias() {
    cleanDatabase();
    migrate();

    migrate();

    assertThat(flyway().info().pending()).isEmpty();
    assertThat(currentVersion()).isEqualTo(ultimaVersao());
  }

  @Test
  void aceitaMigracaoSobreSnapshotDaVersaoAnterior() {
    List<String> versions = versoesAplicaveis();
    List<String> casos = versions.stream().filter(version -> numero(version) >= 2).toList();
    org.junit.jupiter.api.Assumptions.assumeFalse(
        casos.isEmpty(),
        "Ainda não há migração >= V002; o cenário snapshot+restore será exercitado nas próximas fases.");

    for (String version : casos) {
      String anterior = versions.get(versions.indexOf(version) - 1);
      cleanDatabase();
      migrateTo(anterior);
      snapshot("v" + anterior);

      cleanDatabase();
      restore("v" + anterior);
      assertThat(currentVersion()).isEqualTo(anterior);

      migrateTo(version);
      assertThat(currentVersion()).isEqualTo(version);
    }
  }

  @Test
  void migracaoQuebradaFalhaAntesDeAplicacaoFicarPronta(@TempDir Path tempDir) throws IOException {
    cleanDatabase();
    Files.writeString(
        tempDir.resolve("V999__broken.sql"),
        "create table broken_schema (id uuid primary key;" // sintaxe inválida de propósito
        );

    Flyway brokenFlyway =
        Flyway.configure()
            .dataSource(postgres().getJdbcUrl(), postgres().getUsername(), postgres().getPassword())
            .locations("classpath:db/migration", "filesystem:" + tempDir.toAbsolutePath())
            .cleanDisabled(false)
            .load();

    // O Spring Boot executa Flyway antes de declarar readiness; uma falha aqui interrompe o start.
    assertThatThrownBy(brokenFlyway::migrate).isInstanceOf(FlywayException.class);
    assertThat(tableExists("broken_schema")).isFalse();
  }

  private String ultimaVersao() {
    return versoesAplicaveis().getLast();
  }

  private List<String> versoesAplicaveis() {
    return java.util.Arrays.stream(flyway().info().all())
        .filter(info -> info.getVersion() != null)
        .map(MigrationInfo::getVersion)
        .filter(Objects::nonNull)
        .map(version -> version.getVersion())
        .sorted(Comparator.comparingInt(MigrationIT::numero))
        .toList();
  }

  private static int numero(String version) {
    return Integer.parseInt(version.replaceFirst("^0+(?!$)", ""));
  }
}
