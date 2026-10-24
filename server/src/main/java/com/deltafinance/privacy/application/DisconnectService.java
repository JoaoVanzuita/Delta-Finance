package com.deltafinance.privacy.application;

import com.deltafinance.connections.application.SyncJobService;
import com.deltafinance.integrations.application.IntegrationService;
import com.deltafinance.integrations.application.IntegrationView;
import com.deltafinance.shared.audit.AuditEventType;
import com.deltafinance.shared.audit.AuditService;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Desconexão do provedor bancário.
 *
 * <p>Por padrão as credenciais são removidas, as sincronizações são interrompidas e todo o
 * histórico financeiro é preservado. A exclusão dos dados importados é uma escolha explícita do
 * usuário e nunca apaga contas manuais, lançamentos nessas contas ou categorias.
 */
@Service
public class DisconnectService {

  private final IntegrationService integrationService;
  private final SyncJobService syncJobService;
  private final JdbcTemplate jdbcTemplate;
  private final AuditService auditService;

  public DisconnectService(
      IntegrationService integrationService,
      SyncJobService syncJobService,
      JdbcTemplate jdbcTemplate,
      AuditService auditService) {
    this.integrationService = integrationService;
    this.syncJobService = syncJobService;
    this.jdbcTemplate = jdbcTemplate;
    this.auditService = auditService;
  }

  @Transactional
  public void disconnect(UUID userId, boolean deleteImportedData) {
    UUID integrationId = integrationService.findView(userId).map(IntegrationView::id).orElse(null);
    integrationService.disconnect(userId);
    if (integrationId != null) {
      syncJobService.cancelActiveForIntegration(userId, integrationId);
    }

    int deletedAccounts = 0;
    if (deleteImportedData) {
      deletedAccounts =
          jdbcTemplate.update(
              "delete from financial_account where user_id = ? and origin = 'IMPORTED'", userId);
    }

    auditService.record(
        userId,
        AuditEventType.DISCONNECTED,
        "pluggy_integration",
        integrationId,
        Map.of(
            "deleteImportedData",
            deleteImportedData,
            "deletedAccounts",
            Math.max(0, deletedAccounts)));
  }
}
