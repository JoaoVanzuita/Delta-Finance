package com.deltafinance.accounts.application;

import com.deltafinance.accounts.domain.ReservedBalanceTreatment;
import java.util.UUID;

/** Porta para o tratamento padrão de saldos reservados definido na conexão. */
public interface ConnectionReservedBalanceTreatmentPort {

  /** Tratamento padrão da conexão; {@link ReservedBalanceTreatment#UNKNOWN} quando não definido. */
  ReservedBalanceTreatment treatmentOf(UUID userId, UUID connectionId);
}
