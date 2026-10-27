package com.deltafinance.privacy.application;

import com.deltafinance.auth.application.AuthService;
import com.deltafinance.connections.application.SyncJobService;
import com.deltafinance.integrations.application.IntegrationService;
import com.deltafinance.privacy.api.DeleteAccountRequest;
import com.deltafinance.shared.audit.AuditEventType;
import com.deltafinance.shared.audit.AuditService;
import com.deltafinance.shared.crypto.IndexHmacService;
import com.deltafinance.shared.errors.ValidationException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Exclusão da conta com tombstone de identidade.
 *
 * <p>Fluxo destrutivo e síncrono: confirma a senha e a palavra {@code EXCLUIR}, revoga sessões,
 * cancela jobs de sincronização, remove credenciais e todos os dados financeiros/operacionais do
 * titular, e mantém apenas o tombstone em {@code app_user} com status {@code DELETED}. Os eventos
 * de auditoria permanecem sanitizados pelo prazo documentado. Outros usuários não são afetados.
 */
@Service
public class AccountDeletionService {

  private final AuthService authService;
  private final IntegrationService integrationService;
  private final SyncJobService syncJobService;
  private final IndexHmacService hmacService;
  private final JdbcTemplate jdbcTemplate;
  private final AuditService auditService;

  @PersistenceContext private EntityManager entityManager;

  public AccountDeletionService(
      AuthService authService,
      IntegrationService integrationService,
      SyncJobService syncJobService,
      IndexHmacService hmacService,
      JdbcTemplate jdbcTemplate,
      AuditService auditService) {
    this.authService = authService;
    this.integrationService = integrationService;
    this.syncJobService = syncJobService;
    this.hmacService = hmacService;
    this.jdbcTemplate = jdbcTemplate;
    this.auditService = auditService;
  }

  @Transactional
  public DeletionResult delete(UUID userId, DeleteAccountRequest request) {
    if (!"EXCLUIR".equals(request.confirmation() == null ? null : request.confirmation().trim())) {
      throw new ValidationException(
          "confirmation", "Digite EXCLUIR (em maiúsculas) para confirmar a exclusão.");
    }
    String usernameNormalized =
        authService.verifyPasswordForDestructiveAction(userId, request.password());

    authService.requestDeletion(userId);
    syncJobService.cancelActiveForUser(userId);
    // Invalida cache de API Key e marca conexões como indisponíveis antes de apagar os dados.
    integrationService.disconnect(userId);

    // As alterações JPA acima são persistidas antes das remoções em lote via JDBC; limpar o
    // contexto evita que um flush tardio tente atualizar linhas já removidas.
    entityManager.flush();
    entityManager.clear();

    int deletedRecords = deleteActiveData(userId, usernameNormalized);
    authService.revokeAllSessions(userId);
    authService.completeDeletion(userId);

    auditService.record(
        userId,
        AuditEventType.ACCOUNT_DELETED,
        "app_user",
        userId,
        Map.of(
            "deletedRecords", deletedRecords,
            "backupRetentionDays", 30,
            "tombstoneRetentionDays", 365));
    return new DeletionResult(deletedRecords);
  }

  private int deleteActiveData(UUID userId, String usernameNormalized) {
    int deleted = 0;
    // Módulos secundários não são alcançados pelo tombstone de app_user; apague os filhos antes
    // dos pais, sempre com escopo no titular.
    deleted += jdbcTemplate.update("delete from goal_progress_snapshot where user_id = ?", userId);
    deleted += jdbcTemplate.update("delete from goal_allocation where user_id = ?", userId);
    deleted += jdbcTemplate.update("delete from financial_goal where user_id = ?", userId);
    deleted += jdbcTemplate.update("delete from investment_position where user_id = ?", userId);
    deleted += jdbcTemplate.update("delete from manual_asset_liability where user_id = ?", userId);
    // A remoção das contas dispara o cascade de transações, faturas, itens, pagamentos,
    // correções, cartões, histórico de identidade externa e movimentos internos.
    deleted += jdbcTemplate.update("delete from financial_account where user_id = ?", userId);
    deleted += jdbcTemplate.update("delete from category where user_id = ?", userId);
    deleted += jdbcTemplate.update("delete from sync_job where user_id = ?", userId);
    deleted += jdbcTemplate.update("delete from sync_run where user_id = ?", userId);
    deleted += jdbcTemplate.update("delete from pluggy_connection where user_id = ?", userId);
    deleted += jdbcTemplate.update("delete from pluggy_integration where user_id = ?", userId);
    deleted += jdbcTemplate.update("delete from idempotency_key where user_id = ?", userId);
    deleted +=
        jdbcTemplate.update(
            "delete from login_attempt where username_hash = ?",
            hmacService.usernameHash(usernameNormalized));
    return deleted;
  }

  /** Contagem sanitizada para resposta/auditoria; nunca inclui valores financeiros. */
  public record DeletionResult(int deletedRecords) {}
}
