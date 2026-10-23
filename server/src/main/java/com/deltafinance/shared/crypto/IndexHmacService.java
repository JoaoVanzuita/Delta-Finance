package com.deltafinance.shared.crypto;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * HMAC-SHA-256 para índices de busca, deduplicação e fingerprints.
 *
 * <p>Valores brutos (itemId, IP, username) são cifrados quando precisam ser recuperados; o HMAC é
 * usado apenas para igualdade/unicidade. Cada domínio tem separação explícita para impedir que o
 * mesmo valor produza o mesmo hash em contextos diferentes.
 */
public final class IndexHmacService {

  private static final String ALGORITHM = "HmacSHA256";
  private static final String DOMAIN_PATTERN = "[a-z0-9:_-]{1,64}";
  private static final char VALUE_SEPARATOR = '\u001f';

  private final KeyRing keyRing;

  public IndexHmacService(KeyRing keyRing) {
    this.keyRing = Objects.requireNonNull(keyRing, "keyRing");
  }

  public int activeKeyVersion() {
    return keyRing.activeIndexVersion();
  }

  public byte[] hmac(String domain, String value) {
    return hmac(keyRing.activeIndexVersion(), domain, value);
  }

  public byte[] hmac(int keyVersion, String domain, String value) {
    Objects.requireNonNull(domain, "domain");
    Objects.requireNonNull(value, "value");
    if (!domain.matches(DOMAIN_PATTERN)) {
      throw new IllegalArgumentException("domínio de HMAC inválido");
    }
    try {
      Mac mac = Mac.getInstance(ALGORITHM);
      byte[] key = keyRing.indexKey(keyVersion).getEncoded();
      mac.init(new SecretKeySpec(key, ALGORITHM));
      java.util.Arrays.fill(key, (byte) 0);
      mac.update(domain.getBytes(StandardCharsets.UTF_8));
      mac.update((byte) 0);
      return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
    } catch (GeneralSecurityException exception) {
      throw new CryptoException("Falha ao calcular HMAC", exception);
    }
  }

  public String hmacHex(String domain, String value) {
    return HexFormat.of().formatHex(hmac(domain, value));
  }

  public String hmacHex(int keyVersion, String domain, String value) {
    return HexFormat.of().formatHex(hmac(keyVersion, domain, value));
  }

  /** Igualdade constante entre dois HMACs (evita timing em verificações de unicidade). */
  public boolean matches(byte[] left, byte[] right) {
    return left != null && right != null && MessageDigest.isEqual(left, right);
  }

  public byte[] usernameHash(String normalizedUsername) {
    return hmac("auth:username", normalizedUsername);
  }

  public byte[] ipHash(String ipAddress) {
    String value = ipAddress == null || ipAddress.isBlank() ? "unknown" : ipAddress.trim();
    return hmac("auth:ip", value);
  }

  public byte[] externalIdHash(String provider, String externalId) {
    return hmac("external-id:" + normalizeDomain(provider), Objects.requireNonNull(externalId));
  }

  public byte[] dedupKey(
      String provider, UUID connectionId, String externalAccountId, String externalTransactionId) {
    String value =
        String.join(
            String.valueOf(VALUE_SEPARATOR),
            normalizeDomain(provider),
            connectionId.toString(),
            Objects.requireNonNull(externalAccountId),
            Objects.requireNonNull(externalTransactionId));
    return hmac("dedup", value);
  }

  /** Fingerprint de reconciliação quando o ID externo muda. */
  public byte[] fingerprint(String... parts) {
    Objects.requireNonNull(parts, "parts");
    StringBuilder value = new StringBuilder();
    for (int index = 0; index < parts.length; index++) {
      if (index > 0) {
        value.append(VALUE_SEPARATOR);
      }
      value.append(Objects.requireNonNull(parts[index], "part"));
    }
    return hmac("fingerprint", value.toString());
  }

  private static String normalizeDomain(String provider) {
    return Objects.requireNonNull(provider, "provider").toLowerCase(java.util.Locale.ROOT);
  }
}
