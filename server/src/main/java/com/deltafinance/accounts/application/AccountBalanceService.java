package com.deltafinance.accounts.application;

import com.deltafinance.accounts.domain.AccountOrigin;
import com.deltafinance.accounts.domain.FinancialAccount;
import com.deltafinance.shared.crypto.CryptoContext;
import com.deltafinance.shared.crypto.CryptoService;
import com.deltafinance.shared.money.Money;
import com.deltafinance.shared.time.Clock;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Saldo efetivo de contas manuais.
 *
 * <p>O valor informado pelo usuário é um saldo de <em>referência</em> em uma data. Para contas
 * manuais, o saldo atual é esse valor ajustado por tudo o que movimentou a conta após a referência:
 *
 * <ul>
 *   <li>lançamentos {@code POSTED}/{@code RECONCILED} da própria conta, com valor efetivo já
 *       assinado (receitas positivas, despesas negativas, as duas pernas de transferências e
 *       ajustes com sinal);
 *   <li>pagamentos de fatura confirmados que saíram da conta e não têm transação de origem, para
 *       não contar duas vezes o mesmo débito.
 * </ul>
 *
 * <p>Contas importadas preservam o saldo do provedor, que já representa o valor atual. Contas
 * manuais desativadas continuam com o saldo histórico calculado normalmente.
 *
 * <p>A leitura é feita por JDBC para não criar dependência de módulo: transações e faturas já
 * dependem de contas, e uma dependência inversa formaria um ciclo.
 */
@Service
public class AccountBalanceService {

  private static final String TRANSACTION_TABLE = "transaction";
  private static final String PAYMENT_TABLE = "invoice_payment";

  private final JdbcTemplate jdbcTemplate;
  private final CryptoService cryptoService;
  private final Clock clock;

  public AccountBalanceService(
      JdbcTemplate jdbcTemplate, CryptoService cryptoService, Clock clock) {
    this.jdbcTemplate = jdbcTemplate;
    this.cryptoService = cryptoService;
    this.clock = clock;
  }

  /**
   * Devolve o saldo atual para exibição, mantendo o saldo de referência intacto para edição.
   *
   * <p>O filtro de data usa o dia local da referência e inclui lançamentos do mesmo dia, porque a
   * transação guarda apenas a data e não o instante em que ocorreu.
   */
  public String effectiveBalance(FinancialAccount account, String referenceBalance) {
    if (account.getOrigin() != AccountOrigin.MANUAL
        || referenceBalance == null
        || referenceBalance.isBlank()) {
      return referenceBalance;
    }

    BigDecimal balance = new BigDecimal(referenceBalance);
    LocalDate referenceDate =
        account.getBalanceReferenceAt() == null
            ? null
            : LocalDate.ofInstant(account.getBalanceReferenceAt(), clock.zone());
    UUID userId = account.getUserId();
    UUID accountId = account.getId();

    String transactionSql =
        "select id, amount_effective_enc from transaction"
            + " where user_id = ? and account_id = ? and currency = ?"
            + " and status in ('POSTED', 'RECONCILED')"
            + (referenceDate == null ? "" : " and posted_on >= ?");
    List<Object> transactionArgs =
        referenceDate == null
            ? List.of(userId, accountId, account.getCurrency())
            : List.of(userId, accountId, account.getCurrency(), referenceDate);
    for (TransactionAmount row :
        jdbcTemplate.query(
            transactionSql,
            (rs, rowNum) ->
                new TransactionAmount(
                    rs.getObject("id", UUID.class), rs.getBytes("amount_effective_enc")),
            transactionArgs.toArray())) {
      balance = balance.add(decrypt(userId, TRANSACTION_TABLE, "amount_effective", row));
    }

    String paymentSql =
        "select id, amount_enc from invoice_payment"
            + " where user_id = ? and payment_account_id = ? and status = 'CONFIRMED'"
            + " and source_transaction_id is null"
            + (referenceDate == null ? "" : " and paid_on >= ?");
    List<Object> paymentArgs =
        referenceDate == null
            ? List.of(userId, accountId)
            : List.of(userId, accountId, referenceDate);
    for (TransactionAmount row :
        jdbcTemplate.query(
            paymentSql,
            (rs, rowNum) ->
                new TransactionAmount(rs.getObject("id", UUID.class), rs.getBytes("amount_enc")),
            paymentArgs.toArray())) {
      balance = balance.subtract(decrypt(userId, PAYMENT_TABLE, "amount", row));
    }

    return new Money(balance, account.getCurrency()).toPlainString();
  }

  private BigDecimal decrypt(UUID userId, String table, String column, TransactionAmount amount) {
    return new BigDecimal(
        cryptoService.decryptString(
            amount.ciphertext(), new CryptoContext(userId, table, column, amount.id())));
  }

  private record TransactionAmount(UUID id, byte[] ciphertext) {}
}
