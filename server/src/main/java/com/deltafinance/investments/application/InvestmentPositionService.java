package com.deltafinance.investments.application;

import com.deltafinance.investments.api.InvestmentPositionRequest;
import com.deltafinance.investments.domain.InvestmentBroker;
import com.deltafinance.investments.domain.InvestmentPosition;
import com.deltafinance.investments.domain.InvestmentPositionRepository;
import com.deltafinance.shared.audit.AuditEventType;
import com.deltafinance.shared.audit.AuditService;
import com.deltafinance.shared.crypto.CryptoContext;
import com.deltafinance.shared.crypto.CryptoException;
import com.deltafinance.shared.crypto.CryptoService;
import com.deltafinance.shared.errors.NotFoundException;
import com.deltafinance.shared.errors.ValidationException;
import com.deltafinance.shared.money.Money;
import com.deltafinance.shared.pagination.CursorPage;
import com.deltafinance.shared.pagination.PageRequest;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

/**
 * Posições manuais de investimento.
 *
 * <p>O módulo é explicitamente manual: não importa dados, não usa cotações de mercado e não oferece
 * recomendação. O total é sempre {@code quantity × referencePrice}; quando o usuário informa apenas
 * o valor total, o preço unitário é derivado com 8 casas decimais.
 */
@Service
public class InvestmentPositionService {

  private static final String TABLE = "investment_position";
  private static final int QUANTITY_SCALE = 8;
  private static final int PRICE_SCALE = 8;

  private final InvestmentPositionRepository positionRepository;
  private final CryptoService cryptoService;
  private final AuditService auditService;

  public InvestmentPositionService(
      InvestmentPositionRepository positionRepository,
      CryptoService cryptoService,
      AuditService auditService) {
    this.positionRepository = positionRepository;
    this.cryptoService = cryptoService;
    this.auditService = auditService;
  }

  public CursorPage<InvestmentPositionView> list(UUID userId, PageRequest pageRequest) {
    Cursor cursor = decode(pageRequest.cursor());
    Pageable pageable = org.springframework.data.domain.PageRequest.of(0, pageRequest.limit() + 1);
    List<InvestmentPosition> rows =
        cursor == null
            ? positionRepository.findByUserIdOrderByReferenceDateDescIdDesc(userId, pageable)
            : positionRepository.findPageAfter(
                userId, cursor.referenceDate(), cursor.id(), pageable);

    boolean hasMore = rows.size() > pageRequest.limit();
    List<InvestmentPosition> page = hasMore ? rows.subList(0, pageRequest.limit()) : rows;
    List<InvestmentPositionView> views = page.stream().map(this::toView).toList();
    String nextCursor =
        hasMore && !page.isEmpty()
            ? encode(page.getLast().getReferenceDate(), page.getLast().getId())
            : null;
    return hasMore ? CursorPage.of(views, nextCursor) : CursorPage.last(views);
  }

  public InvestmentPositionView get(UUID userId, UUID positionId) {
    return toView(requirePosition(userId, positionId));
  }

  /** Todas as posições do usuário, para consolidação patrimonial (sem paginação). */
  public List<InvestmentPositionView> listAll(UUID userId) {
    return positionRepository.findByUserIdOrderByReferenceDateDescIdDesc(userId).stream()
        .map(this::toView)
        .toList();
  }

  public InvestmentPositionView create(UUID userId, InvestmentPositionRequest request) {
    Amounts amounts = calculate(request);
    InvestmentPosition position = new InvestmentPosition(userId, parseBroker(request.broker()));
    apply(position, request, amounts);
    positionRepository.save(position);
    auditService.record(
        userId,
        AuditEventType.INVESTMENT_POSITION_CREATED,
        TABLE,
        position.getId(),
        Map.of("broker", position.getBroker().name()));
    return toView(position);
  }

