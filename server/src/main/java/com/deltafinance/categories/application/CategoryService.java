package com.deltafinance.categories.application;

import com.deltafinance.categories.domain.Category;
import com.deltafinance.categories.domain.CategoryKind;
import com.deltafinance.categories.domain.CategoryRepository;
import com.deltafinance.shared.audit.AuditEventType;
import com.deltafinance.shared.audit.AuditService;
import com.deltafinance.shared.crypto.CryptoContext;
import com.deltafinance.shared.crypto.CryptoService;
import com.deltafinance.shared.crypto.EncryptedValueReader;
import com.deltafinance.shared.crypto.IndexHmacService;
import com.deltafinance.shared.errors.ConflictException;
import com.deltafinance.shared.errors.NotFoundException;
import com.deltafinance.shared.errors.ValidationException;
import java.text.Normalizer;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Categorias predefinidas e personalizadas por usuário.
 *
 * <p>Nomes são cifrados e a unicidade é garantida por HMAC. Desativar uma categoria nunca apaga
 * transações: elas são reatribuídas ou ficam sem categoria, preservando o histórico.
 */
@Service
public class CategoryService {

  private static final String TABLE = "category";
  private static final String NAME_COLUMN = "name";

  private record Predefined(String key, String name, CategoryKind kind) {}

  private static final List<Predefined> PREDEFINED =
      List.of(
          new Predefined("moradia", "Moradia", CategoryKind.EXPENSE),
          new Predefined("alimentacao", "Alimentação", CategoryKind.EXPENSE),
          new Predefined("transporte", "Transporte", CategoryKind.EXPENSE),
          new Predefined("saude", "Saúde", CategoryKind.EXPENSE),
          new Predefined("educacao", "Educação", CategoryKind.EXPENSE),
          new Predefined("lazer", "Lazer", CategoryKind.EXPENSE),
          new Predefined("compras", "Compras", CategoryKind.EXPENSE),
          new Predefined("servicos", "Serviços", CategoryKind.EXPENSE),
          new Predefined("salario", "Salário", CategoryKind.INCOME),
          new Predefined("rendimentos", "Rendimentos de investimentos", CategoryKind.INCOME),
          new Predefined("investimentos", "Investimentos", CategoryKind.OTHER),
          new Predefined("aplicacoes", "Aplicações", CategoryKind.TRANSFER),
          new Predefined("resgates", "Resgates", CategoryKind.TRANSFER),
          new Predefined("transferencias", "Transferências", CategoryKind.TRANSFER),
          new Predefined("transferencias_externas", "Transferências externas", CategoryKind.OTHER),
          new Predefined("outros", "Outros", CategoryKind.OTHER));

  private final CategoryRepository categoryRepository;
  private final CryptoService cryptoService;
  private final EncryptedValueReader encryptedValueReader;
  private final IndexHmacService hmacService;
  private final AuditService auditService;
  private final JdbcTemplate jdbcTemplate;

  public CategoryService(
      CategoryRepository categoryRepository,
      CryptoService cryptoService,
      EncryptedValueReader encryptedValueReader,
      IndexHmacService hmacService,
      AuditService auditService,
      JdbcTemplate jdbcTemplate) {
    this.categoryRepository = categoryRepository;
    this.cryptoService = cryptoService;
    this.encryptedValueReader = encryptedValueReader;
    this.hmacService = hmacService;
    this.auditService = auditService;
    this.jdbcTemplate = jdbcTemplate;
  }

  public List<CategoryView> list(UUID userId) {
    ensurePredefined(userId);
    return listExisting(userId);
  }

  /** Categorias já existentes, sem criar as predefinidas (leitura para relatórios). */
  public List<CategoryView> listExisting(UUID userId) {
    return categoryRepository.findByUserIdOrderByNameEncAsc(userId).stream()
        .map(this::toView)
        .sorted(Comparator.comparing(CategoryView::name, String.CASE_INSENSITIVE_ORDER))
        .toList();
  }

