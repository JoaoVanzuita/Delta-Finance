package com.deltafinance.connections.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Habilita o scheduler e as propriedades de sincronização.
 *
 * <p>O shutdown gracioso é garantido pela política de ciclo de vida do Spring ({@code
 * spring.task.scheduling.shutdown.await-termination}) e pela flag interna do {@code SyncScheduler}.
 */
@Configuration
@EnableScheduling
@EnableConfigurationProperties(SyncProperties.class)
public class SyncConfig {}
