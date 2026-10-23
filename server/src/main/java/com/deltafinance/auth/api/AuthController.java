package com.deltafinance.auth.api;

import com.deltafinance.auth.application.AuthService;
import com.deltafinance.auth.application.LoginCommand;
import com.deltafinance.auth.application.PasswordChangeCommand;
import com.deltafinance.auth.application.RegisterCommand;
import com.deltafinance.security.AuthenticatedUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Endpoints de identidade; a sessão é o único vínculo de autorização. */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

  private final AuthService authService;
  private final AuthenticatedUser authenticatedUser;

  public AuthController(AuthService authService, AuthenticatedUser authenticatedUser) {
    this.authService = authService;
    this.authenticatedUser = authenticatedUser;
  }

  /** Garante o cookie CSRF. O valor é escrito pelo filtro do Spring Security; o corpo é vazio. */
  @GetMapping("/csrf")
  public ResponseEntity<Void> csrf() {
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/register")
  @ResponseStatus(HttpStatus.CREATED)
  public UserResponse register(@Valid @RequestBody RegisterRequest request) {
    return UserResponse.from(
        authService.register(new RegisterCommand(request.username(), request.password())));
  }

  @PostMapping("/login")
  public UserResponse login(
      @Valid @RequestBody LoginRequest request,
      HttpServletRequest httpRequest,
      HttpServletResponse httpResponse) {
    return UserResponse.from(
        authService.login(
            new LoginCommand(request.username(), request.password()), httpRequest, httpResponse));
  }

  @PostMapping("/logout")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void logout(HttpServletRequest request, HttpServletResponse response) {
    authService.logout(request, response);
  }

  @GetMapping("/me")
  public UserResponse me() {
    return UserResponse.from(authService.me(authenticatedUser.requireId()));
  }

  @PutMapping("/password")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void changePassword(
      @Valid @RequestBody PasswordChangeRequest request, HttpServletRequest httpRequest) {
    HttpSession session = httpRequest.getSession(false);
    authService.changePassword(
        authenticatedUser.requireId(),
        new PasswordChangeCommand(request.currentPassword(), request.newPassword()),
        session == null ? null : session.getId());
  }
}
