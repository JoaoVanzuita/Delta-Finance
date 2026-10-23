package com.deltafinance.shared.errors;

/** Códigos estáveis de erro expostos no campo {@code code} do ProblemDetail. */
public final class ErrorCode {

  public static final String VALIDATION_FAILED = "VALIDATION_FAILED";
  public static final String INVALID_REQUEST = "INVALID_REQUEST";
  public static final String UNAUTHENTICATED = "UNAUTHENTICATED";
  public static final String INVALID_CREDENTIALS = "INVALID_CREDENTIALS";
  public static final String ACCESS_DENIED = "ACCESS_DENIED";
  public static final String CSRF_INVALID = "CSRF_INVALID";
  public static final String RATE_LIMITED = "RATE_LIMITED";
  public static final String USERNAME_TAKEN = "USERNAME_TAKEN";
  public static final String NOT_FOUND = "NOT_FOUND";
  public static final String CONFLICT = "CONFLICT";
  public static final String INTERNAL_ERROR = "INTERNAL_ERROR";

  private ErrorCode() {}
}
