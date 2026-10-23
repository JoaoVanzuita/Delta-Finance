package com.deltafinance.accounts.domain;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CardProfileRepository extends JpaRepository<CardProfile, UUID> {

  Optional<CardProfile> findByAccountIdAndUserId(UUID accountId, UUID userId);
}
