package com.deltafinance.shared.config;

import com.deltafinance.shared.money.MoneyJsonModule;
import com.deltafinance.shared.time.Clock;
import com.deltafinance.shared.time.SystemClock;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Beans transversais (tempo, serialização) usados por todos os módulos. */
@Configuration
public class SharedConfig {

  @Bean
  Clock clock() {
    return new SystemClock();
  }

  @Bean
  JsonMapperBuilderCustomizer moneyJsonMapperCustomizer() {
    return builder -> builder.addModule(new MoneyJsonModule());
  }
}
