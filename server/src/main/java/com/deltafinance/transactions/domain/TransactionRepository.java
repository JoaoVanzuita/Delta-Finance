package com.deltafinance.transactions.domain;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

  Optional<Transaction> findByUserIdAndDedupKey(UUID userId, byte[] dedupKey);

  Optional<Transaction> findByIdAndUserId(UUID id, UUID userId);

  Optional<Transaction> findFirstByUserIdAndAccountIdAndFingerprint(
      UUID userId, UUID accountId, byte[] fingerprint);

  boolean existsByUserIdAndRelatedTransactionId(UUID userId, UUID relatedTransactionId);

  List<Transaction> findByUserIdAndAccountIdOrderByPostedOnDescIdDesc(UUID userId, UUID accountId);

  List<Transaction> findByUserIdOrderByCreatedAtDesc(UUID userId);

  @Query(
      """
      select t from Transaction t
      where t.userId = :userId
        and (:accountId is null or t.accountId = :accountId)
        and (:categoryId is null or t.categoryId = :categoryId)
        and (:status is null or t.status = :status)
        and (:type is null or t.type = :type)
        and (:source is null or t.source = :source)
        and (:from is null or t.postedOn >= :from)
        and (:to is null or t.postedOn <= :to)
        and (
          :cursorPostedOn is null
          or t.postedOn < :cursorPostedOn
          or (t.postedOn = :cursorPostedOn and t.id < :cursorId)
        )
      order by t.postedOn desc, t.id desc
      """)
  List<Transaction> findPage(
      @Param("userId") UUID userId,
      @Param("accountId") UUID accountId,
      @Param("categoryId") UUID categoryId,
      @Param("status") TransactionStatus status,
      @Param("type") TransactionType type,
      @Param("source") TransactionSource source,
      @Param("from") LocalDate from,
      @Param("to") LocalDate to,
      @Param("cursorPostedOn") LocalDate cursorPostedOn,
      @Param("cursorId") UUID cursorId,
      Pageable pageable);

  /**
   * Candidatos para relatórios: apenas metadados claros são filtrados no banco; valor, descrição e
   * faixa monetária são aplicados após a decifragem no módulo de relatórios.
   */
  @Query(
      """
      select t from Transaction t
      where t.userId = :userId
        and t.postedOn >= :from
        and t.postedOn < :toExclusive
        and (:accountId is null or t.accountId = :accountId)
        and (:categoryId is null or t.categoryId = :categoryId)
        and (:type is null or t.type = :type)
        and (:source is null or t.source = :source)
        and t.status in :statuses
      order by t.postedOn asc, t.id asc
      """)
  List<Transaction> findForReporting(
      @Param("userId") UUID userId,
      @Param("from") LocalDate from,
      @Param("toExclusive") LocalDate toExclusive,
      @Param("accountId") UUID accountId,
      @Param("categoryId") UUID categoryId,
      @Param("type") TransactionType type,
      @Param("source") TransactionSource source,
      @Param("statuses") Collection<TransactionStatus> statuses);
}
