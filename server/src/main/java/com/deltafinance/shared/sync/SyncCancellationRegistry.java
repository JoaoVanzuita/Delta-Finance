package com.deltafinance.shared.sync;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Cancelamento em memória da sincronização (ADR-0004).
 *
 * <p>O token é criado no claim do job e vinculado à thread que executa a importação. Os serviços de
 * importação chamam {@link #checkpoint()} antes de cada lote de escrita; quando o token é
 * cancelado, a tentativa para antes da próxima escrita. O registro é local à instância, que é única
 * por decisão explícita.
 */
@Component
public class SyncCancellationRegistry {

  public enum Reason {
    CANCELLED,
    TIMEOUT
  }

  /** Token de uma tentativa; também impede que um worker atrasado sobrescreva o job. */
  public static final class Token {
    private final UUID jobId;
    private volatile Reason reason;

    private Token(UUID jobId) {
      this.jobId = jobId;
    }

    public UUID jobId() {
      return jobId;
    }

    public boolean isCancelled() {
      return reason != null;
    }

    public Reason reason() {
      return reason;
    }

    private void cancel(Reason newReason) {
      if (reason == null) {
        reason = newReason;
      }
    }
  }

  private final ConcurrentHashMap<UUID, Token> active = new ConcurrentHashMap<>();
  private final ThreadLocal<Token> current = new ThreadLocal<>();

  public Token begin(UUID jobId) {
    Token token = new Token(jobId);
    active.put(jobId, token);
    return token;
  }

  public void bind(Token token) {
    current.set(token);
  }

  public void unbind() {
    current.remove();
  }

  public void finish(Token token) {
    if (token != null) {
      active.remove(token.jobId(), token);
    }
  }

  public void cancel(UUID jobId, Reason reason) {
    Token token = active.get(jobId);
    if (token != null) {
      token.cancel(reason);
    }
  }

  /** Chamado antes de cada escrita/importação; lança quando a tentativa foi cancelada. */
  public void checkpoint() {
    Token token = current.get();
    if (token != null && token.isCancelled()) {
      throw new SyncCancelledException(token.reason());
    }
  }
}
