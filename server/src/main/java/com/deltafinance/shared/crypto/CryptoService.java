package com.deltafinance.shared.crypto;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Objects;
import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Criptografia autenticada de campos sensíveis (AES-256-GCM).
 *
 * <p>Formato do ciphertext: {@code [formato:1][versão da chave:1][nonce:12][ciphertext+tag]}.
 *
 * <p>O AAD vincula o valor ao usuário, tabela, coluna, registro e versão da chave. Mover o
 * ciphertext para outro registro invalida a autenticação.
 */
public final class CryptoService {

  private static final byte FORMAT_VERSION = 1;
  private static final int NONCE_BYTES = 12;
  private static final int TAG_BITS = 128;
  private static final int TAG_BYTES = TAG_BITS / 8;
  private static final int HEADER_BYTES = 2;
  private static final int MIN_PAYLOAD_BYTES = HEADER_BYTES + NONCE_BYTES + TAG_BYTES;
  private static final String TRANSFORMATION = "AES/GCM/NoPadding";
  private static final String AAD_PREFIX = "delta-finance";

  private final KeyRing keyRing;
  private final SecureRandom secureRandom;

  public CryptoService(KeyRing keyRing) {
    this(keyRing, new SecureRandom());
  }

  public CryptoService(KeyRing keyRing, SecureRandom secureRandom) {
    this.keyRing = Objects.requireNonNull(keyRing, "keyRing");
    this.secureRandom = Objects.requireNonNull(secureRandom, "secureRandom");
  }

  public byte[] encrypt(String plaintext, CryptoContext context) {
    Objects.requireNonNull(plaintext, "plaintext");
    return encrypt(plaintext.getBytes(StandardCharsets.UTF_8), context);
  }

  public byte[] encrypt(byte[] plaintext, CryptoContext context) {
    Objects.requireNonNull(plaintext, "plaintext");
    Objects.requireNonNull(context, "context");
    int keyVersion = keyRing.activeDataVersion();

    byte[] nonce = new byte[NONCE_BYTES];
    secureRandom.nextBytes(nonce);

    try {
      Cipher cipher = Cipher.getInstance(TRANSFORMATION);
      byte[] key = keyRing.dataKey(keyVersion).getEncoded();
      cipher.init(
          Cipher.ENCRYPT_MODE,
          new SecretKeySpec(key, "AES"),
          new GCMParameterSpec(TAG_BITS, nonce));
      Arrays.fill(key, (byte) 0);
      cipher.updateAAD(aad(context, keyVersion));

      byte[] sealed = cipher.doFinal(plaintext);
      byte[] payload = new byte[HEADER_BYTES + nonce.length + sealed.length];
      payload[0] = FORMAT_VERSION;
      payload[1] = (byte) keyVersion;
      System.arraycopy(nonce, 0, payload, HEADER_BYTES, nonce.length);
      System.arraycopy(sealed, 0, payload, HEADER_BYTES + nonce.length, sealed.length);
      return payload;
    } catch (GeneralSecurityException exception) {
      throw new CryptoException("Falha ao cifrar campo", exception);
    }
  }

  public String decryptString(byte[] payload, CryptoContext context) {
    return new String(decrypt(payload, context), StandardCharsets.UTF_8);
  }

  public byte[] decrypt(byte[] payload, CryptoContext context) {
    Objects.requireNonNull(payload, "payload");
    Objects.requireNonNull(context, "context");
    if (payload.length < MIN_PAYLOAD_BYTES) {
      throw new CryptoException("Ciphertext truncado ou inválido");
    }
    if (payload[0] != FORMAT_VERSION) {
      throw new CryptoException("Formato de ciphertext desconhecido");
    }
    int keyVersion = Byte.toUnsignedInt(payload[1]);

    try {
      Cipher cipher = Cipher.getInstance(TRANSFORMATION);
      byte[] key = keyRing.dataKey(keyVersion).getEncoded();
      cipher.init(
          Cipher.DECRYPT_MODE,
          new SecretKeySpec(key, "AES"),
          new GCMParameterSpec(TAG_BITS, payload, HEADER_BYTES, NONCE_BYTES));
      Arrays.fill(key, (byte) 0);
      cipher.updateAAD(aad(context, keyVersion));

      int ciphertextOffset = HEADER_BYTES + NONCE_BYTES;
      return cipher.doFinal(payload, ciphertextOffset, payload.length - ciphertextOffset);
    } catch (AEADBadTagException exception) {
      throw new CryptoException(
          "Falha de autenticação: dados adulterados ou contexto divergente", exception);
    } catch (GeneralSecurityException exception) {
      throw new CryptoException("Falha ao decifrar campo", exception);
    }
  }

  /** Versão da chave embutida no ciphertext, útil para rotação e auditoria. */
  public int keyVersionOf(byte[] payload) {
    if (payload == null || payload.length < MIN_PAYLOAD_BYTES) {
      throw new CryptoException("Ciphertext truncado ou inválido");
    }
    if (payload[0] != FORMAT_VERSION) {
      throw new CryptoException("Formato de ciphertext desconhecido");
    }
    return Byte.toUnsignedInt(payload[1]);
  }

  private static byte[] aad(CryptoContext context, int keyVersion) {
    String aad =
        AAD_PREFIX
            + "|fmt="
            + FORMAT_VERSION
            + "|key="
            + keyVersion
            + "|user="
            + context.userId()
            + "|table="
            + context.table()
            + "|column="
            + context.column()
            + "|record="
            + context.recordId();
    return aad.getBytes(StandardCharsets.UTF_8);
  }
}
