package com.deltafinance.unit.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.deltafinance.shared.crypto.CryptoContext;
import com.deltafinance.shared.crypto.CryptoException;
import com.deltafinance.shared.crypto.CryptoService;
import com.deltafinance.shared.crypto.EncryptedValueReader;
import com.deltafinance.shared.crypto.KeyRing;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

/** Política única de leitura de campos cifrados. */
class EncryptedValueReaderTest {

  private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
  private static final UUID RECORD_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
  private static final String TABLE = "transaction";
  private static final String COLUMN = "amount_effective";

  private final CryptoService cryptoService = new CryptoService(keyRing(), new SecureRandom());
  private final EncryptedValueReader reader = new EncryptedValueReader(cryptoService);

  @Test
  void devolveNuloApenasQuandoCampoEstaAusente() {
    assertThat(reader.read(USER_ID, TABLE, COLUMN, RECORD_ID, null)).isNull();
    assertThat(reader.tryRead(USER_ID, TABLE, COLUMN, RECORD_ID, null)).isEmpty();
  }

  @Test
  void leTextoENormalizaDinheiroNaEscalaCanonica() {
    byte[] text = encrypt("Padaria", "description");
    byte[] amount = encrypt("12.5", COLUMN);

    assertThat(reader.read(USER_ID, TABLE, "description", RECORD_ID, text)).isEqualTo("Padaria");
    assertThat(reader.readMoney(USER_ID, TABLE, COLUMN, RECORD_ID, amount)).hasToString("12.50");
    assertThat(reader.readMoneyOrZero(USER_ID, TABLE, COLUMN, RECORD_ID, amount))
        .hasToString("12.50");
    assertThat(reader.readMoneyOrZero(USER_ID, TABLE, COLUMN, RECORD_ID, null)).hasToString("0.00");
  }

  @Test
  void campoObrigatorioAusenteFalha() {
    assertThatThrownBy(() -> reader.readRequired(USER_ID, TABLE, COLUMN, RECORD_ID, null))
        .isInstanceOf(CryptoException.class)
        .hasMessageContaining("obrigatório");
  }

  @Test
  void propagaCorrupcaoNoCaminhoEstritoEDegradaNoTolerante() {
    byte[] payload = encrypt("valor", COLUMN);
    byte[] tampered = payload.clone();
    tampered[tampered.length - 1] ^= 0x01;

    assertThatThrownBy(() -> reader.read(USER_ID, TABLE, COLUMN, RECORD_ID, tampered))
        .isInstanceOf(CryptoException.class);
    assertThat(reader.tryRead(USER_ID, TABLE, COLUMN, RECORD_ID, tampered)).isEmpty();
    assertThat(reader.readOrFallback(USER_ID, TABLE, COLUMN, RECORD_ID, tampered, "—"))
        .isEqualTo("—");
  }

  private byte[] encrypt(String value, String column) {
    return cryptoService.encrypt(value, new CryptoContext(USER_ID, TABLE, column, RECORD_ID));
  }

  private static KeyRing keyRing() {
    return new KeyRing(Map.of(1, key((byte) 0x11)), 1, Map.of(1, key((byte) 0x22)), 1);
  }

  private static SecretKey key(byte seed) {
    byte[] material = new byte[32];
    Arrays.fill(material, seed);
    return new SecretKeySpec(material, "AES");
  }
}