  public InvestmentPositionView update(
      UUID userId, UUID positionId, InvestmentPositionRequest request) {
    InvestmentPosition position = requirePosition(userId, positionId);
    Amounts amounts = calculate(request);
    apply(position, request, amounts);
    positionRepository.save(position);
    auditService.record(
        userId,
        AuditEventType.INVESTMENT_POSITION_UPDATED,
        TABLE,
        position.getId(),
        Map.of("broker", position.getBroker().name()));
    return toView(position);
  }

  public void delete(UUID userId, UUID positionId) {
    InvestmentPosition position = requirePosition(userId, positionId);
    positionRepository.delete(position);
    auditService.record(userId, AuditEventType.INVESTMENT_POSITION_DELETED, TABLE, positionId);
  }

  private void apply(
      InvestmentPosition position, InvestmentPositionRequest request, Amounts amounts) {
    UUID userId = position.getUserId();
    UUID id = position.getId();
    position.apply(
        parseBroker(request.broker()),
        encrypt(userId, id, "asset_name", requireText(request.assetName(), "assetName", 120)),
        encryptOptional(userId, id, "ticker", optionalText(request.ticker(), "ticker", 20)),
        encrypt(userId, id, "quantity", amounts.quantity()),
        encrypt(userId, id, "reference_price", amounts.referencePrice()),
        requireReferenceDate(request.referenceDate()),
        encryptOptional(userId, id, "notes", optionalText(request.notes(), "notes", 2000)),
        Boolean.TRUE.equals(request.excludeFromWealth()));
  }

  private InvestmentPosition requirePosition(UUID userId, UUID positionId) {
    return positionRepository
        .findByIdAndUserId(positionId, userId)
        .orElseThrow(NotFoundException::new);
  }

  /**
   * Calcula quantidade e preço canônicos.
   *
   * <p>Preço e valor total são mutuamente exclusivos. Com apenas o total, o preço é {@code total ÷
   * quantidade} arredondado para 8 casas decimais. O total exibido é sempre recalculado a partir da
   * quantidade e do preço armazenados.
   */
  private static Amounts calculate(InvestmentPositionRequest request) {
    BigDecimal quantity = parsePositive(request.quantity(), "quantity", "Informe a quantidade.");
    if (quantity.stripTrailingZeros().scale() > QUANTITY_SCALE) {
      throw new ValidationException(
          "quantity", "A quantidade deve ter no máximo " + QUANTITY_SCALE + " casas decimais.");
    }

    boolean hasPrice = request.referencePrice() != null && !request.referencePrice().isBlank();
    boolean hasTotal = request.totalValue() != null && !request.totalValue().isBlank();
    if (hasPrice == hasTotal) {
      throw new ValidationException(
          hasPrice ? "totalValue" : "referencePrice",
          hasPrice
              ? "Informe apenas o preço unitário ou o valor total."
              : "Informe o preço unitário ou o valor total.");
    }

    BigDecimal price;
    if (hasPrice) {
      price = parsePositive(request.referencePrice(), "referencePrice", "Preço unitário inválido.");
      if (price.stripTrailingZeros().scale() > PRICE_SCALE) {
        throw new ValidationException(
            "referencePrice",
            "O preço unitário deve ter no máximo " + PRICE_SCALE + " casas decimais.");
      }
    } else {
      BigDecimal total =
          parsePositive(request.totalValue(), "totalValue", "Valor total inválido.")
              .setScale(Money.SCALE, RoundingMode.HALF_EVEN);
      if (total.signum() <= 0) {
        throw new ValidationException("totalValue", "Valor total inválido.");
      }
      price = total.divide(quantity, PRICE_SCALE, RoundingMode.HALF_EVEN);
    }

    return new Amounts(canonical(quantity), canonical(price));
  }

  private static BigDecimal parsePositive(String value, String field, String message) {
    if (value == null || value.isBlank()) {
      throw new ValidationException(field, message);
    }
    BigDecimal parsed;
    try {
      parsed = new BigDecimal(value.trim());
    } catch (NumberFormatException exception) {
      throw new ValidationException(field, message);
    }
    if (parsed.signum() <= 0) {
      throw new ValidationException(field, message);
    }
    return parsed;
  }

