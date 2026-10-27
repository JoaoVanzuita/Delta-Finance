package com.deltafinance.security;

import com.deltafinance.shared.crypto.CryptoContext;
import com.deltafinance.shared.crypto.CryptoService;
import com.deltafinance.shared.errors.ErrorCode;
import com.deltafinance.shared.errors.ProblemFactory;
import com.deltafinance.shared.errors.ValidationException;
import com.deltafinance.shared.time.Clock;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Idempotência HTTP para as escritas financeiras críticas.
 *
 * <p>O filtro atua apenas em transações, itens de fatura e pagamentos de fatura. Quando o cliente
 * envia {@code Idempotency-Key} nessas operações, a primeira execução é persistida com hash do
 * método/caminho/corpo e a resposta cifrada. Repetições com o mesmo corpo recebem a mesma resposta;
 * o mesmo par usuário/chave com corpo diferente retorna {@code 409}. O TTL é de 24 h.
 *
 * <p>Demais mutações ignoram o cabeçalho. Login, registro e CSRF ficam fora: autenticação tem rate
 * limit/CSRF próprios.
 */
public class IdempotencyFilter extends OncePerRequestFilter {

  private static final String HEADER = "Idempotency-Key";
  private static final String TABLE = "idempotency_key";
  private static final Duration TTL = Duration.ofHours(24);
  private static final int MIN_KEY_LENGTH = 8;
  private static final int MAX_KEY_LENGTH = 128;

  /** Operações protegidas; qualquer outra mutação ignora o cabeçalho. */
  private static final List<ProtectedOperation> PROTECTED_OPERATIONS =
      List.of(
          new ProtectedOperation("POST", "^/api/v1/transactions$"),
          new ProtectedOperation("PATCH", "^/api/v1/transactions/[^/]+$"),
          new ProtectedOperation("DELETE", "^/api/v1/transactions/[^/]+$"),
          new ProtectedOperation("POST", "^/api/v1/invoices/[^/]+/items$"),
          new ProtectedOperation("PATCH", "^/api/v1/invoice-items/[^/]+$"),
          new ProtectedOperation("DELETE", "^/api/v1/invoice-items/[^/]+$"),
          new ProtectedOperation("POST", "^/api/v1/invoices/[^/]+/payments$"));

  private final JdbcTemplate jdbcTemplate;
  private final CryptoService cryptoService;
  private final Clock clock;
  private final ProblemFactory problems;
  private final JsonMapper jsonMapper;

  public IdempotencyFilter(
      JdbcTemplate jdbcTemplate,
      CryptoService cryptoService,
      Clock clock,
      ProblemFactory problems,
      JsonMapper jsonMapper) {
    this.jdbcTemplate = jdbcTemplate;
    this.cryptoService = cryptoService;
    this.clock = clock;
    this.problems = problems;
    this.jsonMapper = jsonMapper;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    if (!isProtected(request.getMethod(), request.getRequestURI())) {
      return true;
    }
    String key = request.getHeader(HEADER);
    return key == null || key.isBlank();
  }

  /** Indica se a operação aceita replay de idempotência. */
  static boolean isProtected(String method, String path) {
    return PROTECTED_OPERATIONS.stream()
        .anyMatch(
            operation ->
                operation.method().equals(method) && operation.path().matcher(path).matches());
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    UUID userId = authenticatedUserId();
    if (userId == null) {
      filterChain.doFilter(request, response);
      return;
    }

    String key = request.getHeader(HEADER).trim();
    if (key.length() < MIN_KEY_LENGTH || key.length() > MAX_KEY_LENGTH) {
      writeValidation(
          response,
          "Idempotency-Key",
          "O cabeçalho Idempotency-Key deve ter entre 8 e 128 caracteres.",
          request.getRequestURI());
      return;
    }

    byte[] body = request.getInputStream().readAllBytes();
    CachedBodyRequest wrapped = new CachedBodyRequest(request, body);
    String requestTarget =
        request.getRequestURI()
            + (request.getQueryString() == null ? "" : "?" + request.getQueryString());
    byte[] requestHash = requestHash(request.getMethod(), requestTarget, body);
    Existing existing = find(userId, key);
    if (existing != null) {
      if (existing.expiresAt().isAfter(clock.now())) {
        replayOrConflict(response, existing, requestHash, request.getRequestURI());
        return;
      }
      jdbcTemplate.update("delete from idempotency_key where id = ?", existing.id());
    }

    UUID rowId = UUID.randomUUID();
    Instant now = clock.now();
    try {
      jdbcTemplate.update(
          "insert into idempotency_key"
              + " (id, user_id, key, method, path, request_hash, created_at, expires_at)"
              + " values (?, ?, ?, ?, ?, ?, ?, ?)",
          rowId,
          userId,
          key,
          request.getMethod(),
          request.getRequestURI(),
          requestHash,
          Timestamp.from(now),
          Timestamp.from(now.plus(TTL)));
    } catch (DuplicateKeyException exception) {
      Existing concurrent = find(userId, key);
      if (concurrent != null) {
        replayOrConflict(response, concurrent, requestHash, request.getRequestURI());
        return;
      }
      throw exception;
    }

    ContentCachingResponseWrapper responseWrapper = new ContentCachingResponseWrapper(response);
    boolean completed = false;
    try {
      filterChain.doFilter(wrapped, responseWrapper);
      completed = true;
    } finally {
      if (!completed) {
        jdbcTemplate.update("delete from idempotency_key where id = ?", rowId);
      }
    }

    int status = responseWrapper.getStatus();
    if (status >= 200 && status < 500) {
      byte[] responseBody = responseWrapper.getContentAsByteArray();
      byte[] responseBodyEnc =
          responseBody.length == 0
              ? null
              : cryptoService.encrypt(
                  new String(responseBody, StandardCharsets.UTF_8),
                  new CryptoContext(userId, TABLE, "response_body", rowId));
      jdbcTemplate.update(
          "update idempotency_key"
              + " set response_status = ?, response_body_enc = ?,"
              + " expires_at = ? where id = ?",
          status,
          responseBodyEnc,
          Timestamp.from(clock.now().plus(TTL)),
          rowId);
    } else {
      // Falhas do servidor não são definitivas: libera a chave para um novo retry.
      jdbcTemplate.update("delete from idempotency_key where id = ?", rowId);
    }
    responseWrapper.copyBodyToResponse();
  }

