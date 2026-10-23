package com.deltafinance.categories.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoryRepository extends JpaRepository<Category, UUID> {

  Optional<Category> findByUserIdAndNormalizedNameHash(UUID userId, byte[] normalizedNameHash);

  Optional<Category> findByIdAndUserId(UUID id, UUID userId);

  List<Category> findByUserIdOrderByNameEncAsc(UUID userId);
}
