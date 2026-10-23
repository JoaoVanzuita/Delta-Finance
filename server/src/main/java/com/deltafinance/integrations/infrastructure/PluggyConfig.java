package com.deltafinance.integrations.infrastructure;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/** Cliente HTTP do provedor, com timeouts explícitos. */
@Configuration
@EnableConfigurationProperties(PluggyProperties.class)
public class PluggyConfig {

  @Bean
  RestClient pluggyRestClient(PluggyProperties properties) {
    SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
    requestFactory.setConnectTimeout(10_000);
    requestFactory.setReadTimeout((int) properties.getRequestTimeout().toMillis());
    return RestClient.builder()
        .baseUrl(properties.getBaseUrl())
        .requestFactory(requestFactory)
        .build();
  }
}
