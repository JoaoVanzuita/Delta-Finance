package com.deltafinance.accounts.application;

import com.deltafinance.integrations.infrastructure.PluggyModels;
import java.util.List;
import java.util.UUID;

/**
 * Bills oficiais lidas durante a importação de um cartão.
 *
 * <p>O módulo de contas apenas anuncia os dados normalizados; a persistência como referência de
 * conciliação pertence ao módulo de faturas. O evento é síncrono e não carrega segredos.
 */
public record CardBillsFetchedEvent(
    UUID userId, UUID cardAccountId, List<PluggyModels.PluggyBill> bills) {}
