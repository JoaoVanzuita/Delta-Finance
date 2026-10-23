package com.deltafinance.transactions.api;

import com.deltafinance.security.AuthenticatedUser;
import com.deltafinance.transactions.application.ManualTransactionService;
import com.deltafinance.transactions.application.TransactionCorrectionService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Lançamentos manuais e correções de transações. */
@RestController
@RequestMapping("/api/v1/transactions")
public class ManualTransactionController {

  private final ManualTransactionService manualTransactionService;
  private final TransactionCorrectionService correctionService;
  private final AuthenticatedUser authenticatedUser;

  public ManualTransactionController(
      ManualTransactionService manualTransactionService,
      TransactionCorrectionService correctionService,
      AuthenticatedUser authenticatedUser) {
    this.manualTransactionService = manualTransactionService;
    this.correctionService = correctionService;
    this.authenticatedUser = authenticatedUser;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public TransactionResponse create(@Valid @RequestBody ManualTransactionRequest request) {
    return TransactionResponse.from(
        manualTransactionService.create(authenticatedUser.requireId(), request));
  }

  @PatchMapping("/{id}")
  public TransactionResponse correct(
      @PathVariable UUID id, @Valid @RequestBody TransactionCorrectionRequest request) {
    return TransactionResponse.from(
        correctionService.correct(authenticatedUser.requireId(), id, request));
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@PathVariable UUID id) {
    manualTransactionService.delete(authenticatedUser.requireId(), id);
  }
}
