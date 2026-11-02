package com.deltafinance.shared.recalculation;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Agrupa recálculos de cartão durante uma importação.
 *
 * <p>O importador abre um lote, os eventos de transação apenas marcam o cartão como sujo e, ao
 * fechar o lote, o conjunto é devolvido para um único recálculo por cartão. Sem lote ativo, o
 * comportamento é o normal (recálculo imediato).
 */
public final class RecalculationBatch {

  private static final ThreadLocal<State> STATE = ThreadLocal.withInitial(State::new);

  private RecalculationBatch() {}

  public static void begin() {
    STATE.get().depth++;
  }

  public static boolean isActive() {
    return STATE.get().depth > 0;
  }

  public static void markDirty(UUID cardAccountId) {
    if (cardAccountId != null) {
      STATE.get().dirty.add(cardAccountId);
    }
  }

  /**
   * Fecha o lote atual. Devolve os cartões sujos apenas quando o último nível é fechado; em lotes
   * aninhados devolve vazio e mantém a marcação para o nível externo.
   */
  public static Set<UUID> end() {
    State state = STATE.get();
    if (state.depth == 0) {
      return Set.of();
    }
    state.depth--;
    if (state.depth > 0) {
      return Set.of();
    }
    Set<UUID> dirty = Set.copyOf(state.dirty);
    state.dirty.clear();
    STATE.remove();
    return dirty;
  }

  private static final class State {
    private int depth;
    private final Set<UUID> dirty = new HashSet<>();
  }
}
