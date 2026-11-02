package com.deltafinance.architecture;

import com.deltafinance.shared.crypto.CryptoContext;
import com.deltafinance.shared.crypto.CryptoService;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;
import com.tngtech.archunit.library.dependencies.SlicesRuleDefinition;
import java.util.UUID;
import org.springframework.data.repository.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Fronteiras do monólito modular.
 *
 * <p>Falhas aqui quebram o build: dependências cruzadas indevidas, ciclos entre módulos,
 * controladores acessando repositórios/entidades e consultas financeiras sem escopo de usuário.
 */
@AnalyzeClasses(packages = "com.deltafinance", importOptions = ImportOption.DoNotIncludeTests.class)
class ModuleBoundaryTest {

  private static final String[] BUSINESS_MODULES = {
    "com.deltafinance.auth..",
    "com.deltafinance.security..",
    "com.deltafinance.integrations..",
    "com.deltafinance.connections..",
    "com.deltafinance.accounts..",
    "com.deltafinance.transactions..",
    "com.deltafinance.invoices..",
    "com.deltafinance.reconciliation..",
    "com.deltafinance.categories..",
    "com.deltafinance.reporting..",
    "com.deltafinance.privacy..",
    "com.deltafinance.investments..",
    "com.deltafinance.wealth..",
    "com.deltafinance.goals.."
  };

  private static final String[] FINANCIAL_MODULES = {
    "com.deltafinance.integrations..",
    "com.deltafinance.connections..",
    "com.deltafinance.accounts..",
    "com.deltafinance.transactions..",
    "com.deltafinance.invoices..",
    "com.deltafinance.reconciliation..",
    "com.deltafinance.categories..",
    "com.deltafinance.reporting..",
    "com.deltafinance.privacy..",
    "com.deltafinance.investments..",
    "com.deltafinance.wealth..",
    "com.deltafinance.goals.."
  };

  @ArchTest
  static final ArchRule sharedNaoDependeDeModulosDeNegocio =
      ArchRuleDefinition.noClasses()
          .that()
          .resideInAPackage("com.deltafinance.shared..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(BUSINESS_MODULES)
          .because("shared é a base transversal e não pode conhecer regra de negócio");

  @ArchTest
  static final ArchRule modulosNaoTemCiclos =
      SlicesRuleDefinition.slices()
          .matching("com.deltafinance.(*)..")
          .should()
          .beFreeOfCycles()
          .because("ciclos entre módulos impedem evolução e testes isolados");

  @ArchTest
  static final ArchRule apiNaoAcessaDominioInfraestruturaNemRepositorios =
      ArchRuleDefinition.noClasses()
          .that()
          .resideInAPackage("com.deltafinance..api..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "com.deltafinance..domain..",
              "com.deltafinance..infrastructure..",
              "com.deltafinance..config..")
          .orShould()
          .dependOnClassesThat()
          .areAssignableTo(Repository.class)
          .because("controllers conversam com serviços de aplicação, nunca com persistência");

  @ArchTest
  static final ArchRule apiNaoExpoeEntidadesJpa =
      ArchRuleDefinition.noClasses()
          .that()
          .resideInAPackage("com.deltafinance..api..")
          .should()
          .dependOnClassesThat()
          .areAnnotatedWith(jakarta.persistence.Entity.class)
          .because("entidades JPA não atravessam a fronteira pública do módulo");

  @ArchTest
  static final ArchRule leituraCifradaPassaPeloEncryptedValueReader =
      ArchRuleDefinition.noClasses()
          .that()
          .resideOutsideOfPackage("com.deltafinance.shared.crypto..")
          .and()
          .doNotHaveFullyQualifiedName("com.deltafinance.security.IdempotencyFilter")
          .and()
          .doNotHaveFullyQualifiedName("com.deltafinance.shared.crypto.KeyRotationJob")
          .should()
          .callMethod(CryptoService.class, "decryptString", byte[].class, CryptoContext.class)
          .orShould()
          .callMethod(CryptoService.class, "decrypt", byte[].class, CryptoContext.class)
          .because("leitura de campos cifrados deve usar EncryptedValueReader");

  @ArchTest
  static final ArchRule repositoriosFinanceirosNaoUsamFindByIdSemUsuario =
      ArchRuleDefinition.classes()
          .that()
          .areAssignableTo(Repository.class)
          .and()
          .resideInAnyPackage(FINANCIAL_MODULES)
          .should(naoDeclararBuscaPorIdSemUsuario())
          .because("consulta por ID sem user_id permite vazamento entre titulares");

  @ArchTest
  static final ArchRule apiNaoUsaTransactional =
      ArchRuleDefinition.methods()
          .that()
          .areDeclaredInClassesThat()
          .resideInAPackage("com.deltafinance..api..")
          .should(
              new ArchCondition<>("não declarar @Transactional na borda HTTP") {
                @Override
                public void check(JavaMethod method, ConditionEvents events) {
                  if (method.isAnnotatedWith(Transactional.class)) {
                    events.add(
                        SimpleConditionEvent.violated(
                            method, method.getFullName() + " usa @Transactional no controller"));
                  }
                }
              })
          .because("transação pertence ao serviço de aplicação, não ao controller");

  @ArchTest
  static final ArchRule consultasFinanceirasExigemEscopoDeUsuario =
      ArchRuleDefinition.methods()
          .that()
          .areDeclaredInClassesThat()
          .areAssignableTo(Repository.class)
          .and()
          .areDeclaredInClassesThat()
          .resideInAnyPackage(FINANCIAL_MODULES)
          .and()
          .haveNameMatching("(find|get|list|search|count|delete|exists).*")
          .should(exigirParametroDeUsuario())
          .because("nenhuma consulta financeira pode atravessar o limite do usuário");

  private static ArchCondition<JavaClass> naoDeclararBuscaPorIdSemUsuario() {
    return new ArchCondition<>("não declarar findById/getById/deleteById/existsById") {
      @Override
      public void check(JavaClass type, ConditionEvents events) {
        for (JavaMethod method : type.getMethods()) {
          if (method.getName().matches("(findById|getById|deleteById|existsById)")) {
            events.add(
                SimpleConditionEvent.violated(
                    method, method.getFullName() + " busca apenas pelo ID, sem user_id"));
          }
        }
      }
    };
  }

  private static ArchCondition<JavaMethod> exigirParametroDeUsuario() {
    return new ArchCondition<>("exigir parâmetro UUID (user_id)") {
      @Override
      public void check(JavaMethod method, ConditionEvents events) {
        boolean userScoped =
            method.getRawParameterTypes().stream()
                .anyMatch(type -> type.isEquivalentTo(UUID.class));
        if (!userScoped) {
          events.add(
              SimpleConditionEvent.violated(
                  method, method.getFullName() + " não recebe o user_id da sessão"));
        }
      }
    };
  }
}
