package com.deltafinance.shared.crypto;

/** Falha de criptografia/decifração sem vazar conteúdo ou material de chave. */
public class CryptoException extends RuntimeException {

  public CryptoException(String message) {
    super(message);
  }

  public CryptoException(String message, Throwable cause) {
    super(message, cause);
  }
}
