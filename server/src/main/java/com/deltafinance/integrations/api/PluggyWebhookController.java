package com.deltafinance.integrations.api;

import com.deltafinance.integrations.application.WebhookService;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Endpoint opcional de webhook.
 *
 * <p>Autentica token/segredo, registra o {@code eventId} de forma idempotente e responde 202
 * rapidamente. O provedor recebe 409 em replay. Nenhum conteúdo do payload é devolvido ou
 * persistido.
 */
@RestController
@RequestMapping("/api/v1/webhooks/pluggy")
public class PluggyWebhookController {

  private final WebhookService webhookService;
  private final JsonMapper jsonMapper;

  public PluggyWebhookController(WebhookService webhookService, JsonMapper jsonMapper) {
    this.webhookService = webhookService;
    this.jsonMapper = jsonMapper;
  }

  @PostMapping("/{webhookToken}")
  public ResponseEntity<Void> receive(
      @PathVariable String webhookToken,
      @RequestHeader(name = "X-Webhook-Secret", required = false) String secret,
      @RequestBody(required = false) String rawBody) {
    JsonNode payload = parse(rawBody);
    String eventId =
        firstNonBlank(payload.path("eventId").asString(null), payload.path("id").asString(null));
    String eventType = payload.path("event").asString(null);
    String itemId = payload.path("itemId").asString(null);
    if (itemId == null || itemId.isBlank()) {
      itemId = payload.path("item").path("id").asString(null);
    }
    var receipt = webhookService.receive(webhookToken, secret, eventId, eventType, itemId, rawBody);
    if (receipt.duplicate()) {
      return ResponseEntity.status(HttpStatus.CONFLICT).build();
    }
    return ResponseEntity.status(HttpStatus.ACCEPTED).build();
  }

  private JsonNode parse(String rawBody) {
    if (rawBody == null || rawBody.isBlank()) {
      return jsonMapper.createObjectNode();
    }
    try {
      return jsonMapper.readTree(rawBody.getBytes(StandardCharsets.UTF_8));
    } catch (RuntimeException exception) {
      return jsonMapper.createObjectNode();
    }
  }

  private static String firstNonBlank(String first, String second) {
    if (first != null && !first.isBlank()) {
      return first;
    }
    return second;
  }
}
