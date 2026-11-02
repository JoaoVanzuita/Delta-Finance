package com.deltafinance.accounts.api;

import com.deltafinance.accounts.application.ReservedBalanceView;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record ReservedBalanceResponse(
    UUID id,
    UUID accountId,
    String name,
    String providerName,
    boolean nameCustomized,
    String amount,
    String currency,
    LocalDate referenceDate,
    boolean stale,
    List<AmountResponse> amounts) {

  public record AmountResponse(
      String amount,
      String currency,
      String indexer,
      String rateType,
      String calculation,
      String ratePeriodicity,
      String preFixedRate,
      String postFixedIndexerPercentage,
      String indexerAdditionalInfo) {}

  public static ReservedBalanceResponse from(ReservedBalanceView view) {
    return new ReservedBalanceResponse(
        view.id(),
        view.accountId(),
        view.name(),
        view.providerName(),
        view.nameCustomized(),
        view.amount(),
        view.currency(),
        view.referenceDate(),
        view.stale(),
        view.amounts().stream()
            .map(
                amount ->
                    new AmountResponse(
                        amount.amount(),
                        amount.currency(),
                        amount.indexer(),
                        amount.rateType(),
                        amount.calculation(),
                        amount.ratePeriodicity(),
                        amount.preFixedRate(),
                        amount.postFixedIndexerPercentage(),
                        amount.indexerAdditionalInfo()))
            .toList());
  }
}
