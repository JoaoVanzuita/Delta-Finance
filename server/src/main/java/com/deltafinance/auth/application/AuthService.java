package com.deltafinance.auth.application;

import com.deltafinance.auth.domain.AppUser;
import com.deltafinance.auth.domain.AppUserRepository;
import com.deltafinance.auth.domain.PasswordPolicy;
import com.deltafinance.auth.domain.PasswordPolicyException;
import com.deltafinance.auth.domain.Usernames;
import com.deltafinance.security.SessionAttributes;
import com.deltafinance.security.UserPrincipal;
import com.deltafinance.shared.audit.AuditEventType;
import com.deltafinance.shared.audit.AuditService;
import com.deltafinance.shared.errors.ConflictException;
import com.deltafinance.shared.errors.ErrorCode;
import com.deltafinance.shared.errors.InvalidCredentialsException;
import com.deltafinance.shared.errors.TooManyRequestsException;
import com.deltafinance.shared.errors.UnauthenticatedException;
import com.deltafinance.shared.errors.ValidationException;
import com.deltafinance.shared.time.Clock;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.logout.CookieClearingLogoutHandler;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Service;

/**
 * Casos de uso de identidade: cadastro, login, logout, consulta e troca de senha.
 *
 * <p>Mensagens de login são genéricas e o custo de verificação de senha é equalizado para usuário
 * inexistente, reduzindo enumeração por tempo de resposta.
 */
@Service
public class AuthService {

  private static final String ROLE_USER = "ROLE_USER";
  private static final String CSRF_COOKIE = "XSRF-TOKEN";

  private final AppUserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  private final PasswordPolicy passwordPolicy;
  private final LoginAttemptService loginAttemptService;
  private final AuditService auditService;
  private final SecurityContextRepository securityContextRepository;
  private final FindByIndexNameSessionRepository<? extends Session> sessionRepository;
  private final Clock clock;
  private final String dummyPasswordHash;

  public AuthService(
      AppUserRepository userRepository,
      PasswordEncoder passwordEncoder,
      PasswordPolicy passwordPolicy,
      LoginAttemptService loginAttemptService,
      AuditService auditService,
      SecurityContextRepository securityContextRepository,
      FindByIndexNameSessionRepository<? extends Session> sessionRepository,
      Clock clock) {
    this.userRepository = userRepository;
    this.passwordEncoder = passwordEncoder;
    this.passwordPolicy = passwordPolicy;
    this.loginAttemptService = loginAttemptService;
    this.auditService = auditService;
    this.securityContextRepository = securityContextRepository;
    this.sessionRepository = sessionRepository;
    this.clock = clock;
    this.dummyPasswordHash =
        passwordEncoder.encode("delta-finance-dummy-" + HexFormat.of().formatHex(randomBytes(24)));
  }

  public UserView register(RegisterCommand command) {
    Objects.requireNonNull(command, "command");
    if (!Usernames.isValid(command.username())) {
      throw new ValidationException(
          "username", "O nome de usuário deve ter entre 3 e 40 caracteres.");
    }
    String normalized = Usernames.normalize(command.username());
    String display = Usernames.display(command.username());
    validatePassword(normalized, command.password());

    if (userRepository.existsByUsernameNormalized(normalized)) {
      throw new ConflictException(ErrorCode.USERNAME_TAKEN, "Nome de usuário indisponível.");
    }

    AppUser user;
    try {
      user =
          userRepository.save(
              new AppUser(normalized, display, passwordEncoder.encode(command.password())));
    } catch (DataIntegrityViolationException exception) {
      // Corrida entre dois cadastros simultâneos: a única restrição é o username.
      throw new ConflictException(ErrorCode.USERNAME_TAKEN, "Nome de usuário indisponível.");
    }

    auditService.record(user.getId(), AuditEventType.USER_REGISTERED, "app_user", user.getId());
    return toView(user);
  }

