package com.deltafinance.connections.api;

import com.deltafinance.connections.application.ConnectionView;
import com.deltafinance.integrations.application.IntegrationView;
import java.util.List;

/** Estado consolidado das integrações, conexões e jobs pendentes. */
public record SyncStatusResponse(
    List<IntegrationView> integrations, List<ConnectionView> connections, long pendingJobs) {}
