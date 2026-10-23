package com.deltafinance.shared.audit;

import com.deltafinance.shared.time.Clock;
import java.sql.Timestamp;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

/**
 * Registro de auditoria sanitizado.
 *
 * <p>Grava apenas IDs, códigos e contadores. Nunca recebe descrições, valores, Client Secret, API
 * Key, senha ou corpo de requisição. O {@code user_id} é nulo em tentativas de login de usuário
 * inexistente e passa a nulo (por FK) quando a conta é removida.
 */
@Service
public class AuditService {

  private static final String INSERT =
      """
      INSERT INTO audit_event (id, user_id, event_type, target_type, target_id, metadata, created_at)
      VALUES (?, ?, ?, ?, ?, ?::jsonb, ?)
      """;

  private final JdbcTemplate jdbcTemplate;
  private final Clock clock;

  public AuditService(JdbcTemplate jdbcTemplate, Clock clock) {
    this.jdbcTemplate = jdbcTemplate;
    this.clock = clock;
  }

  public void record(UUID userId, AuditEventType eventType) {
    record(userId, eventType, null, null, Map.of());
  }

  public void record(UUID userId, AuditEventType eventType, String targetType, UUID targetId) {
    record(userId, eventType, targetType, targetId, Map.of());
  }

  public void record(
      UUID userId,
      AuditEventType eventType,
      String targetType,
      UUID targetId,
      Map<String, ?> metadata) {
    Objects.requireNonNull(eventType, "eventType");
    Map<String, ?> safeMetadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    String json;
    try {
      json = JsonMapper.shared().writeValueAsString(safeMetadata);
    } catch (RuntimeException exception) {
      // Metadados são auxiliares: nunca deixe a falha de serialização expor conteúdo.
      json = "{}";
    }
    jdbcTemplate.update(
        INSERT,
        UUID.randomUUID(),
        userId,
        eventType.name(),
        targetType,
        targetId,
        json,
        Timestamp.from(clock.now()));
  }
}
