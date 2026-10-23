package com.deltafinance.accounts.domain;

import com.deltafinance.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Conta bancária, carteira ou cartão de crédito. */
@Entity
@Table(name = "financial_account")
public class FinancialAccount extends BaseEntity {

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(name = "connection_id")
  private UUID connectionId;

  @Enumerated(EnumType.STRING)
  @Column(name = "origin", nullable = false, length = 16)
  private AccountOrigin origin;

  @Enumerated(EnumType.STRING)
  @Column(name = "type", nullable = false, length = 16)
  private AccountType type;

  @Column(name = "subtype", length = 64)
  private String subtype;

  @Column(name = "external_account_id_enc", columnDefinition = "bytea")
  private byte[] externalAccountIdEnc;

  @Column(name = "external_account_id_hash", columnDefinition = "bytea")
  private byte[] externalAccountIdHash;

  @Column(name = "institution_name_enc", columnDefinition = "bytea")
  private byte[] institutionNameEnc;

  @Column(name = "alias_enc", columnDefinition = "bytea")
  private byte[] aliasEnc;

  @Column(name = "currency", nullable = false, length = 3)
  private String currency;

  @Column(name = "balance_enc", columnDefinition = "bytea")
  private byte[] balanceEnc;

  @Column(name = "balance_reference_at")
  private Instant balanceReferenceAt;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 16)
  private AccountStatus status;

  @Column(name = "last_sync_at")
  private Instant lastSyncAt;

  @Column(name = "notes_enc", columnDefinition = "bytea")
  private byte[] notesEnc;

  protected FinancialAccount() {
    // Hibernate
  }

  public static FinancialAccount imported(
      UUID userId, UUID connectionId, AccountType type, String subtype, String currency) {
    FinancialAccount account = new FinancialAccount();
    account.userId = Objects.requireNonNull(userId, "userId");
    account.connectionId = Objects.requireNonNull(connectionId, "connectionId");
    account.origin = AccountOrigin.IMPORTED;
    account.type = Objects.requireNonNull(type, "type");
    account.subtype = subtype;
    account.currency = currency == null || currency.isBlank() ? "BRL" : currency;
    account.status = AccountStatus.ACTIVE;
    return account;
  }

  /** Conta manual de dinheiro/carteira, sem conexão externa. */
  public static FinancialAccount manual(UUID userId, AccountType type, String currency) {
    FinancialAccount account = new FinancialAccount();
    account.userId = Objects.requireNonNull(userId, "userId");
    account.origin = AccountOrigin.MANUAL;
    account.type = Objects.requireNonNull(type, "type");
    account.currency = currency == null || currency.isBlank() ? "BRL" : currency;
    account.status = AccountStatus.ACTIVE;
    return account;
  }

  public UUID getUserId() {
    return userId;
  }

  public UUID getConnectionId() {
    return connectionId;
  }

  public AccountOrigin getOrigin() {
    return origin;
  }

  public AccountType getType() {
    return type;
  }

  public String getSubtype() {
    return subtype;
  }

  public byte[] getExternalAccountIdEnc() {
    return externalAccountIdEnc;
  }

  public byte[] getExternalAccountIdHash() {
    return externalAccountIdHash;
  }

  public byte[] getInstitutionNameEnc() {
    return institutionNameEnc;
  }

  public byte[] getAliasEnc() {
    return aliasEnc;
  }

  public String getCurrency() {
    return currency;
  }

  public byte[] getBalanceEnc() {
    return balanceEnc;
  }

  public Instant getBalanceReferenceAt() {
    return balanceReferenceAt;
  }

  public AccountStatus getStatus() {
    return status;
  }

  public Instant getLastSyncAt() {
    return lastSyncAt;
  }

  public byte[] getNotesEnc() {
    return notesEnc;
  }

  public void applyImported(
      byte[] externalAccountIdEnc,
      byte[] externalAccountIdHash,
      byte[] institutionNameEnc,
      byte[] aliasEnc,
      byte[] balanceEnc,
      Instant balanceReferenceAt,
      AccountStatus status) {
    this.externalAccountIdEnc =
        Objects.requireNonNull(externalAccountIdEnc, "externalAccountIdEnc");
    this.externalAccountIdHash =
        Objects.requireNonNull(externalAccountIdHash, "externalAccountIdHash");
    this.institutionNameEnc = institutionNameEnc;
    this.aliasEnc = aliasEnc;
    this.balanceEnc = balanceEnc;
    this.balanceReferenceAt = balanceReferenceAt;
    this.status = Objects.requireNonNull(status, "status");
    this.lastSyncAt = Instant.now();
  }

  public void markUnavailable() {
    this.status = AccountStatus.UNAVAILABLE;
  }

  public void deactivate() {
    this.status = AccountStatus.INACTIVE;
  }

  public void activate() {
    this.status = AccountStatus.ACTIVE;
  }

  /** Atualiza campos editáveis de uma conta manual. */
  public void applyManual(
      byte[] aliasEnc,
      byte[] balanceEnc,
      Instant balanceReferenceAt,
      AccountStatus status,
      byte[] notesEnc) {
    this.aliasEnc = aliasEnc;
    this.balanceEnc = balanceEnc;
    this.balanceReferenceAt = balanceReferenceAt;
    this.status = Objects.requireNonNull(status, "status");
    this.notesEnc = notesEnc;
  }

  public void updateSubtype(String subtype) {
    this.subtype = subtype;
  }
}
