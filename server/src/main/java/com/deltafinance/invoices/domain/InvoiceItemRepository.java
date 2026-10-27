package com.deltafinance.invoices.domain;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InvoiceItemRepository extends JpaRepository<InvoiceItem, UUID> {

  Optional<InvoiceItem> findByIdAndUserId(UUID id, UUID userId);

  List<InvoiceItem> findByUserIdAndTransactionIdAndRemovedAtIsNull(UUID userId, UUID transactionId);

  List<InvoiceItem> findByUserIdAndInvoiceIdAndRemovedAtIsNullOrderByOccurredOnAscCreatedAtAsc(
      UUID userId, UUID invoiceId);

  List<InvoiceItem> findByUserIdAndInvoiceIdOrderByOccurredOnAscCreatedAtAsc(
      UUID userId, UUID invoiceId);

  List<InvoiceItem> findByUserIdAndInvoiceIdInAndRemovedAtIsNullOrderByOccurredOnAscCreatedAtAsc(
      UUID userId, Collection<UUID> invoiceIds);

  List<InvoiceItem> findByUserIdAndTransactionId(UUID userId, UUID transactionId);

  /** Todas as parcelas de um plano, inclusive suspensas ou removidas. */
  List<InvoiceItem> findByUserIdAndInstallmentPlanKey(UUID userId, byte[] installmentPlanKey);

  /** Transações com plano de parcelas ativo (mais de uma parcela). */
  @Query(
      """
      select distinct i.transactionId from InvoiceItem i
      where i.userId = :userId
        and i.transactionId is not null
        and i.totalInstallments > 1
        and i.removedAt is null
      """)
  List<UUID> findInstallmentTransactionIds(@Param("userId") UUID userId);

  /** Parcelas vinculadas a transação no intervalo, opcionalmente de um cartão. */
  @Query(
      """
      select i from InvoiceItem i, Invoice v
      where i.invoiceId = v.id
        and i.userId = v.userId
        and i.userId = :userId
        and i.transactionId is not null
        and i.totalInstallments > 1
        and i.removedAt is null
        and i.occurredOn >= :from
        and i.occurredOn < :toExclusive
        and (:accountId is null or v.cardAccountId = :accountId)
      order by i.occurredOn asc, i.createdAt asc, i.id asc
      """)
  List<InvoiceItem> findLinkedInstallmentsForReporting(
      @Param("userId") UUID userId,
      @Param("from") LocalDate from,
      @Param("toExclusive") LocalDate toExclusive,
      @Param("accountId") UUID accountId);

  /** Itens sem transação que compõem despesas no período, opcionalmente de um cartão. */
  @Query(
      """
      select i from InvoiceItem i, Invoice v
      where i.invoiceId = v.id
        and i.userId = v.userId
        and i.userId = :userId
        and i.transactionId is null
        and i.removedAt is null
        and i.occurredOn >= :from
        and i.occurredOn < :toExclusive
        and (:accountId is null or v.cardAccountId = :accountId)
      order by i.invoiceId asc, i.occurredOn asc, i.createdAt asc, i.id asc
      """)
  List<InvoiceItem> findUnlinkedForReporting(
      @Param("userId") UUID userId,
      @Param("from") LocalDate from,
      @Param("toExclusive") LocalDate toExclusive,
      @Param("accountId") UUID accountId);
}
