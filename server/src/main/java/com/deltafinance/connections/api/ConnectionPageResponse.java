package com.deltafinance.connections.api;

import com.deltafinance.connections.application.ConnectionView;
import java.util.List;

public record ConnectionPageResponse(
    List<ConnectionView> items, String nextCursor, boolean hasMore) {

  public static ConnectionPageResponse last(List<ConnectionView> items) {
    return new ConnectionPageResponse(items, null, false);
  }
}
