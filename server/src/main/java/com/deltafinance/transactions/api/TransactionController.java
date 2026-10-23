package com.deltafinance.transactions.api;

import com.deltafinance.security.AuthenticatedUser;
import com.deltafinance.shared.pagination.CursorPage;
import com.deltafinance.shared.pagination.PageRequest;
import com.deltafinance.transactions.application.SourceRemovalReviewService;
import com.deltafinance.transactions.application.TransactionQueryService;
import com.deltafinance.transactions.application.TransactionView;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/transactions")
public class TransactionController {

  private final TransactionQueryService queryService;
  private final SourceRemovalReviewService sourceRemovalReviewService;
  private final AuthenticatedUser authenticatedUser;

  public TransactionController(
      TransactionQueryService queryService,
      SourceRemovalReviewService sourceRemovalReviewService,
      AuthenticatedUser authenticatedUser) {
    this.queryService = queryService;
    this.sourceRemovalReviewService = sourceRemovalReviewService;
    this.authenticatedUser = authenticatedUser;
  }

  @GetMapping
  public TransactionPageResponse list(
      @RequestParam(required = false) String cursor,
      @RequestParam(defaultValue = "50") int limit,
      @RequestParam(required = false) UUID accountId,
      @RequestParam(required = false) UUID categoryId,
      @RequestParam(required = false) String status,
      @RequestParam(required = false) String type,
      @RequestParam(required = false) String origin,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
    CursorPage<TransactionView> page =
        queryService.list(
            authenticatedUser.requireId(),
            PageRequest.of(limit, cursor),
            accountId,
            categoryId,
            status,
            type,
            origin,
            from,
            to);
    return new TransactionPageResponse(
        page.items().stream().map(TransactionResponse::from).toList(),
        page.nextCursor(),
        page.hasMore());
  }

  /** Revisão explícita de uma transação removida na origem (RESTORE/CONFIRM_REMOVAL). */
  @PostMapping("/{id}/source-removal")
  public TransactionResponse reviewSourceRemoval(
      @PathVariable UUID id, @Valid @RequestBody SourceRemovalReviewRequest request) {
    return TransactionResponse.from(
        sourceRemovalReviewService.review(authenticatedUser.requireId(), id, request.action()));
  }
}
