package com.deltafinance.connections.application;

import com.deltafinance.connections.domain.ConnectionStatus;
import com.deltafinance.connections.domain.PluggyConnection;
import com.deltafinance.connections.domain.SyncRunStatus;
import com.deltafinance.integrations.domain.IntegrationStatus;
import com.deltafinance.integrations.domain.PluggyIntegration;
import com.deltafinance.integrations.infrastructure.PluggyCredentialException;
import com.deltafinance.integrations.infrastructure.PluggyException;
import com.deltafinance.integrations.infrastructure.PluggyItemInvalidException;
import com.deltafinance.integrations.infrastructure.PluggyModels;
import com.deltafinance.integrations.infrastructure.PluggyRateLimitedException;
import com.deltafinance.integrations.infrastructure.PluggyUnauthorizedException;
import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import org.springframework.stereotype.Service;

/**
 * Máquina de estados de sincronização e mapeamento sanitizado de erros.
 *
 * <p>Distingue três situações que exigem ações diferentes do usuário:
 *
 * <ul>
 *   <li>credenciais da aplicação pessoal recusadas ({@code CREDENTIAL_FAILURE});
 *   <li>autorização anual da aplicação expirada ({@code USER_AUTHORIZATION_EXPIRED});
 *   <li>consentimento bancário individual expirado ({@code CONSENT_EXPIRED}).
 * </ul>
 *
 * <p>Falhas temporárias mantêm o último estado conhecido e apenas registram o código, para que os
 * dados importados continuem disponíveis.
 */
@Service
public class SyncStateService {

  public static final String USER_AUTHORIZATION_EXPIRED = "USER_AUTHORIZATION_EXPIRED";
  public static final String CONSENT_EXPIRED = "CONSENT_EXPIRED";
  public static final String LOGIN_ERROR = "LOGIN_ERROR";
  public static final String CREDENTIAL_FAILURE = "CREDENTIAL_FAILURE";
  public static final String RATE_LIMITED = "RATE_LIMITED";
  public static final String TIMEOUT = "TIMEOUT";
  public static final String PROVIDER_UNAVAILABLE = "PROVIDER_UNAVAILABLE";
  public static final String ITEM_UNAVAILABLE = "ITEM_UNAVAILABLE";
  public static final String IMPORT_FAILED = "IMPORT_FAILED";

  /** Decisão de estado para uma falha ou estado de item. */
  public record SyncFailure(
      SyncRunStatus runStatus,
      String errorCode,
      boolean actionRequired,
      boolean retryable,
      Duration retryAfter,
      IntegrationStatus integrationStatus,
      ConnectionStatus connectionStatus) {}

  /** Avalia o estado do item retornado pelo provedor antes de importar dados. */
  public SyncFailure fromItem(PluggyModels.PluggyItem item, Instant now) {
    if (item == null) {
      return null;
    }
    String code = item.errorCode() == null ? null : item.errorCode().toUpperCase(Locale.ROOT);
    if (USER_AUTHORIZATION_EXPIRED.equals(code)) {
      return actionRequired(
          SyncRunStatus.AUTH_ERROR,
          USER_AUTHORIZATION_EXPIRED,
          IntegrationStatus.AUTHORIZATION_EXPIRED,
          ConnectionStatus.AUTHORIZATION_EXPIRED);
    }
    if (LOGIN_ERROR.equals(code)) {
      return actionRequired(
          SyncRunStatus.AUTH_ERROR,
          LOGIN_ERROR,
          IntegrationStatus.ACTION_REQUIRED,
          ConnectionStatus.ACTION_REQUIRED);
    }
    if (item.consentExpiresAt() != null && !item.consentExpiresAt().isAfter(now)) {
      return actionRequired(
          SyncRunStatus.AUTH_ERROR,
          CONSENT_EXPIRED,
          IntegrationStatus.AUTHORIZATION_EXPIRED,
          ConnectionStatus.AUTHORIZATION_EXPIRED);
    }
    String status = item.status() == null ? "" : item.status().toUpperCase(Locale.ROOT);
    if ("LOGIN_ERROR".equals(status) || "WAITING_USER_INPUT".equals(status)) {
      return actionRequired(
          SyncRunStatus.AUTH_ERROR,
          "LOGIN_ERROR",
          IntegrationStatus.ACTION_REQUIRED,
          ConnectionStatus.ACTION_REQUIRED);
    }
    if ("UPDATE_ERROR".equals(status) || "OUTDATED".equals(status)) {
      return actionRequired(
          SyncRunStatus.FAILED,
          "ITEM_UPDATE_ERROR",
          IntegrationStatus.ACTION_REQUIRED,
          ConnectionStatus.ACTION_REQUIRED);
    }
    return null;
  }

