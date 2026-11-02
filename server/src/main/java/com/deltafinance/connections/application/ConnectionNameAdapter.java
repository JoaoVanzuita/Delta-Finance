package com.deltafinance.connections.application;

import com.deltafinance.accounts.application.ConnectionNamePort;
import com.deltafinance.connections.domain.PluggyConnection;
import com.deltafinance.connections.domain.PluggyConnectionRepository;
import com.deltafinance.shared.crypto.EncryptedValueReader;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Implementa a porta de nome de exibição usada pelas contas/cartões.
 *
 * <p>Prefere o nome personalizado da conexão e recua para o nome da instituição do item. Em
 * conexões MeuPluggy o nome do item é o agregador, por isso o nome da conexão (renomeado para o
 * banco real) é a melhor referência visual.
 */
@Component
public class ConnectionNameAdapter implements ConnectionNamePort {

  private static final String CONNECTION_TABLE = "pluggy_connection";

  private final PluggyConnectionRepository connectionRepository;
  private final EncryptedValueReader encryptedValueReader;

  public ConnectionNameAdapter(
      PluggyConnectionRepository connectionRepository, EncryptedValueReader encryptedValueReader) {
    this.connectionRepository = connectionRepository;
    this.encryptedValueReader = encryptedValueReader;
  }

  @Override
  public String displayNameOf(UUID userId, UUID connectionId) {
    if (connectionId == null) {
      return null;
    }
    PluggyConnection connection =
        connectionRepository.findByIdAndUserId(connectionId, userId).orElse(null);
    if (connection == null) {
      return null;
    }
    String customName = decrypt(connection, connection.getDisplayNameEnc(), "display_name");
    if (customName != null && !customName.isBlank()) {
      return customName;
    }
    return decrypt(connection, connection.getInstitutionNameEnc(), "institution_name");
  }

  private String decrypt(PluggyConnection connection, byte[] ciphertext, String column) {
    // Nome é apenas apresentação: chave antiga/ilegível vira nulo e a listagem continua.
    return encryptedValueReader
        .tryRead(connection.getUserId(), CONNECTION_TABLE, column, connection.getId(), ciphertext)
        .orElse(null);
  }
}
