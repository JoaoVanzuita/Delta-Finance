package com.deltafinance.unit.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.deltafinance.auth.domain.CommonPasswordList;
import com.deltafinance.auth.domain.PasswordPolicy;
import com.deltafinance.auth.domain.PasswordPolicyException;
import org.junit.jupiter.api.Test;

class PasswordPolicyTest {

  private final PasswordPolicy policy = new PasswordPolicy(new CommonPasswordList());

  @Test
  void listaLocalTemNoMinimoDezMilEntradas() {
    assertThat(new CommonPasswordList().size()).isGreaterThanOrEqualTo(10_000);
  }

  @Test
  void aceitaFraseSenhaComDozeOuMaisCaracteres() {
    policy.validate("alice", "cavalo correto bateria grampo");
    policy.validate("alice", "SenhaLongaSemSerComum!");
  }

  @Test
  void rejeitaSenhaCurta() {
    assertThatThrownBy(() -> policy.validate("alice", "curta12345"))
        .isInstanceOfSatisfying(
            PasswordPolicyException.class,
            exception ->
                assertThat(exception.reason()).isEqualTo(PasswordPolicyException.Reason.TOO_SHORT));
  }

  @Test
  void rejeitaSenhaAcimaDe128Caracteres() {
    String longa = "a".repeat(129);

    assertThatThrownBy(() -> policy.validate("alice", longa))
        .isInstanceOfSatisfying(
            PasswordPolicyException.class,
            exception ->
                assertThat(exception.reason()).isEqualTo(PasswordPolicyException.Reason.TOO_LONG));
  }

  @Test
  void rejeitaSenhaQueContemONomeDeUsuario() {
    assertThatThrownBy(() -> policy.validate("alice", "Alice-uma-senha-longa"))
        .isInstanceOfSatisfying(
            PasswordPolicyException.class,
            exception ->
                assertThat(exception.reason())
                    .isEqualTo(PasswordPolicyException.Reason.CONTAINS_USERNAME));
  }

  @Test
  void rejeitaSenhaDaListaDeSenhasComuns() {
    assertThatThrownBy(() -> policy.validate("alice", "unbelievable"))
        .isInstanceOfSatisfying(
            PasswordPolicyException.class,
            exception ->
                assertThat(exception.reason())
                    .isEqualTo(PasswordPolicyException.Reason.COMMON_PASSWORD));
  }

  @Test
  void mensagensNaoEcoamASenha() {
    String message = PasswordPolicy.describe(PasswordPolicyException.Reason.COMMON_PASSWORD);

    assertThat(message).doesNotContain("unbelievable").contains("comum");
  }
}
