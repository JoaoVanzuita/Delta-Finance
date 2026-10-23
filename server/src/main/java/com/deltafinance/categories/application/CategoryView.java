package com.deltafinance.categories.application;

import java.util.UUID;

/** Visão pública de categoria com nome decifrado. */
public record CategoryView(UUID id, String name, String kind, boolean predefined, boolean active) {}
