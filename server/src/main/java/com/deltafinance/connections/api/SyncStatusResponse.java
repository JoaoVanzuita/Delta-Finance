package com.deltafinance.connections.api;

import com.deltafinance.integrations.api.IntegrationResponse;
import java.util.List;

/** Estado consolidado das integrações, conexões e jobs pendentes. */
public record SyncStatusResponse(
    List<IntegrationResponse> integrations,
    List<ConnectionResponse> connections,
    long pendingJobs) {}
