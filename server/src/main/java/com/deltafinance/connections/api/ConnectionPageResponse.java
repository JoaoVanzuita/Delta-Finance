package com.deltafinance.connections.api;

import java.util.List;

public record ConnectionPageResponse(
    List<ConnectionResponse> items, String nextCursor, boolean hasMore) {

  public static ConnectionPageResponse last(List<ConnectionResponse> items) {
    return new ConnectionPageResponse(items, null, false);
  }
}
