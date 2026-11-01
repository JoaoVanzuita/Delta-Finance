package com.deltafinance.investments.application;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Aportes, resgates e aplicações do perímetro de investimento.
 *
 * <p>O relatório separa o caixa (aporte/resgate) da aplicação em ativos (BUY/SELL). Aportes são
 * créditos que entram na conta marcada como investimento vindos de fora do perímetro; aplicações
 * são as compras registradas pelo provedor. A soma dos dois nunca deve ser tratada como um único
 * aporte.
 */
public record InvestmentContributionView(
    LocalDate from,
    LocalDate to,
    String currency,
    String contributions,
    String withdrawals,
    String netContributions,
    String applied,
    String redeemed,
    String income,
    String fees,
    String idleCash,
    String portfolioValue,
    String cashBalance,
    String settlementDebits,
    String result,
    String resultPercentage,
    String cumulativeContributions,
    String cumulativeWithdrawals,
    String feeSource,
    String reconciliationDifference,
    int contributionCount,
    int withdrawalCount,
    int applicationCount,
    int positionCount,
    List<Month> months,
    List<Flow> flows,
    List<Source> sources) {

  /** Agregação mensal dos fluxos do período. */
  public record Month(
      String month,
      String contributions,
      String withdrawals,
      String netContributions,
      String applied,
      String redeemed,
      String income,
      String fees,
      String idleCash) {}

  /**
   * Um evento do relatório.
   *
   * <p>{@code amount} é assinado para fluxos de caixa (aporte positivo, resgate/aplicação/custo
   * negativos, provento positivo). Transferências internas aparecem com valor absoluto e não entram
   * nos totais.
   */
  public record Flow(
      LocalDate date,
      String kind,
      String amount,
      String description,
      UUID accountId,
      UUID connectionId,
      UUID transactionId,
      UUID investmentId,
      String investmentName,
      String investmentCode,
      String source,
      String status) {}

  /** Conexão incluída no perímetro e como as contas foram selecionadas. */
  public record Source(
      UUID connectionId,
      String name,
      int accountCount,
      int positionCount,
      String perimeterSource) {}
}
