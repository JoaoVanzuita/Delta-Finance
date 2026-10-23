package com.deltafinance.shared.crypto;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Key ring carregado de um arquivo externo (Docker secret ou arquivo read-only).
 *
 * <p>O arquivo contém chaves de 32 bytes em base64, separadas por finalidade:
 *
 * <pre>{@code
 * {
 *   "data":  [{ "version": 1, "key": "<base64 32B>" }],
 *   "index": [{ "version": 1, "key": "<base64 32B>" }]
 * }
 * }</pre>
 *
 * <p>Chaves antigas permanecem disponíveis para decifrar dados existentes; apenas a versão ativa
 * cifra novos valores. Nenhum método expõe material de chave e o {@code toString} não o inclui.
 */
public final class KeyRing {

  private static final int KEY_BYTES = 32;
  private static final String DATA_PURPOSE = "data";
  private static final String INDEX_PURPOSE = "index";

  private final Map<Integer, SecretKey> dataKeys;
  private final Map<Integer, SecretKey> indexKeys;
  private final int activeDataVersion;
  private final int activeIndexVersion;

  public KeyRing(
      Map<Integer, SecretKey> dataKeys,
      int activeDataVersion,
      Map<Integer, SecretKey> indexKeys,
      int activeIndexVersion) {
    this.dataKeys = Map.copyOf(Objects.requireNonNull(dataKeys, "dataKeys"));
    this.indexKeys = Map.copyOf(Objects.requireNonNull(indexKeys, "indexKeys"));
    this.activeDataVersion = activeDataVersion;
    this.activeIndexVersion = activeIndexVersion;
    requireKey(this.dataKeys, activeDataVersion, "criptografia");
    requireKey(this.indexKeys, activeIndexVersion, "índice");
    if (this.dataKeys.isEmpty() || this.indexKeys.isEmpty()) {
      throw new CryptoException("Key ring precisa de ao menos uma chave de dados e uma de índice");
    }
  }

  /** Carrega e valida o key ring; falha rápido antes de a aplicação atender requisições. */
  public static KeyRing load(Path keyFile, int activeDataVersion, int activeIndexVersion) {
    Objects.requireNonNull(keyFile, "keyFile");
    if (!Files.isReadable(keyFile)) {
      throw new CryptoException("Key ring não encontrado ou sem permissão de leitura: " + keyFile);
    }

    JsonNode root;
    try {
      root = JsonMapper.shared().readTree(Files.readString(keyFile, StandardCharsets.UTF_8));
    } catch (IOException | RuntimeException exception) {
      throw new CryptoException("Key ring inválido em " + keyFile, exception);
    }

    Map<Integer, SecretKey> data = parsePurpose(root, DATA_PURPOSE, keyFile);
    Map<Integer, SecretKey> index = parsePurpose(root, INDEX_PURPOSE, keyFile);
    return new KeyRing(data, activeDataVersion, index, activeIndexVersion);
  }

  private static Map<Integer, SecretKey> parsePurpose(JsonNode root, String purpose, Path keyFile) {
    JsonNode entries = root.path(purpose);
    if (!entries.isArray() || entries.isEmpty()) {
      throw new CryptoException(
          "Key ring inválido em " + keyFile + ": seção '" + purpose + "' ausente ou vazia");
    }
    Map<Integer, SecretKey> keys = new LinkedHashMap<>();
    for (JsonNode entry : entries) {
      int version = entry.path("version").asInt(-1);
      String encoded = entry.path("key").asString(null);
      if (version < 1) {
        throw new CryptoException("Key ring inválido: versão de chave deve ser positiva");
      }
      if (encoded == null || encoded.isBlank()) {
        throw new CryptoException("Key ring inválido: chave ausente na versão " + version);
      }
      byte[] material;
      try {
        material = Base64.getDecoder().decode(encoded.trim());
      } catch (IllegalArgumentException exception) {
        throw new CryptoException("Key ring inválido: chave não está em base64", exception);
      }
      if (material.length != KEY_BYTES) {
        throw new CryptoException(
            "Key ring inválido: chave de " + purpose + " versão " + version + " não tem 32 bytes");
      }
      if (keys.putIfAbsent(version, new SecretKeySpec(material, "AES")) != null) {
        throw new CryptoException("Key ring inválido: versão duplicada " + version);
      }
    }
    return keys;
  }

  public int activeDataVersion() {
    return activeDataVersion;
  }

  public int activeIndexVersion() {
    return activeIndexVersion;
  }

  public SecretKey dataKey(int version) {
    return requireKey(dataKeys, version, "criptografia");
  }

  public SecretKey indexKey(int version) {
    return requireKey(indexKeys, version, "índice");
  }

  public boolean hasDataKey(int version) {
    return dataKeys.containsKey(version);
  }

  public boolean hasIndexKey(int version) {
    return indexKeys.containsKey(version);
  }

  private static SecretKey requireKey(Map<Integer, SecretKey> keys, int version, String label) {
    SecretKey key = keys.get(version);
    if (key == null) {
      throw new CryptoException("Versão " + version + " de chave de " + label + " não encontrada");
    }
    return key;
  }

  @Override
  public String toString() {
    return "KeyRing[dataVersions="
        + dataKeys.keySet()
        + ", indexVersions="
        + indexKeys.keySet()
        + ", activeData="
        + activeDataVersion
        + ", activeIndex="
        + activeIndexVersion
        + "]";
  }
}
