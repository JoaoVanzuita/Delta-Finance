package com.deltafinance.transactions.domain;

import com.deltafinance.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Histórico de identificadores externos de uma transação local estável. */
@Entity
@Table(name = "external_identity_history")
public class ExternalIdentityHistory extends BaseEntity {

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(name = "transaction_id", nullable = false)
  private UUID transactionId;

  @Column(name = "account_id", nullable = false)
  private UUID accountId;

  @Column(name = "source", nullable = false, length = 16)
  private String source;

  @Column(name = "external_id_enc", nullable = false, columnDefinition = "bytea")
  private byte[] externalIdEnc;

  @Column(name = "external_id_hash", nullable = false, columnDefinition = "bytea")
  private byte[] externalIdHash;

  @Column(name = "first_seen_at", nullable = false)
  private Instant firstSeenAt;

  @Column(name = "last_seen_at", nullable = false)
  private Instant lastSeenAt;

  protected ExternalIdentityHistory() {
    // Hibernate
  }

  public ExternalIdentityHistory(
      UUID userId,
      UUID transactionId,
      UUID accountId,
      String source,
      byte[] externalIdEnc,
      byte[] externalIdHash,
      Instant seenAt) {
    this.userId = Objects.requireNonNull(userId, "userId");
    this.transactionId = Objects.requireNonNull(transactionId, "transactionId");
    this.accountId = Objects.requireNonNull(accountId, "accountId");
    this.source = Objects.requireNonNull(source, "source");
    this.externalIdEnc = Objects.requireNonNull(externalIdEnc, "externalIdEnc");
    this.externalIdHash = Objects.requireNonNull(externalIdHash, "externalIdHash");
    this.firstSeenAt = Objects.requireNonNull(seenAt, "seenAt");
    this.lastSeenAt = seenAt;
  }

  public UUID getUserId() {
    return userId;
  }

  public UUID getTransactionId() {
    return transactionId;
  }

  public UUID getAccountId() {
    return accountId;
  }

  public String getSource() {
    return source;
  }

  public byte[] getExternalIdEnc() {
    return externalIdEnc;
  }

  public byte[] getExternalIdHash() {
    return externalIdHash;
  }

  public Instant getFirstSeenAt() {
    return firstSeenAt;
  }

  public Instant getLastSeenAt() {
    return lastSeenAt;
  }

  public void touch(Instant seenAt) {
    this.lastSeenAt = Objects.requireNonNull(seenAt, "seenAt");
  }
}
