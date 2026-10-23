package com.deltafinance.shared.logging;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Mascaramento defensivo de segredos em texto de log.
 *
 * <p>Esta classe é a última linha de defesa: o código não deve registrar segredos, valores
 * financeiros ou corpos de requisição. Ela cobre padrões comuns de vazamento acidental ({@code
 * clientSecret=...}, {@code Authorization: Bearer ...}, {@code token=...}) sem tentar interpretar o
 * conteúdo.
 */
public final class SensitiveDataMasker {

  private static final String MASK = "***";
  private static final Pattern SECRET_ASSIGNMENT =
      Pattern.compile(
          "(?i)(client[_-]?secret|client[_-]?id|api[_-]?key|access[_-]?token|refresh[_-]?token|"
              + "password|passwd|authorization|token|cookie)\\s*[:=]\\s*(\"[^\"]*\"|'[^']*'|[^\\s,;&]+)");
  private static final Pattern BEARER = Pattern.compile("(?i)(Bearer)\\s+[A-Za-z0-9._~+/-]+=*");
  private static final Pattern LONG_SECRET_LIKE = Pattern.compile("[A-Za-z0-9_-]{40,}");

  private SensitiveDataMasker() {}

  public static String mask(String value) {
    if (value == null || value.isEmpty()) {
      return value;
    }
    String masked = BEARER.matcher(value).replaceAll("$1 " + MASK);
    Matcher matcher = SECRET_ASSIGNMENT.matcher(masked);
    StringBuilder builder = new StringBuilder();
    while (matcher.find()) {
      matcher.appendReplacement(builder, Matcher.quoteReplacement(matcher.group(1) + "=" + MASK));
    }
    matcher.appendTail(builder);
    return builder.toString();
  }

  /**
   * Evita registrar identificadores opacos longos (possíveis API Keys) fora de um par chave/valor.
   * Use apenas em mensagens que não deveriam conter identificadores externos.
   */
  public static String maskOpaqueIdentifiers(String value) {
    if (value == null || value.isEmpty()) {
      return value;
    }
    return LONG_SECRET_LIKE.matcher(value).replaceAll(MASK);
  }
}
