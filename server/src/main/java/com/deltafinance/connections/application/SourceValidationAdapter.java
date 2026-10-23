package com.deltafinance.connections.application;

import com.deltafinance.accounts.domain.FinancialAccount;
import com.deltafinance.accounts.domain.FinancialAccountRepository;
import com.deltafinance.connections.domain.PluggyConnection;
import com.deltafinance.connections.domain.PluggyConnectionRepository;
import com.deltafinance.integrations.domain.PluggyIntegration;
import com.deltafinance.integrations.domain.PluggyIntegrationRepository;
import com.deltafinance.integrations.infrastructure.PluggyAuthClient;
import com.deltafinance.integrations.infrastructure.PluggyDataClient;
import com.deltafinance.integrations.infrastructure.PluggyModels;
import com.deltafinance.integrations.infrastructure.PluggyUnauthorizedException;
import com.deltafinance.shared.crypto.CryptoContext;
import com.deltafinance.shared.crypto.CryptoService;
import com.deltafinance.shared.time.Clock;
import com.deltafinance.transactions.application.SourceValidationPort;
import com.deltafinance.transactions.domain.TransactionStatus;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Valida no provedor se uma transação removida voltou a existir. */
@Component
public class SourceValidationAdapter implements SourceValidationPort {

  private static final String ACCOUNT_TABLE = "financial_account";
  private static final int LOOKBACK_MONTHS = 12;

  private final FinancialAccountRepository accountRepository;
  private final PluggyConnectionRepository connectionRepository;
  private final PluggyIntegrationRepository integrationRepository;
  private final PluggyAuthClient authClient;
  private final PluggyDataClient dataClient;
  private final CryptoService cryptoService;
  private final Clock clock;

  public SourceValidationAdapter(
      FinancialAccountRepository accountRepository,
      PluggyConnectionRepository connectionRepository,
      PluggyIntegrationRepository integrationRepository,
      PluggyAuthClient authClient,
      PluggyDataClient dataClient,
      CryptoService cryptoService,
      Clock clock) {
    this.accountRepository = accountRepository;
    this.connectionRepository = connectionRepository;
    this.integrationRepository = integrationRepository;
    this.authClient = authClient;
    this.dataClient = dataClient;
    this.cryptoService = cryptoService;
    this.clock = clock;
  }

  @Override
  public Optional<TransactionStatus> statusAtSource(
      UUID userId, UUID accountId, String externalTransactionId) {
    FinancialAccount account = accountRepository.findByIdAndUserId(accountId, userId).orElse(null);
    if (account == null || account.getConnectionId() == null) {
      return Optional.empty();
    }
    PluggyConnection connection =
        connectionRepository.findByIdAndUserId(account.getConnectionId(), userId).orElse(null);
    if (connection == null) {
      return Optional.empty();
    }
    PluggyIntegration integration =
        integrationRepository.findByIdAndUserId(connection.getIntegrationId(), userId).orElse(null);
    if (integration == null) {
      return Optional.empty();
    }

    String externalAccountId =
        cryptoService.decryptString(
            account.getExternalAccountIdEnc(),
            new CryptoContext(userId, ACCOUNT_TABLE, "external_account_id", account.getId()));
    LocalDate from = clock.today().minusMonths(LOOKBACK_MONTHS);
    PluggyModels.PluggyTransactionPage page =
        fetchTransactions(integration, externalAccountId, from);
    for (PluggyModels.PluggyTransaction item : page.items()) {
      if (externalTransactionId.equals(item.externalId())) {
        return Optional.of(
            "PENDING".equalsIgnoreCase(item.status())
                ? TransactionStatus.PENDING
                : TransactionStatus.POSTED);
      }
    }
    return Optional.empty();
  }

  private PluggyModels.PluggyTransactionPage fetchTransactions(
      PluggyIntegration integration, String externalAccountId, LocalDate from) {
    String apiKey = authClient.getApiKey(integration);
    try {
      return dataClient.listTransactions(apiKey, externalAccountId, from, null);
    } catch (PluggyUnauthorizedException exception) {
      apiKey = authClient.refreshApiKey(integration);
      return dataClient.listTransactions(apiKey, externalAccountId, from, null);
    }
  }
}