  /** Converte uma exceção do provedor em decisão de estado e política de retry. */
  public SyncFailure fromException(Throwable throwable) {
    if (throwable instanceof PluggyRateLimitedException rateLimited) {
      return new SyncFailure(
          SyncRunStatus.RATE_LIMITED,
          RATE_LIMITED,
          false,
          true,
          rateLimited.retryAfter(),
          null,
          null);
    }
    if (throwable instanceof PluggyCredentialException) {
      return actionRequired(
          SyncRunStatus.AUTH_ERROR,
          CREDENTIAL_FAILURE,
          IntegrationStatus.CREDENTIAL_FAILURE,
          ConnectionStatus.CREDENTIAL_FAILURE);
    }
    if (throwable instanceof PluggyUnauthorizedException) {
      return actionRequired(
          SyncRunStatus.AUTH_ERROR,
          USER_AUTHORIZATION_EXPIRED,
          IntegrationStatus.AUTHORIZATION_EXPIRED,
          ConnectionStatus.AUTHORIZATION_EXPIRED);
    }
    if (isTimeout(throwable)) {
      return new SyncFailure(SyncRunStatus.TIMEOUT, TIMEOUT, false, true, null, null, null);
    }
    if (throwable instanceof PluggyItemInvalidException) {
      // itemId inexistente/removido na origem: exige ação do usuário e não deve
      // ser retentado automaticamente.
      return actionRequired(
          SyncRunStatus.FAILED,
          ITEM_UNAVAILABLE,
          IntegrationStatus.ITEM_UNAVAILABLE,
          ConnectionStatus.ITEM_UNAVAILABLE);
    }
    if (throwable instanceof PluggyException) {
      return new SyncFailure(
          SyncRunStatus.FAILED, PROVIDER_UNAVAILABLE, false, true, null, null, null);
    }
    return new SyncFailure(SyncRunStatus.FAILED, IMPORT_FAILED, false, true, null, null, null);
  }

  /** Aplica a decisão às entidades, preservando o último estado em falhas temporárias. */
  public void apply(
      PluggyIntegration integration,
      PluggyConnection connection,
      SyncFailure failure,
      Instant now) {
    if (failure == null) {
      return;
    }
    if (integration != null) {
      if (failure.integrationStatus() != null) {
        integration.recordError(failure.errorCode(), failure.integrationStatus());
      } else {
        integration.recordTransientError(failure.errorCode());
      }
    }
    if (connection != null) {
      if (failure.connectionStatus() != null) {
        connection.recordError(failure.errorCode(), failure.connectionStatus());
      } else {
        connection.recordTransientError(failure.errorCode(), now);
      }
    }
  }

  private static SyncFailure actionRequired(
      SyncRunStatus runStatus,
      String errorCode,
      IntegrationStatus integrationStatus,
      ConnectionStatus connectionStatus) {
    return new SyncFailure(
        runStatus, errorCode, true, false, null, integrationStatus, connectionStatus);
  }

  private static boolean isTimeout(Throwable throwable) {
    Throwable current = throwable;
    for (int depth = 0; current != null && depth < 8; depth++) {
      if (current instanceof SocketTimeoutException
          || current instanceof HttpTimeoutException
          || current.getClass().getSimpleName().toLowerCase(Locale.ROOT).contains("timeout")) {
        return true;
      }
      current = current.getCause();
    }
    return false;
  }
}
