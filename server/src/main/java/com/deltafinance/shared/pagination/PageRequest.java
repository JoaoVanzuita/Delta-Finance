package com.deltafinance.shared.pagination;

/**
 * Requisição de página por cursor.
 *
 * <p>O cursor é opaco para o cliente e nunca carrega dados financeiros; cada consulta decide como
 * codificá-lo. O limite é limitado para impedir varreduras acidentais.
 */
public record PageRequest(int limit, String cursor) {

  public static final int DEFAULT_LIMIT = 50;
  public static final int MAX_LIMIT = 100;

  public PageRequest {
    if (limit < 1 || limit > MAX_LIMIT) {
      throw new IllegalArgumentException("limit deve estar entre 1 e " + MAX_LIMIT);
    }
    if (cursor != null && cursor.length() > 512) {
      throw new IllegalArgumentException("cursor excede o tamanho máximo");
    }
  }

  public static PageRequest of(Integer limit, String cursor) {
    int effectiveLimit = limit == null ? DEFAULT_LIMIT : limit;
    String effectiveCursor = cursor == null || cursor.isBlank() ? null : cursor.trim();
    return new PageRequest(effectiveLimit, effectiveCursor);
  }

  public static PageRequest firstPage() {
    return new PageRequest(DEFAULT_LIMIT, null);
  }

  public boolean hasCursor() {
    return cursor != null;
  }

  public PageRequest withLimit(int newLimit) {
    return new PageRequest(newLimit, cursor);
  }
}
