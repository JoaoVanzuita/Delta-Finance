package com.deltafinance.integrations.application;

import java.util.UUID;

/**
 * Porta para contagem de conexões, implementada pelo módulo connections.
 *
 * <p>Evita dependência direta integrations → connections e mantém o grafo de módulos acíclico.
 */
public interface ConnectionCounter {

  long countByIntegration(UUID integrationId);
}
