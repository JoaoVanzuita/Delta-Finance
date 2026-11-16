package com.deltafinance.transactions.api;

import java.time.LocalDate;

/** Efetivação de um lançamento pendente; data ausente usa a previsão original. */
public record SettleTransactionRequest(LocalDate postedOn) {}
