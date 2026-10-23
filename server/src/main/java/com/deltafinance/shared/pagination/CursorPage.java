package com.deltafinance.shared.pagination;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * Página por cursor.
 *
 * @param items elementos da página atual
 * @param nextCursor cursor opaco para a próxima página, ou {@code null} na última
 * @param hasMore indica se existe outra página
 */
public record CursorPage<T>(List<T> items, String nextCursor, boolean hasMore) {

  public CursorPage {
    items = List.copyOf(Objects.requireNonNull(items, "items"));
    if (hasMore && (nextCursor == null || nextCursor.isBlank())) {
      throw new IllegalArgumentException("hasMore exige nextCursor");
    }
    if (!hasMore && nextCursor != null) {
      throw new IllegalArgumentException("última página não deve ter nextCursor");
    }
  }

  public static <T> CursorPage<T> empty() {
    return new CursorPage<>(List.of(), null, false);
  }

  public static <T> CursorPage<T> last(List<T> items) {
    return new CursorPage<>(items, null, false);
  }

  public static <T> CursorPage<T> of(List<T> items, String nextCursor) {
    return new CursorPage<>(items, nextCursor, true);
  }

  public <R> CursorPage<R> map(Function<? super T, ? extends R> mapper) {
    Objects.requireNonNull(mapper, "mapper");
    return new CursorPage<>(items.stream().<R>map(mapper).toList(), nextCursor, hasMore);
  }

  public int size() {
    return items.size();
  }
}
