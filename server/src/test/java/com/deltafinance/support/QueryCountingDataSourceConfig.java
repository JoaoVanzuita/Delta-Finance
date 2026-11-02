package com.deltafinance.support;

import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;
import net.ttddyy.dsproxy.support.ProxyDataSourceBuilder;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** DataSource de teste que conta consultas para os testes de orçamento de query. */
@TestConfiguration(proxyBeanMethods = false)
public class QueryCountingDataSourceConfig {

  @Bean
  @Primary
  DataSource queryCountingDataSource(DataSourceProperties properties) {
    HikariDataSource real =
        properties.initializeDataSourceBuilder().type(HikariDataSource.class).build();
    real.setMaximumPoolSize(2);
    real.setMinimumIdle(1);
    return ProxyDataSourceBuilder.create(real).name("query-budget").countQuery().build();
  }
}