  public CategoryView create(UUID userId, String name, String kindValue) {
    String displayName = requireName(name);
    CategoryKind kind = parseKind(kindValue);
    byte[] hash = hmacService.hmac("category", normalize(displayName));
    var existing = categoryRepository.findByUserIdAndNormalizedNameHash(userId, hash);
    if (existing.isPresent()) {
      Category category = existing.get();
      if (!category.isActive()) {
        category.activate();
        category.changeKind(kind);
        return toView(categoryRepository.save(category));
      }
      throw new ConflictException(
          "CATEGORY_ALREADY_EXISTS", "Já existe uma categoria com esse nome.");
    }
    UUID id = UUID.randomUUID();
    Category category =
        new Category(id, userId, encrypt(userId, id, displayName), hash, kind, null);
    try {
      categoryRepository.save(category);
    } catch (DataIntegrityViolationException race) {
      throw new ConflictException(
          "CATEGORY_ALREADY_EXISTS", "Já existe uma categoria com esse nome.");
    }
    auditService.record(userId, AuditEventType.CATEGORY_CREATED, "category", category.getId());
    return toView(category);
  }

  public CategoryView update(UUID userId, UUID categoryId, String name, String kindValue) {
    Category category = requireCategory(userId, categoryId);
    requireCustomCategory(category);
    String displayName = requireName(name);
    CategoryKind kind = parseKind(kindValue);
    byte[] hash = hmacService.hmac("category", normalize(displayName));
    categoryRepository
        .findByUserIdAndNormalizedNameHash(userId, hash)
        .filter(other -> !other.getId().equals(category.getId()))
        .ifPresent(
            other -> {
              throw new ConflictException(
                  "CATEGORY_ALREADY_EXISTS", "Já existe uma categoria com esse nome.");
            });
    category.rename(encrypt(userId, category.getId(), displayName), hash);
    category.changeKind(kind);
    categoryRepository.save(category);
    auditService.record(userId, AuditEventType.CATEGORY_UPDATED, "category", category.getId());
    return toView(category);
  }

  /** Desativa e reatribui (ou remove a categoria) das transações sem apagar lançamentos. */
  public void deactivate(UUID userId, UUID categoryId, UUID reassignTo) {
    deactivate(userId, categoryId, reassignTo, false);
  }

  /** Caminho interno para consolidar uma categoria legada da taxonomia. */
  void deactivatePredefined(UUID userId, UUID categoryId, UUID reassignTo) {
    deactivate(userId, categoryId, reassignTo, true);
  }

  private void deactivate(UUID userId, UUID categoryId, UUID reassignTo, boolean allowPredefined) {
    Category category = requireCategory(userId, categoryId);
    if (!allowPredefined) {
      requireCustomCategory(category);
    }
    if (reassignTo != null) {
      if (reassignTo.equals(categoryId)) {
        throw new ValidationException("reassignTo", "Escolha outra categoria para reatribuir.");
      }
      Category target = requireCategory(userId, reassignTo);
      if (!target.isActive()) {
        throw new ValidationException("reassignTo", "A categoria de destino deve estar ativa.");
      }
    }
    jdbcTemplate.update(
        """
        UPDATE transaction
           SET category_id = ?,
               edited_fields = CASE
                 WHEN 'category' = ANY(edited_fields) THEN edited_fields
                 ELSE array_append(edited_fields, 'category')
               END,
               updated_at = now()
         WHERE user_id = ? AND category_id = ?
        """,
        reassignTo,
        userId,
        categoryId);
    category.deactivate();
    categoryRepository.save(category);
    auditService.record(
        userId,
        AuditEventType.CATEGORY_DEACTIVATED,
        "category",
        category.getId(),
        java.util.Map.of("reassigned", reassignTo != null));
  }

