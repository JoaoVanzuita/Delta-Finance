package com.deltafinance.shared.crypto;

import com.deltafinance.shared.money.Money;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Leitura padronizada de campos cifrados.
 *
 * <p>Antes cada serviço tinha seu próprio {@code decrypt} e escolhia sozinho entre devolver {@code
 * null}, estourar exceção ou mascarar a falha. Aqui a política é explícita:
 *
 * <ul>
 *   <li>{@link #read} devolve {@code null} apenas quando o campo está ausente e propaga falhas de
 *       autenticação/decifragem;
 *   <li>{@link #tryRead} existe para telas que precisam degradar um campo ilegível sem derrubar a
 *       consulta inteira; o chamador decide o fallback;
 *   <li>{@link #readMoney} usa a escala monetária canônica de {@link Money}.
 * </ul>
 */
@Component
public class EncryptedValueReader {

  private final CryptoService cryptoService;

  public EncryptedValueReader(CryptoService cryptoService) {
    this.cryptoService = cryptoService;
  }

  public String read(UUID userId, String table, String column, UUID recordId, byte[] ciphertext) {
    return read(ciphertext, new CryptoContext(userId, table, column, recordId));
  }

  public String read(byte[] ciphertext, CryptoContext context) {
    if (ciphertext == null) {
      return null;
    }
    return cryptoService.decryptString(ciphertext, context);
  }

  public Optional<String> tryRead(
      UUID userId, String table, String column, UUID recordId, byte[] ciphertext) {
    if (ciphertext == null) {
      return Optional.empty();
    }
    try {
      return Optional.of(
          cryptoService.decryptString(
              ciphertext, new CryptoContext(userId, table, column, recordId)));
    } catch (CryptoException unreadable) {
      return Optional.empty();
    }
  }

  public String readOrFallback(
      UUID userId, String table, String column, UUID recordId, byte[] ciphertext, String fallback) {
    return tryRead(userId, table, column, recordId, ciphertext).orElse(fallback);
  }

  /** Campo obrigatório: ausência ou corrupção falham, em vez de virar valor vazio. */
  public String readRequired(
      UUID userId, String table, String column, UUID recordId, byte[] ciphertext) {
    String value = read(userId, table, column, recordId, ciphertext);
    if (value == null || value.isBlank()) {
      throw new CryptoException("Campo cifrado obrigatório ausente: " + table + "." + column);
    }
    return value;
  }

  public BigDecimal readMoney(
      UUID userId, String table, String column, UUID recordId, byte[] ciphertext) {
    String value = read(userId, table, column, recordId, ciphertext);
    if (value == null || value.isBlank()) {
      return null;
    }
    return Money.scaled(new BigDecimal(value));
  }

  public Optional<BigDecimal> tryReadMoney(
      UUID userId, String table, String column, UUID recordId, byte[] ciphertext) {
    return tryRead(userId, table, column, recordId, ciphertext)
        .filter(value -> !value.isBlank())
        .map(value -> Money.scaled(new BigDecimal(value)));
  }

  /** Para somas em que ausência significa zero; corrupção continua sendo erro. */
  public BigDecimal readMoneyOrZero(
      UUID userId, String table, String column, UUID recordId, byte[] ciphertext) {
    BigDecimal value = readMoney(userId, table, column, recordId, ciphertext);
    return value == null ? Money.scaled(BigDecimal.ZERO) : value;
  }
}
