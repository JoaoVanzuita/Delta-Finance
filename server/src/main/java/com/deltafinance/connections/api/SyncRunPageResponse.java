package com.deltafinance.connections.api;

import com.deltafinance.connections.application.SyncRunView;
import java.util.List;

/** Página de execuções de sincronização. */
public record SyncRunPageResponse(List<SyncRunView> items, String nextCursor, boolean hasMore) {}
