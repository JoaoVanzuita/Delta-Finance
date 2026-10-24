package com.deltafinance.privacy.api;

import com.deltafinance.privacy.application.AccountDeletionService;
import com.deltafinance.privacy.application.DisconnectService;
import com.deltafinance.privacy.application.ExportService;
import com.deltafinance.security.AuthenticatedUser;
import com.deltafinance.shared.time.Clock;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

/** Exportação, desconexão e exclusão da conta. Todas as operações usam o usuário da sessão. */
@RestController
@RequestMapping("/api/v1")
public class PrivacyController {

  private final ExportService exportService;
  private final DisconnectService disconnectService;
  private final AccountDeletionService accountDeletionService;
  private final AuthenticatedUser authenticatedUser;
  private final Clock clock;

  public PrivacyController(
      ExportService exportService,
      DisconnectService disconnectService,
      AccountDeletionService accountDeletionService,
      AuthenticatedUser authenticatedUser,
      Clock clock) {
    this.exportService = exportService;
    this.disconnectService = disconnectService;
    this.accountDeletionService = accountDeletionService;
    this.authenticatedUser = authenticatedUser;
    this.clock = clock;
  }

  /** Baixa um ZIP em streaming com JSON Lines e CSV, sem segredos. */
  @GetMapping("/me/export")
  public ResponseEntity<StreamingResponseBody> export() {
    UUID userId = authenticatedUser.requireId();
    String filename = "delta-finance-export-" + clock.today() + ".zip";
    StreamingResponseBody body = output -> exportService.export(userId, output);
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
        .contentType(MediaType.parseMediaType("application/zip"))
        .body(body);
  }

  /** Exclusão síncrona com confirmação; retorna 202 e encerra as sessões. */
  @PostMapping("/me/delete")
  @ResponseStatus(HttpStatus.ACCEPTED)
  public void delete(
      @Valid @RequestBody DeleteAccountRequest request,
      HttpServletRequest httpRequest,
      HttpServletResponse httpResponse) {
    accountDeletionService.delete(authenticatedUser.requireId(), request);
    new SecurityContextLogoutHandler().logout(httpRequest, httpResponse, null);
  }

  /** Desconecta o provedor; por padrão preserva os dados importados. */
  @DeleteMapping("/integrations/pluggy")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void disconnect(@RequestParam(defaultValue = "false") boolean deleteImportedData) {
    disconnectService.disconnect(authenticatedUser.requireId(), deleteImportedData);
  }
}
