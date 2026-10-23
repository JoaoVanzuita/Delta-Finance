package com.deltafinance.connections.api;

import java.util.List;

/** Página de execuções de sincronização. */
public record SyncRunPageResponse(
    List<SyncRunResponse> items, String nextCursor, boolean hasMore) {}
