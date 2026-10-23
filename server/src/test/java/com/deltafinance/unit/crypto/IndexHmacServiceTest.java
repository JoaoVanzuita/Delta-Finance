package com.deltafinance.unit.crypto;

import static org.assertj.core.api.Assertions.assertThat;

import com.deltafinance.shared.crypto.IndexHmacService;
import com.deltafinance.shared.crypto.KeyRing;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

class IndexHmacServiceTest {

  private final IndexHmacService service = new IndexHmacService(keyRing());

  @Test
  void geraHmacDeterministicoCom64CaracteresHex() {
    String first = service.hmacHex("auth:username", "alice");
    String second = service.hmacHex("auth:username", "alice");

    assertThat(first).isEqualTo(second).hasSize(64).matches("[0-9a-f]{64}");
  }

  @Test
  void separaDominiosParaOMesmoValor() {
    assertThat(service.hmac("auth:username", "alice"))
        .isNotEqualTo(service.hmac("auth:ip", "alice"))
        .isNotEqualTo(service.hmac("external-id:pluggy", "alice"));
  }

  @Test
  void versaoDeChaveDiferenteProduzHashDiferente() {
    byte[] v1 = service.hmac(1, "auth:username", "alice");
    byte[] v2 = service.hmac(2, "auth:username", "alice");

    assertThat(v1).isNotEqualTo(v2);
    assertThat(service.activeKeyVersion()).isEqualTo(2);
  }

  @Test
  void fingerprintEDedupUsamSeparadoresCanonicos() {
    UUID connectionId = UUID.randomUUID();

    assertThat(service.fingerprint("conta", "10.00", "2026-10-22"))
        .isNotEqualTo(service.fingerprint("conta", "10.00", "2026-10-23"));
    assertThat(service.fingerprint("a", "b")).isNotEqualTo(service.fingerprint("ab", ""));
    assertThat(service.dedupKey("pluggy", connectionId, "account-1", "transaction-1"))
        .isNotEqualTo(service.dedupKey("pluggy", connectionId, "account-1", "transaction-2"));
  }

  @Test
  void comparaHashesEmTempoConstante() {
    byte[] left = service.hmac("auth:username", "alice");

    assertThat(service.matches(left, left)).isTrue();
    assertThat(service.matches(left, service.hmac("auth:username", "bob"))).isFalse();
    assertThat(service.matches(left, null)).isFalse();
  }

  @Test
  void ipAusenteNaoQuebraOHash() {
    assertThat(service.ipHash(null)).isEqualTo(service.ipHash("unknown"));
    assertThat(service.ipHash("203.0.113.10")).isNotEqualTo(service.ipHash("unknown"));
  }

  private static KeyRing keyRing() {
    return new KeyRing(
        Map.of(1, key((byte) 0x11), 2, key((byte) 0x22)),
        2,
        Map.of(1, key((byte) 0x33), 2, key((byte) 0x44)),
        2);
  }

  private static SecretKey key(byte seed) {
    byte[] material = new byte[32];
    Arrays.fill(material, seed);
    return new SecretKeySpec(material, "AES");
  }
}
