package com.deltafinance.auth.domain;

import com.deltafinance.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;

/**
 * Conta de acesso.
 *
 * <p>Não contém dados financeiros. O nome normalizado é a chave de unicidade e de login; o nome de
 * exibição preserva a capitalização informada pelo usuário.
 */
@Entity
@Table(name = "app_user")
public class AppUser extends BaseEntity {

  @Column(name = "username_normalized", nullable = false, unique = true, length = 40)
  private String usernameNormalized;

  @Column(name = "username_display", nullable = false, length = 40)
  private String usernameDisplay;

  @Column(name = "password_hash", nullable = false)
  private String passwordHash;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 32)
  private UserStatus status;

  @Column(name = "deletion_requested_at")
  private Instant deletionRequestedAt;

  @Column(name = "deleted_at")
  private Instant deletedAt;

  protected AppUser() {
    // Hibernate
  }

  public AppUser(String usernameNormalized, String usernameDisplay, String passwordHash) {
    this.usernameNormalized = Objects.requireNonNull(usernameNormalized, "usernameNormalized");
    this.usernameDisplay = Objects.requireNonNull(usernameDisplay, "usernameDisplay");
    this.passwordHash = Objects.requireNonNull(passwordHash, "passwordHash");
    this.status = UserStatus.ACTIVE;
  }

  public String getUsernameNormalized() {
    return usernameNormalized;
  }

  public String getUsernameDisplay() {
    return usernameDisplay;
  }

  public String getPasswordHash() {
    return passwordHash;
  }

  public UserStatus getStatus() {
    return status;
  }

  public Instant getDeletionRequestedAt() {
    return deletionRequestedAt;
  }

  public Instant getDeletedAt() {
    return deletedAt;
  }

  public boolean isActive() {
    return status == UserStatus.ACTIVE;
  }

  public void changePasswordHash(String newPasswordHash) {
    this.passwordHash = Objects.requireNonNull(newPasswordHash, "newPasswordHash");
  }

  public void requestDeletion(Instant requestedAt) {
    this.status = UserStatus.DELETION_PENDING;
    this.deletionRequestedAt = Objects.requireNonNull(requestedAt, "requestedAt");
  }

  public void markDeleted(Instant deletedAt) {
    this.status = UserStatus.DELETED;
    this.deletedAt = Objects.requireNonNull(deletedAt, "deletedAt");
  }
}
