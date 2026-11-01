package com.deltafinance.accounts.application;

import java.util.UUID;

/**
 * Porta para expor o nome de exibição da conexão sem criar dependência do módulo de contas para
 * connections.
 *
 * <p>No MeuPluggy o nome da instituição do item é o agregador ("MeuPluggy"); o banco real fica no
 * nome da conexão. A UI deve preferir esse nome ao exibir e agrupar contas/cartões.
 */
public interface ConnectionNamePort {

  /** Nome de exibição da conexão ou {@code null} quando ela não existe ou não tem nome. */
  String displayNameOf(UUID userId, UUID connectionId);
}
