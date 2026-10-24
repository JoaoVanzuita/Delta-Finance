package com.deltafinance.reporting.application;

import java.time.LocalDate;

/** Bucket temporal com intervalo {@code [start, end)} em America/Sao_Paulo. */
public record DashboardBucketView(
    String label, LocalDate start, LocalDate end, String income, String expenses, String result) {}
