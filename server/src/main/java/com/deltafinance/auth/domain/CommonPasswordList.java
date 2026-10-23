package com.deltafinance.auth.domain;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * Lista local e versionada de senhas comuns.
 *
 * <p>Carregada do classpath no start, sem chamada externa. Se a lista tiver menos de 10.000
 * entradas, a aplicação falha: uma lista incompleta não cumpre a política do produto.
 */
@Component
public class CommonPasswordList {

  public static final int MINIMUM_ENTRIES = 10_000;
  static final String DEFAULT_RESOURCE = "security/common-passwords-v1.txt";
  static final String DEFAULT_VERSION = "v1";

  private static final Logger LOG = LoggerFactory.getLogger(CommonPasswordList.class);

  private final Set<String> entries;
  private final String version;

  public CommonPasswordList() {
    this(DEFAULT_RESOURCE, DEFAULT_VERSION);
  }

  public CommonPasswordList(String resourcePath, String version) {
    this.version = version;
    this.entries = load(resourcePath);
    if (entries.size() < MINIMUM_ENTRIES) {
      throw new IllegalStateException(
          "Lista de senhas comuns incompleta: "
              + entries.size()
              + " entradas (mínimo "
              + MINIMUM_ENTRIES
              + ")");
    }
    LOG.info("Lista de senhas comuns carregada: versão={} entradas={}", version, entries.size());
  }

  public boolean contains(String password) {
    return password != null && entries.contains(password.toLowerCase(Locale.ROOT).strip());
  }

  public int size() {
    return entries.size();
  }

  public String version() {
    return version;
  }

  private static Set<String> load(String resourcePath) {
    ClassPathResource resource = new ClassPathResource(resourcePath);
    if (!resource.exists()) {
      throw new IllegalStateException("Lista de senhas comuns não encontrada: " + resourcePath);
    }
    Set<String> loaded = new HashSet<>();
    try (BufferedReader reader =
        new BufferedReader(
            new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
      String line;
      while ((line = reader.readLine()) != null) {
        String candidate = line.strip();
        if (candidate.isEmpty() || candidate.startsWith("#")) {
          continue;
        }
        loaded.add(candidate.toLowerCase(Locale.ROOT));
      }
    } catch (IOException exception) {
      throw new IllegalStateException("Falha ao ler lista de senhas comuns", exception);
    }
    return Set.copyOf(loaded);
  }
}
