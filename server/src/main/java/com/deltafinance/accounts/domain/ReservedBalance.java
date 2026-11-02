package com.deltafinance.accounts.domain;

import com.deltafinance.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Saldo reservado dentro de uma conta bancária.
 *
 * <p>Não é conta e não é investimento: é dinheiro earmarked (cofrinho, caixinha, bloqueio,
 * agendamento). O nome recebido do provedor nunca é sobrescrito pelo nome local.
 */
@Entity
@Table(name = "reserved_balance")
public class ReservedBalance extends BaseEntity {

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(name = "account_id", nullable = false)
  private UUID accountId;

  @Column(name = "connection_id", nullable = false)
  private UUID connectionId;

  @Column(name = "provider_identification_enc", columnDefinition = "bytea")
  private byte[] providerIdentificationEnc;

  @Column(name = "provider_identification_hash", nullable = false, columnDefinition = "bytea")
  private byte[] providerIdentificationHash;

  @Column(name = "provider_name_enc", columnDefinition = "bytea")
  private byte[] providerNameEnc;

  @Column(name = "display_name_enc", columnDefinition = "bytea")
  private byte[] displayNameEnc;

  @Column(name = "currency", nullable = false, length = 3)
  private String currency;

  @Column(name = "amount_enc", nullable = false, columnDefinition = "bytea")
  private byte[] amountEnc;

  @Column(name = "details_enc", columnDefinition = "bytea")
  private byte[] detailsEnc;

  @Column(name = "reference_date")
  private LocalDate referenceDate;

  @Column(name = "last_seen_at", nullable = false)
  private Instant lastSeenAt;

  @Column(name = "removed_at")
  private Instant removedAt;

  protected ReservedBalance() {
    // Hibernate
  }

  public ReservedBalance(
      UUID userId, UUID accountId, UUID connectionId, byte[] providerIdentificationHash) {
    this.userId = Objects.requireNonNull(userId, "userId");
    this.accountId = Objects.requireNonNull(accountId, "accountId");
    this.connectionId = Objects.requireNonNull(connectionId, "connectionId");
    this.providerIdentificationHash =
        Objects.requireNonNull(providerIdentificationHash, "providerIdentificationHash");
  }

  public void apply(
      byte[] providerIdentificationEnc,
      byte[] providerNameEnc,
      String currency,
      byte[] amountEnc,
      byte[] detailsEnc,
      LocalDate referenceDate,
      Instant lastSeenAt) {
    this.providerIdentificationEnc = providerIdentificationEnc;
    this.providerNameEnc = providerNameEnc;
    this.currency = Objects.requireNonNull(currency, "currency");
    this.amountEnc = Objects.requireNonNull(amountEnc, "amountEnc");
    this.detailsEnc = detailsEnc;
    this.referenceDate = referenceDate;
    this.lastSeenAt = Objects.requireNonNull(lastSeenAt, "lastSeenAt");
    this.removedAt = null;
  }

  public void renameDisplayName(byte[] displayNameEnc) {
    this.displayNameEnc = Objects.requireNonNull(displayNameEnc, "displayNameEnc");
  }

  public void clearDisplayName() {
    this.displayNameEnc = null;
  }

  public void markRemoved(Instant at) {
    this.removedAt = Objects.requireNonNull(at, "at");
  }

  public UUID getUserId() {
    return userId;
  }

  public UUID getAccountId() {
    return accountId;
  }

  public UUID getConnectionId() {
    return connectionId;
  }

  public byte[] getProviderIdentificationEnc() {
    return providerIdentificationEnc;
  }

  public byte[] getProviderIdentificationHash() {
    return providerIdentificationHash;
  }

  public byte[] getProviderNameEnc() {
    return providerNameEnc;
  }

  public byte[] getDisplayNameEnc() {
    return displayNameEnc;
  }

  public String getCurrency() {
    return currency;
  }

  public byte[] getAmountEnc() {
    return amountEnc;
  }

  public byte[] getDetailsEnc() {
    return detailsEnc;
  }

  public LocalDate getReferenceDate() {
    return referenceDate;
  }

  public Instant getLastSeenAt() {
    return lastSeenAt;
  }

  public Instant getRemovedAt() {
    return removedAt;
  }

  public boolean isRemoved() {
    return removedAt != null;
  }
}
