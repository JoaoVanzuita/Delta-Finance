package com.deltafinance.shared.crypto;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Configuração do key ring e das versões de chave ativas. */
@ConfigurationProperties(prefix = "delta.crypto")
public class CryptoProperties {

  /**
   * Arquivo JSON do key ring, fora do banco e da imagem.
   *
   * <p>String (e não Path) porque o binder do Spring rejeita caminhos relativos contendo `..`;
   * {@code CryptoConfig} converte para caminho absoluto e normaliza antes de ler.
   */
  private String keyFile;

  /** Versão da chave de criptografia usada em novos ciphertexts. */
  private int activeVersion = 1;

  /** Versão da chave HMAC usada em novos índices. */
  private int indexKeyVersion = 1;

  public String getKeyFile() {
    return keyFile;
  }

  public void setKeyFile(String keyFile) {
    this.keyFile = keyFile;
  }

  public int getActiveVersion() {
    return activeVersion;
  }

  public void setActiveVersion(int activeVersion) {
    this.activeVersion = activeVersion;
  }

  public int getIndexKeyVersion() {
    return indexKeyVersion;
  }

  public void setIndexKeyVersion(int indexKeyVersion) {
    this.indexKeyVersion = indexKeyVersion;
  }
}
