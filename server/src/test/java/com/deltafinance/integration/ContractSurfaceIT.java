package com.deltafinance.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.deltafinance.support.PostgresTestBase;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.yaml.snakeyaml.Yaml;

/**
 * Garante que o contrato OpenAPI e os controllers exponham exatamente as mesmas operações.
 *
 * <p>Diferenças de parâmetros são cobertas pelos testes de integração dos endpoints; este teste
 * protege a superfície (método + caminho), que é o que mais custa detectar em revisão manual.
 */
class ContractSurfaceIT extends PostgresTestBase {

  private static final String API_PREFIX = "/api/v1";
  private static final Set<String> HTTP_METHODS = Set.of("get", "put", "post", "delete", "patch");

  @Autowired
  @Qualifier("requestMappingHandlerMapping") private RequestMappingHandlerMapping handlerMapping;

  @Test
  void contratoEControllersDeclaramAsMesmasOperacoes() throws Exception {
    Set<String> contract = contractOperations();
    Set<String> controllers = controllerOperations();

    assertThat(controllers)
        .as("operação do controller ausente no contrato")
        .containsExactlyInAnyOrderElementsOf(contract);
  }

  private static Set<String> contractOperations() throws IOException {
    Path path = contractPath();
    Set<String> operations = new TreeSet<>();
    try (InputStream input = Files.newInputStream(path)) {
      Map<String, Object> root = new Yaml().load(input);
      Map<String, Object> paths = map(root.get("paths"));
      for (Map.Entry<String, Object> entry : paths.entrySet()) {
        Map<String, Object> item = map(entry.getValue());
        for (String method : HTTP_METHODS) {
          if (item.containsKey(method)) {
            operations.add(method.toUpperCase() + " " + normalize(API_PREFIX + entry.getKey()));
          }
        }
      }
    }
    return operations;
  }

  private Set<String> controllerOperations() {
    Set<String> operations = new TreeSet<>();
    for (Map.Entry<RequestMappingInfo, HandlerMethod> entry :
        handlerMapping.getHandlerMethods().entrySet()) {
      HandlerMethod handler = entry.getValue();
      if (!handler.getBeanType().getPackageName().startsWith("com.deltafinance")) {
        continue;
      }
      RequestMappingInfo info = entry.getKey();
      Set<RequestMethod> methods = info.getMethodsCondition().getMethods();
      List<String> patterns = new ArrayList<>(info.getPatternValues());
      for (String pattern : patterns) {
        for (RequestMethod method : methods) {
          operations.add(method.name() + " " + normalize(pattern));
        }
      }
    }
    return operations;
  }

  private static Path contractPath() {
    for (Path candidate :
        List.of(
            Path.of("..", "specs", "001-finance", "contracts", "openapi.yaml"),
            Path.of("specs", "001-finance", "contracts", "openapi.yaml"))) {
      if (Files.exists(candidate)) {
        return candidate;
      }
    }
    throw new IllegalStateException("Contrato OpenAPI não encontrado.");
  }

  private static String normalize(String path) {
    return path.replaceAll("\\{[^/]+}", "{}");
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> map(Object value) {
    return (Map<String, Object>) value;
  }
}
