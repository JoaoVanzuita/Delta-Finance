package com.deltafinance.categories.domain;

import com.deltafinance.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.Objects;
import java.util.UUID;

/** Categoria de usuário; nome cifrado e unicidade por HMAC. */
@Entity
@Table(name = "category")
public class Category extends BaseEntity {

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(name = "name_enc", nullable = false, columnDefinition = "bytea")
  private byte[] nameEnc;

  @Column(name = "normalized_name_hash", nullable = false, columnDefinition = "bytea")
  private byte[] normalizedNameHash;

  @Enumerated(EnumType.STRING)
  @Column(name = "kind", nullable = false, length = 16)
  private CategoryKind kind;

  @Column(name = "predefined_key", length = 64)
  private String predefinedKey;

  @Column(name = "active", nullable = false)
  private boolean active;

  protected Category() {
    // Hibernate
  }

  public Category(
      UUID userId,
      byte[] nameEnc,
      byte[] normalizedNameHash,
      CategoryKind kind,
      String predefinedKey) {
    this(UUID.randomUUID(), userId, nameEnc, normalizedNameHash, kind, predefinedKey);
  }

  public Category(
      UUID id,
      UUID userId,
      byte[] nameEnc,
      byte[] normalizedNameHash,
      CategoryKind kind,
      String predefinedKey) {
    super(id);
    this.userId = Objects.requireNonNull(userId, "userId");
    this.nameEnc = Objects.requireNonNull(nameEnc, "nameEnc");
    this.normalizedNameHash = Objects.requireNonNull(normalizedNameHash, "normalizedNameHash");
    this.kind = Objects.requireNonNull(kind, "kind");
    this.predefinedKey = predefinedKey;
    this.active = true;
  }

  public UUID getUserId() {
    return userId;
  }

  public byte[] getNameEnc() {
    return nameEnc;
  }

  public byte[] getNormalizedNameHash() {
    return normalizedNameHash;
  }

  public CategoryKind getKind() {
    return kind;
  }

  public String getPredefinedKey() {
    return predefinedKey;
  }

  public boolean isActive() {
    return active;
  }

  public void deactivate() {
    this.active = false;
  }

  public void rename(byte[] nameEnc, byte[] normalizedNameHash) {
    this.nameEnc = Objects.requireNonNull(nameEnc, "nameEnc");
    this.normalizedNameHash = Objects.requireNonNull(normalizedNameHash, "normalizedNameHash");
  }
}