  public UserView login(
      LoginCommand command, HttpServletRequest request, HttpServletResponse response) {
    Objects.requireNonNull(command, "command");
    String normalized = Usernames.normalize(command.username());
    String ipAddress = request.getRemoteAddr();

    LoginAttemptService.Decision decision = loginAttemptService.check(normalized, ipAddress);
    if (decision.blocked()) {
      auditService.record(
          null, AuditEventType.LOGIN_BLOCKED, "app_user", null, Map.of("reason", "RATE_LIMITED"));
      throw new TooManyRequestsException(
          "Muitas tentativas de login. Aguarde antes de tentar novamente.",
          decision.retryAfter().toSeconds());
    }

    var user = userRepository.findByUsernameNormalized(normalized);
    String storedHash = user.map(AppUser::getPasswordHash).orElse(dummyPasswordHash);
    boolean passwordMatches = passwordEncoder.matches(command.password(), storedHash);
    boolean canAuthenticate = user.isPresent() && user.get().isActive() && passwordMatches;

    if (!canAuthenticate) {
      loginAttemptService.recordFailure(normalized, ipAddress);
      auditService.record(
          user.map(AppUser::getId).orElse(null),
          AuditEventType.LOGIN_FAILURE,
          "app_user",
          user.map(AppUser::getId).orElse(null),
          Map.of("reason", "BAD_CREDENTIALS"));
      throw new InvalidCredentialsException();
    }

    AppUser authenticated = user.orElseThrow();
    loginAttemptService.recordSuccess(normalized, ipAddress);
    establishSession(authenticated, request, response);
    auditService.record(
        authenticated.getId(), AuditEventType.LOGIN_SUCCESS, "app_user", authenticated.getId());
    return toView(authenticated);
  }

  public void logout(HttpServletRequest request, HttpServletResponse response) {
    var principal = currentPrincipal();
    if (principal != null) {
      auditService.record(principal.id(), AuditEventType.LOGOUT, "app_user", principal.id());
    }
    new SecurityContextLogoutHandler().logout(request, response, null);
    new CookieClearingLogoutHandler(CSRF_COOKIE).logout(request, response, null);
  }

  public UserView me(UUID userId) {
    AppUser user =
        userRepository
            .findById(userId)
            .filter(AppUser::isActive)
            .orElseThrow(UnauthenticatedException::new);
    return toView(user);
  }

  public void changePassword(UUID userId, PasswordChangeCommand command, String currentSessionId) {
    AppUser user =
        userRepository
            .findById(userId)
            .filter(AppUser::isActive)
            .orElseThrow(UnauthenticatedException::new);

    if (!passwordEncoder.matches(command.currentPassword(), user.getPasswordHash())) {
      throw new ValidationException("currentPassword", "Senha atual incorreta.");
    }
    validatePassword(user.getUsernameNormalized(), command.newPassword());
    if (passwordEncoder.matches(command.newPassword(), user.getPasswordHash())) {
      throw new ValidationException(
          "newPassword", "A nova senha deve ser diferente da senha atual.");
    }

    user.changePasswordHash(passwordEncoder.encode(command.newPassword()));
    userRepository.save(user);
    revokeOtherSessions(user.getUsernameNormalized(), currentSessionId);
    auditService.record(user.getId(), AuditEventType.PASSWORD_CHANGED, "app_user", user.getId());
  }

  private void establishSession(
      AppUser user, HttpServletRequest request, HttpServletResponse response) {
    HttpSession existing = request.getSession(false);
    if (existing != null) {
      // Prevenção de fixação de sessão antes de gravar o contexto autenticado.
      request.changeSessionId();
    }
    HttpSession session = request.getSession(true);
    session.setAttribute(SessionAttributes.STARTED_AT, clock.now());

    UserPrincipal principal =
        new UserPrincipal(user.getId(), user.getUsernameNormalized(), user.getUsernameDisplay());
    var authentication =
        UsernamePasswordAuthenticationToken.authenticated(
            principal, null, java.util.List.of(new SimpleGrantedAuthority(ROLE_USER)));

    SecurityContext context = SecurityContextHolder.createEmptyContext();
    context.setAuthentication(authentication);
    SecurityContextHolder.setContext(context);
    securityContextRepository.saveContext(context, request, response);
  }

  private void revokeOtherSessions(String usernameNormalized, String currentSessionId) {
    Map<String, ? extends Session> sessions =
        sessionRepository.findByPrincipalName(usernameNormalized);
    sessions.keySet().stream()
        .filter(sessionId -> !sessionId.equals(currentSessionId))
        .forEach(sessionRepository::deleteById);
  }

  private void validatePassword(String usernameNormalized, String password) {
    try {
      passwordPolicy.validate(usernameNormalized, password);
    } catch (PasswordPolicyException exception) {
      throw new ValidationException("password", PasswordPolicy.describe(exception.reason()));
    }
  }

  private UserPrincipal currentPrincipal() {
    var authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null
        || !(authentication.getPrincipal() instanceof UserPrincipal principal)) {
      return null;
    }
    return principal;
  }

  private static UserView toView(AppUser user) {
    return new UserView(user.getId(), user.getUsernameDisplay(), user.getCreatedAt());
  }

  private static byte[] randomBytes(int length) {
    byte[] bytes = new byte[length];
    new SecureRandom().nextBytes(bytes);
    return bytes;
  }
}