  private static String canonical(BigDecimal value) {
    BigDecimal stripped = value.stripTrailingZeros();
    return stripped.scale() < 0 ? stripped.setScale(0).toPlainString() : stripped.toPlainString();
  }

  private static InvestmentBroker parseBroker(String value) {
    if (value == null || value.isBlank()) {
      throw new ValidationException("broker", "Informe a corretora.");
    }
    try {
      return InvestmentBroker.valueOf(value.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException exception) {
      throw new ValidationException("broker", "Corretora inválida.");
    }
  }

  private static LocalDate requireReferenceDate(LocalDate referenceDate) {
    if (referenceDate == null) {
      throw new ValidationException("referenceDate", "Informe a data de referência.");
    }
    return referenceDate;
  }

  private static String requireText(String value, String field, int maxLength) {
    if (value == null || value.isBlank()) {
      throw new ValidationException(
          field, "assetName".equals(field) ? "Informe o nome do ativo." : "Informe o valor.");
    }
    String trimmed = value.strip();
    if (trimmed.length() > maxLength) {
      throw new ValidationException(
          field, "O valor deve ter no máximo " + maxLength + " caracteres.");
    }
    return trimmed;
  }

  private static String optionalText(String value, String field, int maxLength) {
    if (value == null || value.isBlank()) {
      return null;
    }
    String trimmed = value.strip();
    if (trimmed.length() > maxLength) {
      throw new ValidationException(
          field, "O valor deve ter no máximo " + maxLength + " caracteres.");
    }
    return trimmed;
  }

  private InvestmentPositionView toView(InvestmentPosition position) {
    String quantity = decrypt(position, position.getQuantityEnc(), "quantity");
    String referencePrice = decrypt(position, position.getReferencePriceEnc(), "reference_price");
    String total = null;
    if (quantity != null && referencePrice != null) {
      total =
          new BigDecimal(quantity)
              .multiply(new BigDecimal(referencePrice))
              .setScale(Money.SCALE, RoundingMode.HALF_EVEN)
              .toPlainString();
    }
    return new InvestmentPositionView(
        position.getId(),
        position.getBroker().name(),
        decrypt(position, position.getAssetNameEnc(), "asset_name"),
        decrypt(position, position.getTickerEnc(), "ticker"),
        quantity,
        referencePrice,
        total,
        position.getReferenceDate(),
        decrypt(position, position.getNotesEnc(), "notes"),
        position.getOrigin().name(),
        position.isExcludeFromWealth());
  }

  private String decrypt(InvestmentPosition position, byte[] ciphertext, String column) {
    if (ciphertext == null) {
      return null;
    }
    try {
      return cryptoService.decryptString(
          ciphertext, new CryptoContext(position.getUserId(), TABLE, column, position.getId()));
    } catch (CryptoException exception) {
      return null;
    }
  }

  private byte[] encrypt(UUID userId, UUID positionId, String column, String value) {
    return cryptoService.encrypt(value, new CryptoContext(userId, TABLE, column, positionId));
  }

  private byte[] encryptOptional(UUID userId, UUID positionId, String column, String value) {
    return value == null ? null : encrypt(userId, positionId, column, value);
  }

  private static String encode(LocalDate referenceDate, UUID id) {
    String raw = referenceDate + "|" + id;
    return Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
  }

  private static Cursor decode(String cursor) {
    if (cursor == null || cursor.isBlank()) {
      return null;
    }
    try {
      String raw = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
      int separator = raw.indexOf('|');
      if (separator <= 0) {
        throw new IllegalArgumentException("cursor sem separador");
      }
      return new Cursor(
          LocalDate.parse(raw.substring(0, separator)),
          UUID.fromString(raw.substring(separator + 1)));
    } catch (RuntimeException exception) {
      throw new ValidationException("cursor", "Cursor inválido.");
    }
  }

  private record Cursor(LocalDate referenceDate, UUID id) {}

  private record Amounts(String quantity, String referencePrice) {}
}
