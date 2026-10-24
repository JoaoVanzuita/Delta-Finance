package com.deltafinance.integrations.infrastructure;

/**
 * Item inexistente ou itemId malformado para a aplicação Meu Pluggy do usuário.
 *
 * <p>Não representa indisponibilidade do provedor: o provedor respondeu 400/404 porque aquele
 * itemId não existe ou não pertence à aplicação autenticada. Deve gerar ação do usuário (reconectar
 * ou corrigir o itemId), não retry automático.
 */
public class PluggyItemInvalidException extends PluggyException {

  /** Mensagem acionável exibida ao usuário; não inclui dados do provedor. */
  public static final String USER_MESSAGE =
      "itemId inválido ou não encontrado para esta integração Meu Pluggy. "
          + "Copie o UUID do item (não o da conta) em Itens Conectados, na página Demo "
          + "da sua aplicação no Dashboard Pluggy.";

  public PluggyItemInvalidException() {
    super("Item inexistente ou itemId inválido para a aplicação Meu Pluggy.");
  }
}
