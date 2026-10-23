package com.deltafinance.shared.config;

import com.deltafinance.shared.crypto.KeyRing;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.actuate.info.InfoContributor;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.boot.micrometer.metrics.autoconfigure.MeterRegistryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Observabilidade transversal.
 *
 * <p>Readiness inclui banco e key ring; o health do key ring expõe apenas as versões ativas, nunca
 * o material das chaves.
 */
@Configuration
public class ObservabilityConfig {

  @Bean
  HealthIndicator keyRingHealthIndicator(KeyRing keyRing) {
    return () ->
        Health.up()
            .withDetail("activeDataKeyVersion", keyRing.activeDataVersion())
            .withDetail("activeIndexKeyVersion", keyRing.activeIndexVersion())
            .build();
  }

  @Bean
  MeterRegistryCustomizer<MeterRegistry> commonMeterTags() {
    return registry -> registry.config().commonTags("application", "delta-finance");
  }

  @Bean
  InfoContributor applicationInfoContributor() {
    return builder -> builder.withDetail("app", "Delta Finance");
  }
}
