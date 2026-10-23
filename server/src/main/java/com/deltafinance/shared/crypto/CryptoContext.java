package com.deltafinance.shared.crypto;

import java.util.Objects;
import java.util.UUID;

/**
 * Contexto autenticado (AAD) de um campo cifrado.
 *
 * <p>Vincular usuário, tabela, coluna e registro impede que um ciphertext válido seja movido para
 * outro registro ou outro usuário sem detecção.
 */
public record CryptoContext(UUID userId, String table, String column, UUID recordId) {

  private static final String IDENTIFIER_PATTERN = "[a-z0-9_]{1,64}";

  public CryptoContext {
    Objects.requireNonNull(userId, "userId");
    Objects.requireNonNull(recordId, "recordId");
    if (table == null || !table.matches(IDENTIFIER_PATTERN)) {
      throw new IllegalArgumentException("tabela inválida para AAD");
    }
    if (column == null || !column.matches(IDENTIFIER_PATTERN)) {
      throw new IllegalArgumentException("coluna inválida para AAD");
    }
  }
}
