package com.deltafinance.integrations.domain;

/** Estado de processamento de um evento de webhook. */
public enum WebhookEventStatus {
  RECEIVED,
  PROCESSED,
  FAILED,
  IGNORED
}
