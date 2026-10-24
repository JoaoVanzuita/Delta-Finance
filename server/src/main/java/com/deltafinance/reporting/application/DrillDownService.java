package com.deltafinance.reporting.application;

import com.deltafinance.shared.errors.ValidationException;
import com.deltafinance.shared.pagination.CursorPage;
import com.deltafinance.shared.pagination.PageRequest;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Drill-down dos totais do dashboard.
 *
 * <p>Usa exatamente a mesma projeção de {@link ReportingService}, garantindo que a soma das
 * entradas corresponda ao indicador que as originou. Entradas são ordenadas por data e
 * identificador locais, com cursor opaco.
 */
@Service
public class DrillDownService {

  private final ReportingService reportingService;

  public DrillDownService(ReportingService reportingService) {
    this.reportingService = reportingService;
  }

  public CursorPage<ReportEntryView> entries(UUID userId, ReportQuery query, PageRequest page) {
    List<ReportEntry> entries = new ArrayList<>(reportingService.entries(userId, query));
    entries.sort(
        Comparator.comparing(ReportEntry::occurredOn)
            .reversed()
            .thenComparing(ReportEntry::id, Comparator.reverseOrder()));

    Cursor cursor = decode(page.cursor());
    List<ReportEntry> filtered = entries;
    if (cursor != null) {
      filtered =
          entries.stream()
              .filter(
                  entry ->
                      entry.occurredOn().isBefore(cursor.occurredOn())
                          || (entry.occurredOn().equals(cursor.occurredOn())
                              && entry.id().compareTo(cursor.id()) < 0))
              .toList();
    }

    boolean hasMore = filtered.size() > page.limit();
    List<ReportEntry> slice = hasMore ? filtered.subList(0, page.limit()) : filtered;
    List<ReportEntryView> views = slice.stream().map(ReportEntryView::from).toList();
    String nextCursor =
        hasMore && !slice.isEmpty()
            ? encode(slice.getLast().occurredOn(), slice.getLast().id())
            : null;
    return hasMore ? CursorPage.of(views, nextCursor) : CursorPage.last(views);
  }

  private static String encode(LocalDate occurredOn, UUID id) {
    String raw = occurredOn + "|" + id;
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
          LocalDate.parse(raw.substring(0, separator)),
          UUID.fromString(raw.substring(separator + 1)));
    } catch (RuntimeException exception) {
      throw new ValidationException("cursor", "Cursor inválido.");
    }
  }

  private record Cursor(LocalDate occurredOn, UUID id) {}
}
