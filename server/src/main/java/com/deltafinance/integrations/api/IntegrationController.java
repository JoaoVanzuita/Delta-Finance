package com.deltafinance.integrations.api;

import com.deltafinance.integrations.application.IntegrationService;
import com.deltafinance.security.AuthenticatedUser;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Configuração pessoal Meu Pluggy; todas as operações usam o usuário da sessão. */
@RestController
@RequestMapping("/api/v1/integrations/pluggy")
public class IntegrationController {

  private final IntegrationService integrationService;
  private final AuthenticatedUser authenticatedUser;

  public IntegrationController(
      IntegrationService integrationService, AuthenticatedUser authenticatedUser) {
    this.integrationService = integrationService;
    this.authenticatedUser = authenticatedUser;
  }

  @GetMapping
  public IntegrationResponse get() {
    return IntegrationResponse.from(integrationService.get(authenticatedUser.requireId()));
  }

  @PutMapping
  public IntegrationResponse save(@Valid @RequestBody IntegrationCredentialsRequest request) {
    return IntegrationResponse.from(
        integrationService.saveCredentials(
            authenticatedUser.requireId(), request.clientId(), request.clientSecret()));
  }

  @PostMapping("/validate")
  public ValidationResponse validate(@Valid @RequestBody IntegrationCredentialsRequest request) {
    return ValidationResponse.from(
        integrationService.validate(request.clientId(), request.clientSecret()));
  }
}
