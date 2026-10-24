package com.deltafinance.wealth.api;

import java.util.List;

/** Histórico de snapshots patrimoniais. */
public record WealthSnapshotListResponse(List<WealthSnapshotResponse> items) {}
