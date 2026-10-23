package com.deltafinance.shared.time;

import java.time.Instant;
import java.time.ZoneId;
import java.util.Objects;

/** Implementação padrão: relógio do sistema no fuso operacional do MVP. */
public final class SystemClock implements Clock {

  public static final ZoneId DEFAULT_ZONE = ZoneId.of("America/Sao_Paulo");

  private final ZoneId zone;

  public SystemClock() {
    this(DEFAULT_ZONE);
  }

  public SystemClock(ZoneId zone) {
    this.zone = Objects.requireNonNull(zone, "zone");
  }

  @Override
  public Instant now() {
    return Instant.now();
  }

  @Override
  public ZoneId zone() {
    return zone;
  }
}
