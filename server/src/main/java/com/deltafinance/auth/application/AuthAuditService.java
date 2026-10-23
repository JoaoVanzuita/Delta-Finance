package com.deltafinance.auth.application;

import com.deltafinance.shared.audit.AuditEventType;
import com.deltafinance.shared.audit.AuditService;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Eventos de auditoria de identidade.
 *
 * <p>Centraliza os tipos e metadados permitidos: apenas IDs, códigos e contadores. Nunca recebe
 * senha, hash, token, IP bruto ou corpo de requisição.
 */
@Service
public class AuthAuditService {

  private static final String TARGET_TYPE = "app_user";

  private final AuditService auditService;

  public AuthAuditService(AuditService auditService) {
    this.auditService = auditService;
  }

  public void userRegistered(UUID userId) {
    auditService.record(userId, AuditEventType.USER_REGISTERED, TARGET_TYPE, userId);
  }

  public void loginSucceeded(UUID userId) {
    auditService.record(userId, AuditEventType.LOGIN_SUCCESS, TARGET_TYPE, userId);
  }

  /** {@code userId} é nulo quando o nome não corresponde a nenhuma conta. */
  public void loginFailed(UUID userId) {
    auditService.record(
        userId,
        AuditEventType.LOGIN_FAILURE,
        TARGET_TYPE,
        userId,
        Map.of("reason", "BAD_CREDENTIALS"));
  }

  public void loginBlocked(UUID userId) {
    auditService.record(
        userId,
        AuditEventType.LOGIN_BLOCKED,
        TARGET_TYPE,
        userId,
        Map.of("reason", "RATE_LIMITED"));
  }

  public void logout(UUID userId) {
    auditService.record(userId, AuditEventType.LOGOUT, TARGET_TYPE, userId);
  }

  public void passwordChanged(UUID userId) {
    auditService.record(userId, AuditEventType.PASSWORD_CHANGED, TARGET_TYPE, userId);
  }
}
