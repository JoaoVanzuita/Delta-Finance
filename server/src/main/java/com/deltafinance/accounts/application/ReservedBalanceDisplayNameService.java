package com.deltafinance.accounts.application;

import com.deltafinance.accounts.domain.ReservedBalance;
import com.deltafinance.accounts.domain.ReservedBalanceRepository;
import com.deltafinance.shared.audit.AuditEventType;
import com.deltafinance.shared.audit.AuditService;
import com.deltafinance.shared.crypto.CryptoContext;
import com.deltafinance.shared.crypto.CryptoService;
import com.deltafinance.shared.errors.NotFoundException;
import com.deltafinance.shared.errors.ValidationException;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Nome local de um saldo reservado.
 *
 * <p>O nome do provedor nunca é sobrescrito. Ex.: “Cofrinho Vai Brasil” pode virar “Reserva de
 * emergência” apenas neste sistema, e o nome original continua disponível para restauração.
 */
@Service
public class ReservedBalanceDisplayNameService {

  private static final String TABLE = "reserved_balance";
  private static final int MAX_LENGTH = 120;

  private final ReservedBalanceRepository reservedBalanceRepository;
  private final ReservedBalanceQueryService queryService;
  private final CryptoService cryptoService;
  private final AuditService auditService;

  public ReservedBalanceDisplayNameService(
      ReservedBalanceRepository reservedBalanceRepository,
      ReservedBalanceQueryService queryService,
      CryptoService cryptoService,
      AuditService auditService) {
    this.reservedBalanceRepository = reservedBalanceRepository;
    this.queryService = queryService;
    this.cryptoService = cryptoService;
    this.auditService = auditService;
  }

  @Transactional
  public ReservedBalanceView rename(UUID userId, UUID reservedBalanceId, String displayName) {
    ReservedBalance reserved = require(userId, reservedBalanceId);
    String normalized = requireDisplayName(displayName);
    reserved.renameDisplayName(
        cryptoService.encrypt(
            normalized, new CryptoContext(userId, TABLE, "display_name", reserved.getId())));
    reservedBalanceRepository.save(reserved);
    auditService.record(
        userId,
        AuditEventType.RESERVED_BALANCE_RENAMED,
        TABLE,
        reserved.getId(),
        Map.of("nameCustomized", true));
    return queryService.get(userId, reservedBalanceId);
  }

  @Transactional
  public ReservedBalanceView reset(UUID userId, UUID reservedBalanceId) {
    ReservedBalance reserved = require(userId, reservedBalanceId);
    reserved.clearDisplayName();
    reservedBalanceRepository.save(reserved);
    auditService.record(
        userId,
        AuditEventType.RESERVED_BALANCE_NAME_RESET,
        TABLE,
        reserved.getId(),
        Map.of("nameCustomized", false));
    return queryService.get(userId, reservedBalanceId);
  }

  private ReservedBalance require(UUID userId, UUID reservedBalanceId) {
    return reservedBalanceRepository
        .findByIdAndUserId(reservedBalanceId, userId)
        .orElseThrow(NotFoundException::new);
  }

  private static String requireDisplayName(String displayName) {
    if (displayName == null || displayName.isBlank()) {
      throw new ValidationException("displayName", "Informe o nome de exibição.");
    }
    String trimmed = displayName.strip();
    if (trimmed.length() > MAX_LENGTH) {
      throw new ValidationException(
          "displayName", "O nome de exibição deve ter no máximo " + MAX_LENGTH + " caracteres.");
    }
    if (trimmed.chars().anyMatch(Character::isISOControl)) {
      throw new ValidationException(
          "displayName", "O nome de exibição contém caracteres inválidos.");
    }
    return trimmed;
  }
}
