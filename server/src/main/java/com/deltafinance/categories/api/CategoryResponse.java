package com.deltafinance.categories.api;

import com.deltafinance.categories.application.CategoryView;
import java.util.UUID;

public record CategoryResponse(
    UUID id, String name, String kind, boolean predefined, boolean active) {

  public static CategoryResponse from(CategoryView view) {
    return new CategoryResponse(
        view.id(), view.name(), view.kind(), view.predefined(), view.active());
  }
}
