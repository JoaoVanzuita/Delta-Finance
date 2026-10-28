package com.deltafinance.support;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.Container.ExecResult;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Base para testes de integração que exigem um PostgreSQL real.
 *
 * <p>Sobe um {@code postgres:18.6-alpine} via Testcontainers, aponta o datasource do Spring para
 * ele e oferece utilitários para validar migrações Flyway:
 *
 * <ul>
 *   <li>{@code flyway.target} (system property ou variável de ambiente) limita a versão aplicada;
 *   <li>{@link #snapshot(String)} e {@link #restore(String)} permitem voltar o banco ao estado de
 *       uma versão anterior, cenário exigido pelo {@code MigrationIT} (T039);
 *   <li>{@link #migrateTo(String)} aplica migrações até uma versão específica.
 * </ul>
 *
 * <p>Se o Docker não estiver disponível, os testes que estendem esta classe são ignorados em vez de
 * falhar. Nunca use dados reais aqui: as fixtures são sintéticas (T038).
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@ActiveProfiles("test")
public abstract class PostgresTestBase {

  /** Imagem fixada: migrations e testes devem rodar na mesma versão do Compose. */
  private static final DockerImageName POSTGRES_IMAGE =
      DockerImageName.parse("postgres:18.6-alpine");

  private static final String DATABASE_NAME = "delta_finance";
  private static final String DATABASE_USER = "delta";
  private static final String DATABASE_PASSWORD = "delta";
  private static final String SNAPSHOT_DIRECTORY = "/tmp/delta-snapshots";

  // Compartilhado por toda a JVM de testes: reiniciar o container entre classes mudaria a porta
  // mapeada e invalidaria o ApplicationContext em cache. O Ryuk o remove ao final da JVM.
  protected static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer(POSTGRES_IMAGE)
          .withDatabaseName(DATABASE_NAME)
          .withUsername(DATABASE_USER)
          .withPassword(DATABASE_PASSWORD);

  @Autowired protected JdbcTemplate jdbcTemplate;

  @DynamicPropertySource
  static void registerDatasourceProperties(DynamicPropertyRegistry registry) {
    ensureContainerStarted();
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
    registry.add("spring.datasource.driver-class-name", POSTGRES::getDriverClassName);
    registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    registry.add("spring.flyway.enabled", () -> "true");
    registry.add("spring.flyway.clean-disabled", () -> "false");
    registry.add("spring.session.jdbc.initialize-schema", () -> "never");
    registry.add("delta.crypto.key-file", () -> testResourcePath("crypto-keys-test.json"));

    String target = flywayTarget();
    if (target != null) {
      registry.add("spring.flyway.target", () -> target);
    }
  }

  /** Versão-alvo opcional, configurável por {@code -Dflyway.target} ou {@code FLYWAY_TARGET}. */
  public static String flywayTarget() {
    String target = System.getProperty("flyway.target");
    if (target == null || target.isBlank()) {
      target = System.getenv("FLYWAY_TARGET");
    }
    return target == null || target.isBlank() ? null : target.trim();
  }

  /** Caminho absoluto de um recurso classpath usado por propriedades do Spring. */
  protected static String testResourcePath(String name) {
    var resource = PostgresTestBase.class.getResource("/" + name);
    if (resource == null) {
      throw new IllegalStateException("Recurso de teste não encontrado: " + name);
    }
    try {
      return Path.of(resource.toURI()).toString();
    } catch (URISyntaxException exception) {
      throw new IllegalStateException("URI de recurso inválida: " + name, exception);
    }
  }

  protected static PostgreSQLContainer postgres() {
    ensureContainerStarted();
    return POSTGRES;
  }

  /** Flyway programático com {@code clean} habilitado e o alvo configurado, se houver. */
  protected Flyway flyway() {
    return configureFlyway(flywayTarget());
  }

  /** Aplica todas as migrações disponíveis (até a versão mais recente). */
  protected void migrate() {
    flyway().migrate();
  }

  /** Aplica migrações até {@code version} (ex.: {@code "001"}). */
  protected void migrateTo(String version) {
    configureFlyway(version).migrate();
  }

  /** Apaga todos os objetos do schema e o histórico Flyway. */
  protected void cleanDatabase() {
    flyway().clean();
  }

  /**
   * Salva um dump lógico do banco atual dentro do container.
   *
   * <p>Usado pelo {@code MigrationIT} para simular um banco já existente em uma versão anterior
   * antes de aplicar as próximas migrações.
   */
  protected void snapshot(String name) {
    requireValidSnapshotName(name);
    exec(
        "sh",
        "-c",
        "mkdir -p "
            + SNAPSHOT_DIRECTORY
            + " && pg_dump -U "
            + DATABASE_USER
            + " -d "
            + DATABASE_NAME
            + " -Fc -f "
            + SNAPSHOT_DIRECTORY
            + "/"
            + name
            + ".dump");
  }

  /** Restaura o dump criado por {@link #snapshot(String)}, substituindo o estado atual. */
  protected void restore(String name) {
    requireValidSnapshotName(name);
    exec(
        "pg_restore",
        "-U",
        DATABASE_USER,
        "-d",
        DATABASE_NAME,
        "--clean",
        "--if-exists",
        "--no-owner",
        SNAPSHOT_DIRECTORY + "/" + name + ".dump");
  }

  /**
   * Limpa dados de identidade/sessão entre testes.
   *
   * <p>Mantém o schema intacto (migrações aplicadas) e respeita a ordem das FKs.
   */
  protected void cleanIdentityData() {
    jdbcTemplate.execute("delete from goal_progress_snapshot");
    jdbcTemplate.execute("delete from goal_allocation");
    jdbcTemplate.execute("delete from financial_goal");
    jdbcTemplate.execute("delete from manual_asset_liability");
    jdbcTemplate.execute("delete from investment_income");
    jdbcTemplate.execute("delete from provider_investment_transaction");
    jdbcTemplate.execute("delete from provider_investment");
    jdbcTemplate.execute("delete from provider_investment_sync");
    jdbcTemplate.execute("delete from investment_position");
    jdbcTemplate.execute("delete from sync_run");
    jdbcTemplate.execute("delete from sync_job");
    jdbcTemplate.execute("delete from invoice_item_correction");
    jdbcTemplate.execute("delete from invoice_payment");
    jdbcTemplate.execute("delete from invoice_item");
    jdbcTemplate.execute("delete from invoice");
    jdbcTemplate.execute("delete from internal_match");
    jdbcTemplate.execute("delete from transaction_correction");
    jdbcTemplate.execute("delete from external_identity_history");
    jdbcTemplate.execute("delete from transaction");
    jdbcTemplate.execute("delete from card_profile");
    jdbcTemplate.execute("delete from financial_account");
    jdbcTemplate.execute("delete from category");
    jdbcTemplate.execute("delete from pluggy_connection");
    jdbcTemplate.execute("delete from pluggy_integration");
    jdbcTemplate.execute("delete from spring_session_attributes");
    jdbcTemplate.execute("delete from spring_session");
    jdbcTemplate.execute("delete from idempotency_key");
    jdbcTemplate.execute("delete from audit_event");
    jdbcTemplate.execute("delete from login_attempt");
    jdbcTemplate.execute("delete from app_user");
  }

  /** Indica se uma tabela existe no schema {@code public}. */
  protected boolean tableExists(String table) {
    Boolean exists =
        jdbcTemplate.queryForObject(
            "select exists (select 1 from information_schema.tables"
                + " where table_schema = 'public' and table_name = ?)",
            Boolean.class,
            table);
    return Boolean.TRUE.equals(exists);
  }

  /** Versão atual do schema (ou {@code null} se nenhuma migração foi aplicada). */
  protected String currentVersion() {
    MigrationVersion version = flyway().info().current().getVersion();
    return version == null ? null : version.getVersion();
  }

  private Flyway configureFlyway(String target) {
    ensureContainerStarted();
    var configuration =
        Flyway.configure()
            .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
            .locations("classpath:db/migration")
            .cleanDisabled(false);
    if (target != null) {
      configuration.target(MigrationVersion.fromVersion(target));
    }
    return configuration.load();
  }

  private static void ensureContainerStarted() {
    if (!POSTGRES.isRunning()) {
      POSTGRES.start();
    }
  }

  private static void requireValidSnapshotName(String name) {
    if (name == null || !name.matches("[a-zA-Z0-9_-]{1,64}")) {
      throw new IllegalArgumentException("Nome de snapshot inválido: " + name);
    }
  }

  private void exec(String... command) {
    try {
      ExecResult result = postgres().execInContainer(command);
      if (result.getExitCode() != 0) {
        throw new IllegalStateException(
            "Comando falhou ("
                + result.getExitCode()
                + "): "
                + String.join(" ", List.of(command))
                + "\n"
                + result.getStderr());
      }
    } catch (IOException exception) {
      throw new UncheckedIOException("Falha ao executar comando no container", exception);
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Execução interrompida no container", exception);
    }
  }

  /** Exposto para diagnósticos de falha de conexão em CI. */
  protected String datasourceDescription() {
    return "jdbc:postgresql://"
        + POSTGRES.getHost()
        + ":"
        + POSTGRES.getMappedPort(PostgreSQLContainer.POSTGRESQL_PORT)
        + "/"
        + DATABASE_NAME
        + " (user="
        + DATABASE_USER
        + ")";
  }

  /** Garante que o container aceita conexões antes de um teste que dependa do banco. */
  protected void awaitDatabase() {
    ensureContainerStarted();
    Integer probe = jdbcTemplate.queryForObject("select 1", Integer.class);
    if (probe == null || probe != 1) {
      throw new IllegalStateException(
          "PostgreSQL de teste não respondeu ao probe: " + datasourceDescription());
    }
  }
}
