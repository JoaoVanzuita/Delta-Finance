package com.deltafinance.shared.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Base das entidades JPA.
 *
 * <ul>
 *   <li>IDs são {@link UUID} gerados pela aplicação (nunca pelo banco), permitindo correlação e
 *       idempotência antes do flush;
 *   <li>{@code created_at}/{@code updated_at} são instantes UTC;
 *   <li>{@code version} habilita locking otimista em agregados mutáveis;
 *   <li>alterações em timestamps são responsabilidade da própria entidade, sem depender de um
 *       relógio estático do JPA.
 * </ul>
 */
@MappedSuperclass
public abstract class BaseEntity {

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id = UUID.randomUUID();

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Version
  @Column(name = "version", nullable = false)
  private long version;

  protected BaseEntity() {
    // exigido pelo Hibernate
  }

  protected BaseEntity(UUID id) {
    this.id = Objects.requireNonNull(id, "id");
  }

  @PrePersist
  void onPersist() {
    Instant now = Instant.now();
    if (createdAt == null) {
      createdAt = now;
    }
    updatedAt = now;
  }

  @PreUpdate
  void onUpdate() {
    updatedAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public long getVersion() {
    return version;
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    if (other == null || getClass() != other.getClass()) {
      return false;
    }
    return id.equals(((BaseEntity) other).id);
  }

  @Override
  public int hashCode() {
    return id.hashCode();
  }
}
