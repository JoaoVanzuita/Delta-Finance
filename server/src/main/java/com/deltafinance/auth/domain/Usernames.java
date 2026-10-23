package com.deltafinance.auth.domain;

import java.text.Normalizer;
import java.util.Locale;

/** Normalização e validação básica de nomes de usuário. */
public final class Usernames {

  public static final int MIN_LENGTH = 3;
  public static final int MAX_LENGTH = 40;

  private Usernames() {}

  /** NFKC + trim + minúsculas. É a forma usada para unicidade, login e comparação de senha. */
  public static String normalize(String rawUsername) {
    if (rawUsername == null) {
      return null;
    }
    return Normalizer.normalize(rawUsername, Normalizer.Form.NFKC).strip().toLowerCase(Locale.ROOT);
  }

  /** Forma de exibição: preserva capitalização e remove apenas espaços nas pontas. */
  public static String display(String rawUsername) {
    if (rawUsername == null) {
      return null;
    }
    return Normalizer.normalize(rawUsername, Normalizer.Form.NFKC).strip();
  }

  public static boolean isValid(String rawUsername) {
    String display = display(rawUsername);
    if (display == null) {
      return false;
    }
    int length = display.codePointCount(0, display.length());
    if (length < MIN_LENGTH || length > MAX_LENGTH) {
      return false;
    }
    return display.codePoints().noneMatch(Character::isISOControl);
  }
}
