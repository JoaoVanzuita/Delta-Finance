package com.deltafinance.wealth.api;

import java.time.LocalDate;

/** Captura manual opcional; data ausente usa hoje. */
public record WealthSnapshotCaptureRequest(LocalDate referenceDate) {}
