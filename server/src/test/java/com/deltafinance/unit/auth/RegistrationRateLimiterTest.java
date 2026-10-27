package com.deltafinance.unit.auth;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.deltafinance.auth.application.RegistrationRateLimiter;
import com.deltafinance.shared.crypto.IndexHmacService;
import com.deltafinance.shared.errors.TooManyRequestsException;
import com.deltafinance.shared.time.Clock;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

/** Janela e isolamento do limite de cadastro por IP (C03). */
class RegistrationRateLimiterTest {

  private static final Instant START = Instant.parse("2026-10-25T12:00:00Z");

  @Test
  void bloqueiaRajadaEliberaAposAJanela() {
    MutableClock clock = new MutableClock(START);
    RegistrationRateLimiter limiter =
        new RegistrationRateLimiter(clock, hmac(), 2, Duration.ofHours(1));

    limiter.checkAndRecord("203.0.113.1");
    limiter.checkAndRecord("203.0.113.1");
    assertThatThrownBy(() -> limiter.checkAndRecord("203.0.113.1"))
        .isInstanceOf(TooManyRequestsException.class)
        .satisfies(
            error ->
                org.assertj.core.api.Assertions.assertThat(
                        ((TooManyRequestsException) error).retryAfterSeconds())
                    .isBetween(1L, 3600L));

    clock.advance(Duration.ofHours(1).plusSeconds(1));
    assertThatCode(() -> limiter.checkAndRecord("203.0.113.1")).doesNotThrowAnyException();
  }

  @Test
  void outroIpNaoHerdamOLimite() {
    MutableClock clock = new MutableClock(START);
    RegistrationRateLimiter limiter =
        new RegistrationRateLimiter(clock, hmac(), 1, Duration.ofHours(1));

    limiter.checkAndRecord("203.0.113.2");
    assertThatThrownBy(() -> limiter.checkAndRecord("203.0.113.2"))
        .isInstanceOf(TooManyRequestsException.class);
    assertThatCode(() -> limiter.checkAndRecord("203.0.113.3")).doesNotThrowAnyException();
  }

  private static IndexHmacService hmac() {
    IndexHmacService hmac = mock(IndexHmacService.class);
    when(hmac.ipHash(anyString()))
        .thenAnswer(
            invocation -> invocation.getArgument(0, String.class).getBytes(StandardCharsets.UTF_8));
    return hmac;
  }

  private static final class MutableClock implements Clock {
    private Instant now;

    private MutableClock(Instant now) {
      this.now = now;
    }

    private void advance(Duration duration) {
      now = now.plus(duration);
    }

    @Override
    public Instant now() {
      return now;
    }

    @Override
    public ZoneId zone() {
      return ZoneId.of("America/Sao_Paulo");
    }
  }
}
