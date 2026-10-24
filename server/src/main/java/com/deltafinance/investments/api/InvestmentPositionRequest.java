package com.deltafinance.investments.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * Criação/edição de posição manual.
 *
 * <p>O preço unitário ou o valor total deve ser informado — nunca os dois. Quando apenas o valor
 * total é enviado, o preço é derivado por {@code total ÷ quantidade}, com 8 casas decimais.
 */
public record InvestmentPositionRequest(
    @NotBlank(message = "Informe a corretora.") String broker,
    @NotBlank(message = "Informe o nome do ativo.") @Size(max = 120, message = "O nome do ativo deve ter no máximo 120 caracteres.") String assetName,
    @Size(max = 20, message = "O ticker deve ter no máximo 20 caracteres.") String ticker,
    @NotBlank(message = "Informe a quantidade.") String quantity,
    String referencePrice,
    String totalValue,
    @NotNull(message = "Informe a data de referência.") LocalDate referenceDate,
    @Size(max = 2000, message = "As observações devem ter no máximo 2000 caracteres.") String notes) {}
