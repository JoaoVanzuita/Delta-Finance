package com.deltafinance.integrations.api;

import com.deltafinance.integrations.application.ValidationView;

public record ValidationResponse(boolean valid, String message, boolean itemValidated) {

  public static ValidationResponse from(ValidationView view) {
    return new ValidationResponse(view.valid(), view.message(), view.itemValidated());
  }
}