  private void replayOrConflict(
      HttpServletResponse response, Existing existing, byte[] requestHash, String path)
      throws IOException {
    if (!MessageDigest.isEqual(existing.requestHash(), requestHash)) {
      writeProblem(
          response,
          HttpStatus.CONFLICT,
          ErrorCode.IDEMPOTENCY_KEY_REUSED,
          "Esta Idempotency-Key já foi usada com outro conteúdo.",
          path);
      return;
    }
    if (existing.responseStatus() == null) {
      writeProblem(
          response,
          HttpStatus.CONFLICT,
          ErrorCode.IDEMPOTENCY_IN_PROGRESS,
          "A requisição com esta Idempotency-Key ainda está em processamento.",
          path);
      return;
    }
    response.setStatus(existing.responseStatus());
    if (existing.responseBodyEnc() != null) {
      String body =
          cryptoService.decryptString(
              existing.responseBodyEnc(),
              new CryptoContext(existing.userId(), TABLE, "response_body", existing.id()));
      response.setContentType(
          existing.responseStatus() >= 400 ? "application/problem+json" : "application/json");
      response.setCharacterEncoding(StandardCharsets.UTF_8.name());
      response.getOutputStream().write(body.getBytes(StandardCharsets.UTF_8));
    }
  }

  private Existing find(UUID userId, String key) {
    List<Existing> rows =
        jdbcTemplate.query(
            "select id, user_id, request_hash, response_status, response_body_enc, expires_at"
                + " from idempotency_key where user_id = ? and key = ?",
            (rs, rowNum) ->
                new Existing(
                    rs.getObject("id", UUID.class),
                    rs.getObject("user_id", UUID.class),
                    rs.getBytes("request_hash"),
                    rs.getObject("response_status", Integer.class),
                    rs.getBytes("response_body_enc"),
                    rs.getTimestamp("expires_at").toInstant()),
            userId,
            key);
    return rows.isEmpty() ? null : rows.getFirst();
  }

  private void writeValidation(
      HttpServletResponse response, String field, String message, String path) throws IOException {
    ProblemDetail problem = problems.validation(new ValidationException(field, message), path);
    response.setStatus(HttpStatus.UNPROCESSABLE_ENTITY.value());
    response.setContentType("application/problem+json");
    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
    jsonMapper.writeValue(response.getOutputStream(), problem);
  }

  private void writeProblem(
      HttpServletResponse response, HttpStatus status, String code, String detail, String path)
      throws IOException {
    response.setStatus(status.value());
    response.setContentType("application/problem+json");
    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
    jsonMapper.writeValue(response.getOutputStream(), problems.problem(status, code, detail, path));
  }

  private static byte[] requestHash(String method, String path, byte[] body) {
    MessageDigest digest = sha256Digest();
    digest.update(method.getBytes(StandardCharsets.UTF_8));
    digest.update((byte) '\n');
    digest.update(path.getBytes(StandardCharsets.UTF_8));
    digest.update((byte) '\n');
    digest.update(body);
    return digest.digest();
  }

  private static MessageDigest sha256Digest() {
    try {
      return MessageDigest.getInstance("SHA-256");
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 indisponível.", exception);
    }
  }

  private static UUID authenticatedUserId() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null
        || !authentication.isAuthenticated()
        || !(authentication.getPrincipal() instanceof UserPrincipal principal)) {
      return null;
    }
    return principal.id();
  }

  private record Existing(
      UUID id,
      UUID userId,
      byte[] requestHash,
      Integer responseStatus,
      byte[] responseBodyEnc,
      Instant expiresAt) {}

  private record ProtectedOperation(String method, Pattern path) {
    private ProtectedOperation(String method, String path) {
      this(method, Pattern.compile(path));
    }
  }

  /** Request com corpo cacheado para hash e leitura pelo controller. */
  private static final class CachedBodyRequest extends HttpServletRequestWrapper {

    private final byte[] body;

    private CachedBodyRequest(HttpServletRequest request, byte[] body) {
      super(request);
      this.body = body;
    }

    @Override
    public ServletInputStream getInputStream() {
      ByteArrayInputStream input = new ByteArrayInputStream(body);
      return new ServletInputStream() {
        @Override
        public int read() {
          return input.read();
        }

        @Override
        public int read(byte[] target, int offset, int length) {
          return input.read(target, offset, length);
        }

        @Override
        public boolean isFinished() {
          return input.available() == 0;
        }

        @Override
        public boolean isReady() {
          return true;
        }

        @Override
        public void setReadListener(ReadListener listener) {
          throw new UnsupportedOperationException("Leitura assíncrona não suportada.");
        }
      };
    }

    @Override
    public BufferedReader getReader() {
      Charset charset =
          getCharacterEncoding() == null
              ? StandardCharsets.UTF_8
              : Charset.forName(getCharacterEncoding());
      return new BufferedReader(new InputStreamReader(getInputStream(), charset));
    }

    @Override
    public int getContentLength() {
      return body.length;
    }

    @Override
    public long getContentLengthLong() {
      return body.length;
    }
  }
}
