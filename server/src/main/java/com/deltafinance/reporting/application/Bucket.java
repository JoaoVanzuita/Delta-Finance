package com.deltafinance.reporting.application;

import java.time.LocalDate;

record Bucket(String label, LocalDate start, LocalDate end) {}
