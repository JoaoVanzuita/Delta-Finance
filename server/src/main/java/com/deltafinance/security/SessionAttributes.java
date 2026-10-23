package com.deltafinance.security;

/** Atributos de sessão controlados pela aplicação. */
public final class SessionAttributes {

  /** Instante de criação da sessão autenticada, para o limite absoluto de 12 horas. */
  public static final String STARTED_AT = "delta.session.startedAt";

  private SessionAttributes() {}
}
