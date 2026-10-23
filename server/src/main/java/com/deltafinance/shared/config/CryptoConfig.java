package com.deltafinance.shared.config;

import com.deltafinance.shared.crypto.CryptoProperties;
import com.deltafinance.shared.crypto.CryptoService;
import com.deltafinance.shared.crypto.IndexHmacService;
import com.deltafinance.shared.crypto.KeyRing;
import java.nio.file.Path;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Carrega o key ring no start e publica os serviços criptográficos. */
@Configuration
@EnableConfigurationProperties(CryptoProperties.class)
public class CryptoConfig {

  @Bean
  KeyRing keyRing(CryptoProperties properties) {
    if (properties.getKeyFile() == null || properties.getKeyFile().isBlank()) {
      throw new IllegalStateException(
          "delta.crypto.key-file não configurado; o key ring externo é obrigatório");
    }
    Path keyFile = Path.of(properties.getKeyFile()).toAbsolutePath().normalize();
    return KeyRing.load(keyFile, properties.getActiveVersion(), properties.getIndexKeyVersion());
  }

  @Bean
  CryptoService cryptoService(KeyRing keyRing) {
    return new CryptoService(keyRing);
  }

  @Bean
  IndexHmacService indexHmacService(KeyRing keyRing) {
    return new IndexHmacService(keyRing);
  }
}
