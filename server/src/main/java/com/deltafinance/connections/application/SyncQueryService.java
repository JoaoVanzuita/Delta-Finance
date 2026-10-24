package com.deltafinance.connections.application;

import com.deltafinance.shared.errors.ValidationException;
import com.deltafinance.shared.pagination.CursorPage;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** Consulta paginada do histórico de sincronizações, sempre escopada pelo usuário. */
@Service
public class SyncQueryService {

  private static final String SELECT =
      """
      SELECT id, integration_id, connection_id, status, started_at, finished_at,
             read_count, created_count, updated_count, skipped_count, failed_count,
             duration_ms, error_code
        FROM sync_run
      """;

  private final JdbcTemplate jdbcTemplate;

  public SyncQueryService(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public CursorPage<SyncRunView> runs(UUID userId, String cursor, int limit) {
    int pageSize = Math.min(100, Math.max(1, limit));
    Cursor decoded = decode(cursor);
    List<SyncRunView> rows =
        decoded == null
            ? jdbcTemplate.query(
                SELECT + " WHERE user_id = ? ORDER BY started_at DESC, id DESC LIMIT ?",
                (rs, rowNum) -> toView(rs),
                userId,
                pageSize + 1)
            : jdbcTemplate.query(
                SELECT
                    + """
                       WHERE user_id = ?
                         AND (started_at < ? OR (started_at = ? AND id < ?))
                       ORDER BY started_at DESC, id DESC
                       LIMIT ?
                      """,
                (rs, rowNum) -> toView(rs),
                userId,
                Timestamp.from(decoded.startedAt()),
                Timestamp.from(decoded.startedAt()),
                decoded.id(),
                pageSize + 1);
    boolean hasMore = rows.size() > pageSize;
    List<SyncRunView> page = hasMore ? new ArrayList<>(rows.subList(0, pageSize)) : rows;
    String nextCursor =
        hasMore && !page.isEmpty() ? encode(page.getLast().startedAt(), page.getLast().id()) : null;
    return hasMore ? CursorPage.of(page, nextCursor) : CursorPage.last(page);
  }

  /**
   * Sincronização bem-sucedida mais recente entre as conexões informadas.
   *
   * <p>{@code SUCCESS} e {@code EMPTY} contam como bem-sucedidas; falhas e execuções parciais não.
   * Retorna {@code null} quando nenhuma conexão se aplica.
   */
  public Instant lastSuccessfulSyncAt(UUID userId, Collection<UUID> connectionIds) {
    if (connectionIds == null || connectionIds.isEmpty()) {
      return null;
    }
    String placeholders =
        connectionIds.stream().map(ignored -> "?").collect(Collectors.joining(","));
    List<Object> arguments = new ArrayList<>();
    arguments.add(userId);
    arguments.addAll(connectionIds);
    Timestamp result =
        jdbcTemplate.queryForObject(
            """
            SELECT max(coalesce(finished_at, started_at))
              FROM sync_run
             WHERE user_id = ?
               AND status IN ('SUCCESS', 'EMPTY')
               AND connection_id IN (%s)
            """
                .formatted(placeholders),
            Timestamp.class,
            arguments.toArray());
    return result == null ? null : result.toInstant();
  }

  private static SyncRunView toView(ResultSet rs) throws SQLException {
    Timestamp finishedAt = rs.getTimestamp("finished_at");
    return new SyncRunView(
        rs.getObject("id", UUID.class),
        rs.getObject("integration_id", UUID.class),
        rs.getObject("connection_id", UUID.class),
        rs.getString("status"),
        rs.getTimestamp("started_at").toInstant(),
        finishedAt == null ? null : finishedAt.toInstant(),
        rs.getInt("read_count"),
        rs.getInt("created_count"),
        rs.getInt("updated_count"),
        rs.getInt("skipped_count"),
        rs.getInt("failed_count"),
        rs.getObject("duration_ms", Long.class),
        rs.getString("error_code"));
  }

  private static String encode(Instant startedAt, UUID id) {
    String raw = startedAt.toString() + "|" + id;
    return Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
  }

  private static Cursor decode(String cursor) {
    if (cursor == null || cursor.isBlank()) {
      return null;
    }
    try {
      String raw = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
      int separator = raw.indexOf('|');
      if (separator <= 0) {
        throw new IllegalArgumentException("cursor sem separador");
      }
      return new Cursor(
          Instant.parse(raw.substring(0, separator)),
          UUID.fromString(raw.substring(separator + 1)));
    } catch (RuntimeException exception) {
      throw new ValidationException("cursor", "Cursor inválido.");
    }
  }

  private record Cursor(Instant startedAt, UUID id) {}
}
