package com.deltafinance.unit.recalculation;

import static org.assertj.core.api.Assertions.assertThat;

import com.deltafinance.shared.recalculation.RecalculationBatch;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** O lote de importação devolve um conjunto único de cartões por recálculo. */
class RecalculationBatchTest {

  @Test
  void semLoteNaoEstaAtivo() {
    assertThat(RecalculationBatch.isActive()).isFalse();
  }

  @Test
  void fechaDevolvendoCartoesMarcadosUmaUnicaVez() {
    UUID card = UUID.randomUUID();
    RecalculationBatch.begin();
    RecalculationBatch.markDirty(card);
    RecalculationBatch.markDirty(card);

    assertThat(RecalculationBatch.isActive()).isTrue();
    assertThat(RecalculationBatch.end()).containsExactly(card);
    assertThat(RecalculationBatch.isActive()).isFalse();
  }

  @Test
  void loteAninhadoSoLiberaNoNivelExterno() {
    UUID card = UUID.randomUUID();
    RecalculationBatch.begin();
    RecalculationBatch.begin();
    RecalculationBatch.markDirty(card);

    assertThat(RecalculationBatch.end()).isEmpty();
    assertThat(RecalculationBatch.isActive()).isTrue();
    assertThat(RecalculationBatch.end()).containsExactly(card);
    assertThat(RecalculationBatch.isActive()).isFalse();
  }

  @Test
  void loteFechadoSemCartoesDevolveVazio() {
    RecalculationBatch.begin();

    assertThat(RecalculationBatch.end()).isEmpty();
  }
}
