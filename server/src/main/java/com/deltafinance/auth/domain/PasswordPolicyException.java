package com.deltafinance.auth.domain;

/** Violação da política de senha, com motivo estável para tradução segura na API. */
public class PasswordPolicyException extends RuntimeException {

  public enum Reason {
    BLANK,
    TOO_SHORT,
    TOO_LONG,
    CONTAINS_USERNAME,
    COMMON_PASSWORD
  }

  private final Reason reason;

  public PasswordPolicyException(Reason reason) {
    super("Senha não atende à política: " + reason.name());
    this.reason = reason;
  }

  public Reason reason() {
    return reason;
  }
}
