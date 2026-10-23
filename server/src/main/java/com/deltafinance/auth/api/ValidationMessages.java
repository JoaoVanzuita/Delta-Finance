package com.deltafinance.auth.api;

/** Mensagens de validação reutilizadas (pt-BR) e sem dados sensíveis. */
public final class ValidationMessages {

  public static final String USERNAME_REQUIRED = "Informe o nome de usuário.";
  public static final String USERNAME_SIZE = "Entre 3 e 40 caracteres.";
  public static final String PASSWORD_REQUIRED = "Informe uma senha.";
  public static final String PASSWORD_SIZE = "Entre 12 e 128 caracteres.";
  public static final String CURRENT_PASSWORD_REQUIRED = "Informe a senha atual.";
  public static final String NEW_PASSWORD_REQUIRED = "Informe a nova senha.";
  public static final String NEW_PASSWORD_SIZE = "Entre 12 e 128 caracteres.";

  private ValidationMessages() {}
}
