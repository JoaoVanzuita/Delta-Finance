package com.deltafinance.auth.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Parâmetros do Argon2id fixados pela especificação: m=19 MiB, t=2, p=1, salt 16 B, hash 32 B.
 *
 * <p>Calibração alvo: 100–250 ms no hardware de referência. O hash resultante carrega os parâmetros
 * e o salt, permitindo verificação futura mesmo após reavaliação de custo.
 */
@Configuration
public class PasswordConfig {

  static final int SALT_BYTES = 16;
  static final int HASH_BYTES = 32;
  static final int PARALLELISM = 1;
  static final int MEMORY_KIB = 19 * 1024;
  static final int ITERATIONS = 2;

  @Bean
  PasswordEncoder passwordEncoder() {
    return new Argon2PasswordEncoder(SALT_BYTES, HASH_BYTES, PARALLELISM, MEMORY_KIB, ITERATIONS);
  }
}
