package com.deltafinance.auth.domain;

import java.util.Locale;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * Política mínima de senha do produto.
 *
 * <ul>
 *   <li>12 a 128 caracteres (frases-senha são permitidas; não há composição obrigatória);
 *   <li>não pode ser igual nem conter o nome de usuário normalizado;
 *   <li>não pode estar na lista local versionada de senhas comuns;
 *   <li>mesma política no cadastro e na troca de senha.
 * </ul>
 */
@Component
public class PasswordPolicy {

  public static final int MIN_LENGTH = 12;
  public static final int MAX_LENGTH = 128;

  private final CommonPasswordList commonPasswords;

  public PasswordPolicy(CommonPasswordList commonPasswords) {
    this.commonPasswords = Objects.requireNonNull(commonPasswords, "commonPasswords");
  }

  public void validate(String usernameNormalized, String password) {
    if (password == null || password.isBlank()) {
      throw new PasswordPolicyException(PasswordPolicyException.Reason.BLANK);
    }
    int length = password.codePointCount(0, password.length());
    if (length < MIN_LENGTH) {
      throw new PasswordPolicyException(PasswordPolicyException.Reason.TOO_SHORT);
    }
    if (length > MAX_LENGTH) {
      throw new PasswordPolicyException(PasswordPolicyException.Reason.TOO_LONG);
    }

    String lower = password.toLowerCase(Locale.ROOT);
    if (lower.contains(usernameNormalized)) {
      throw new PasswordPolicyException(PasswordPolicyException.Reason.CONTAINS_USERNAME);
    }
    if (commonPasswords.contains(lower)) {
      throw new PasswordPolicyException(PasswordPolicyException.Reason.COMMON_PASSWORD);
    }
  }

  /** Mensagem segura (sem ecoar a senha) para exibir no formulário. */
  public static String describe(PasswordPolicyException.Reason reason) {
    return switch (reason) {
      case BLANK -> "Informe uma senha.";
      case TOO_SHORT -> "A senha deve ter pelo menos 12 caracteres.";
      case TOO_LONG -> "A senha deve ter no máximo 128 caracteres.";
      case CONTAINS_USERNAME -> "A senha não pode conter o nome de usuário.";
      case COMMON_PASSWORD -> "Escolha uma senha menos comum.";
    };
  }

  public String commonPasswordListVersion() {
    return commonPasswords.version();
  }
}
