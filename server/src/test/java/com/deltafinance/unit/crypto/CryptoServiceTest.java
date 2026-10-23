package com.deltafinance.unit.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.deltafinance.shared.crypto.CryptoContext;
import com.deltafinance.shared.crypto.CryptoException;
import com.deltafinance.shared.crypto.CryptoService;
import com.deltafinance.shared.crypto.KeyRing;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

class CryptoServiceTest {

  private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
  private static final UUID RECORD_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
  private static final CryptoContext CONTEXT =
      new CryptoContext(USER_ID, "transaction", "description", RECORD_ID);

  private final KeyRing ring = keyRing(2);

  @Test
  void cifraEDecifraTextoComAcentos() {
    CryptoService crypto = new CryptoService(ring, new SecureRandom());

    byte[] payload = crypto.encrypt("Padaria São João — R$ 12,50", CONTEXT);

    assertThat(payload).isNotEqualTo("Padaria São João — R$ 12,50".getBytes());
    assertThat(crypto.decryptString(payload, CONTEXT)).isEqualTo("Padaria São João — R$ 12,50");
    assertThat(crypto.keyVersionOf(payload)).isEqualTo(2);
  }

  @Test
  void usaNonceAleatorioPorCifragem() {
    CryptoService crypto = new CryptoService(ring, new SecureRandom());

    byte[] first = crypto.encrypt("mesmo valor", CONTEXT);
    byte[] second = crypto.encrypt("mesmo valor", CONTEXT);

    assertThat(first).isNotEqualTo(second);
    assertThat(crypto.decryptString(first, CONTEXT)).isEqualTo("mesmo valor");
    assertThat(crypto.decryptString(second, CONTEXT)).isEqualTo("mesmo valor");
  }

  @Test
  void detectaAdulteracaoDoCiphertext() {
    CryptoService crypto = new CryptoService(ring, new SecureRandom());
    byte[] payload = crypto.encrypt("valor sensível", CONTEXT);
    byte[] tampered = payload.clone();
    tampered[tampered.length - 1] ^= 0x01;

    assertThatThrownBy(() -> crypto.decryptString(tampered, CONTEXT))
        .isInstanceOf(CryptoException.class)
        .hasMessageContaining("autenticação");
  }

  @Test
  void detectaContextoDivergente() {
    CryptoService crypto = new CryptoService(ring, new SecureRandom());
    byte[] payload = crypto.encrypt("valor sensível", CONTEXT);

    CryptoContext outroUsuario =
        new CryptoContext(UUID.randomUUID(), "transaction", "description", RECORD_ID);
    CryptoContext outraColuna = new CryptoContext(USER_ID, "transaction", "notes", RECORD_ID);

    assertThatThrownBy(() -> crypto.decryptString(payload, outroUsuario))
        .isInstanceOf(CryptoException.class);
    assertThatThrownBy(() -> crypto.decryptString(payload, outraColuna))
        .isInstanceOf(CryptoException.class);
  }

  @Test
  void decifraDadosDeVersaoAnteriorDuranteRotacao() {
    CryptoService oldWriter = new CryptoService(keyRing(1), new SecureRandom());
    byte[] payload = oldWriter.encrypt("dado antigo", CONTEXT);

    CryptoService newReader = new CryptoService(keyRing(2), new SecureRandom());

    assertThat(newReader.keyVersionOf(payload)).isEqualTo(1);
    assertThat(newReader.decryptString(payload, CONTEXT)).isEqualTo("dado antigo");
  }

  @Test
  void rejeitaPayloadTruncadoOuFormatoDesconhecido() {
    CryptoService crypto = new CryptoService(ring, new SecureRandom());
    byte[] payload = crypto.encrypt("valor", CONTEXT);

    assertThatThrownBy(() -> crypto.decryptString(Arrays.copyOf(payload, 10), CONTEXT))
        .isInstanceOf(CryptoException.class)
        .hasMessageContaining("truncado");

    byte[] formatoDesconhecido = payload.clone();
    formatoDesconhecido[0] = 99;
    assertThatThrownBy(() -> crypto.decryptString(formatoDesconhecido, CONTEXT))
        .isInstanceOf(CryptoException.class)
        .hasMessageContaining("Formato");
  }

  @Test
  void keyRingNaoExpoeMaterialDeChaveNoToString() {
    assertThat(ring.toString()).contains("activeData=2").doesNotContain("key=");
  }

  private static KeyRing keyRing(int activeDataVersion) {
    return new KeyRing(
        Map.of(1, key((byte) 0x11), 2, key((byte) 0x22)),
        activeDataVersion,
        Map.of(1, key((byte) 0x33), 2, key((byte) 0x44)),
        2);
  }

  private static SecretKey key(byte seed) {
    byte[] material = new byte[32];
    Arrays.fill(material, seed);
    return new SecretKeySpec(material, "AES");
  }
}
