package com.deltafinance.investments.api;

import com.deltafinance.investments.application.InvestmentContributionView;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Aportes, resgates e aplicações do perímetro de investimento. */
public record InvestmentContributionResponse(
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
    List<MonthResponse> months,
    List<FlowResponse> flows,
    List<SourceResponse> sources) {

  public record MonthResponse(
      String month,
      String contributions,
      String withdrawals,
      String netContributions,
      String applied,
      String redeemed,
      String income,
      String fees,
      String idleCash) {}

  public record FlowResponse(
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

  public record SourceResponse(
      UUID connectionId,
      String name,
      int accountCount,
      int positionCount,
      String perimeterSource) {}

  public static InvestmentContributionResponse from(InvestmentContributionView view) {
    return new InvestmentContributionResponse(
        view.from(),
        view.to(),
        view.currency(),
        view.contributions(),
        view.withdrawals(),
        view.netContributions(),
        view.applied(),
        view.redeemed(),
        view.income(),
        view.fees(),
        view.idleCash(),
        view.portfolioValue(),
        view.cashBalance(),
        view.settlementDebits(),
        view.result(),
        view.resultPercentage(),
        view.cumulativeContributions(),
        view.cumulativeWithdrawals(),
        view.feeSource(),
        view.reconciliationDifference(),
        view.contributionCount(),
        view.withdrawalCount(),
        view.applicationCount(),
        view.positionCount(),
        view.months().stream()
            .map(
                month ->
                    new MonthResponse(
                        month.month(),
                        month.contributions(),
                        month.withdrawals(),
                        month.netContributions(),
                        month.applied(),
                        month.redeemed(),
                        month.income(),
                        month.fees(),
                        month.idleCash()))
            .toList(),
        view.flows().stream()
            .map(
                flow ->
                    new FlowResponse(
                        flow.date(),
                        flow.kind(),
                        flow.amount(),
                        flow.description(),
                        flow.accountId(),
                        flow.connectionId(),
                        flow.transactionId(),
                        flow.investmentId(),
                        flow.investmentName(),
                        flow.investmentCode(),
                        flow.source(),
                        flow.status()))
            .toList(),
        view.sources().stream()
            .map(
                source ->
                    new SourceResponse(
                        source.connectionId(),
                        source.name(),
                        source.accountCount(),
                        source.positionCount(),
                        source.perimeterSource()))
            .toList());
  }
}