  public Category requireActiveCategory(UUID userId, UUID categoryId) {
    Category category = requireCategory(userId, categoryId);
    if (!category.isActive()) {
      throw new ValidationException("categoryId", "A categoria está inativa.");
    }
    return category;
  }

  /** Resolve uma chave da taxonomia para a categoria predefinida do usuário. */
  public UUID canonicalCategoryId(UUID userId, String key) {
    ensurePredefined(userId);
    return categoryRepository
        .findByUserIdAndPredefinedKey(userId, key)
        .map(Category::getId)
        .orElseThrow(
            () -> new ValidationException("category", "Categoria canônica não encontrada: " + key));
  }

  /** ID da categoria predefinida existente, sem criá-la; nula quando a chave não existe. */
  public UUID predefinedCategoryId(UUID userId, String key) {
    return categoryRepository
        .findByUserIdAndPredefinedKey(userId, key)
        .map(Category::getId)
        .orElse(null);
  }

  private Category requireCategory(UUID userId, UUID categoryId) {
    return categoryRepository
        .findByIdAndUserId(categoryId, userId)
        .orElseThrow(NotFoundException::new);
  }

  /**
   * Categorias da taxonomia são imutáveis: renomear ou desativar quebraria a resolução canônica e
   * permitiria duplicatas com a mesma {@code predefined_key}.
   */
  private static void requireCustomCategory(Category category) {
    if (category.getPredefinedKey() != null) {
      throw new ConflictException(
          "PREDEFINED_CATEGORY_IMMUTABLE",
          "Categorias predefinidas não podem ser renomeadas, alteradas ou desativadas.");
    }
  }

  private void ensurePredefined(UUID userId) {
    for (Predefined predefined : PREDEFINED) {
      if (categoryRepository.findByUserIdAndPredefinedKey(userId, predefined.key()).isPresent()) {
        continue;
      }
      byte[] hash = hmacService.hmac("category", normalize(predefined.name()));
      UUID id = UUID.randomUUID();
      try {
        categoryRepository.save(
            new Category(
                id,
                userId,
                encrypt(userId, id, predefined.name()),
                hash,
                predefined.kind(),
                predefined.key()));
      } catch (DataIntegrityViolationException race) {
        // Outra requisição criou a mesma categoria predefinida; a lista continua consistente.
      }
    }
  }

  private CategoryView toView(Category category) {
    return new CategoryView(
        category.getId(),
        decrypt(category),
        category.getKind().name(),
        category.getPredefinedKey() != null,
        category.isActive());
  }

  private String decrypt(Category category) {
    // Nunca devolver null aqui: o nome entra em Comparator e na UI. Uma categoria ilegível
    // permanece listada com um rótulo estável em vez de derrubar dashboard e relatórios.
    return encryptedValueReader
        .tryRead(category.getUserId(), TABLE, NAME_COLUMN, category.getId(), category.getNameEnc())
        .orElse("Categoria sem nome");
  }

  private byte[] encrypt(UUID userId, UUID categoryId, String name) {
    return cryptoService.encrypt(name, new CryptoContext(userId, TABLE, NAME_COLUMN, categoryId));
  }

  private static String requireName(String name) {
    if (name == null || name.isBlank()) {
      throw new ValidationException("name", "Informe o nome da categoria.");
    }
    String trimmed = name.strip();
    if (trimmed.length() > 80) {
      throw new ValidationException("name", "O nome deve ter no máximo 80 caracteres.");
    }
    return trimmed;
  }

  private static CategoryKind parseKind(String value) {
    if (value == null || value.isBlank()) {
      throw new ValidationException("kind", "Informe o tipo da categoria.");
    }
    try {
      return CategoryKind.valueOf(value.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException exception) {
      throw new ValidationException("kind", "Tipo de categoria inválido.");
    }
  }

  static String normalize(String value) {
    return Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFKC)
        .strip()
        .toLowerCase(Locale.ROOT)
        .replaceAll("\\s+", " ");
  }
}
