package com.deltafinance.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/**
 * Valida a infraestrutura de testes de integração que será reutilizada pelas histórias.
 *
 * <p>Não testa regra de negócio: garante que o container PostgreSQL, o Flyway programático e o
 * mecanismo de snapshot/restore realmente funcionam antes de sustentar o {@code MigrationIT}
 * (T039).
 */
class PostgresTestBaseIT extends PostgresTestBase {

  @Test
  void conectaAoPostgresReal() {
    awaitDatabase();
    Integer result = jdbcTemplate.queryForObject("select 1", Integer.class);
    assertThat(result).isEqualTo(1);
  }

  @Test
  void restauraSnapshotAnterior() {
    jdbcTemplate.execute("drop table if exists smoke_probe");
    jdbcTemplate.execute("create table smoke_probe (id integer primary key, note text not null)");
    jdbcTemplate.update("insert into smoke_probe (id, note) values (?, ?)", 1, "antes do snapshot");

    snapshot("smoke");

    jdbcTemplate.update(
        "insert into smoke_probe (id, note) values (?, ?)", 2, "depois do snapshot");
    assertThat(countProbeRows()).isEqualTo(2);

    restore("smoke");

    assertThat(countProbeRows()).isEqualTo(1);
    assertThat(
            jdbcTemplate.queryForObject("select note from smoke_probe where id = 1", String.class))
        .isEqualTo("antes do snapshot");
  }

  @Test
  void rejeitaAlvoDeMigracaoInexistente() {
    // Prova que `flyway.target` chega à configuração do Flyway: um alvo sem migração correspondente
    // falha em vez de ser ignorado silenciosamente.
    assertThatThrownBy(() -> migrateTo("999"))
        .isInstanceOf(org.flywaydb.core.api.FlywayException.class)
        .hasMessageContaining("999");
  }

  private int countProbeRows() {
    Integer count = jdbcTemplate.queryForObject("select count(*) from smoke_probe", Integer.class);
    return count == null ? 0 : count;
  }
}
