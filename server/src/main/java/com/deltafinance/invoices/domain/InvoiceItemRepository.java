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

  Optional<InvoiceItem> findByUserIdAndTransactionIdAndRemovedAtIsNull(
      UUID userId, UUID transactionId);

  Optional<InvoiceItem> findByUserIdAndProviderItemIdHashAndRemovedAtIsNull(
      UUID userId, byte[] providerItemIdHash);

  List<InvoiceItem> findByUserIdAndInvoiceIdAndRemovedAtIsNullOrderByOccurredOnAscCreatedAtAsc(
      UUID userId, UUID invoiceId);

  List<InvoiceItem> findByUserIdAndInvoiceIdOrderByOccurredOnAscCreatedAtAsc(
      UUID userId, UUID invoiceId);

  List<InvoiceItem> findByUserIdAndInvoiceIdInAndRemovedAtIsNullOrderByOccurredOnAscCreatedAtAsc(
      UUID userId, Collection<UUID> invoiceIds);

  List<InvoiceItem> findByUserIdAndTransactionId(UUID userId, UUID transactionId);

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
