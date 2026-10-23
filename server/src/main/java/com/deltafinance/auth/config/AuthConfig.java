package com.deltafinance.auth.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Habilita as propriedades do módulo de autenticação. */
@Configuration
@EnableConfigurationProperties(LoginProtectionProperties.class)
public class AuthConfig {}
