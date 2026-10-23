package com.deltafinance.auth.api;

import com.deltafinance.auth.application.UserView;
import java.time.Instant;
import java.util.UUID;

/** Representação pública do usuário autenticado. */
public record UserResponse(UUID id, String username, Instant createdAt) {

  public static UserResponse from(UserView view) {
    return new UserResponse(view.id(), view.username(), view.createdAt());
  }
}
