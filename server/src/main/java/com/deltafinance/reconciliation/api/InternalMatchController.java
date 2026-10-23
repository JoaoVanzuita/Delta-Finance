package com.deltafinance.reconciliation.api;

import com.deltafinance.reconciliation.application.InternalMatchDecisionService;
import com.deltafinance.reconciliation.application.InternalMatchService;
import com.deltafinance.reconciliation.application.InternalMatchView;
import com.deltafinance.security.AuthenticatedUser;
import com.deltafinance.shared.pagination.CursorPage;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/internal-matches")
public class InternalMatchController {

  private final InternalMatchService matchService;
  private final InternalMatchDecisionService decisionService;
  private final AuthenticatedUser authenticatedUser;

  public InternalMatchController(
      InternalMatchService matchService,
      InternalMatchDecisionService decisionService,
      AuthenticatedUser authenticatedUser) {
    this.matchService = matchService;
    this.decisionService = decisionService;
    this.authenticatedUser = authenticatedUser;
  }

  @GetMapping
  public InternalMatchPageResponse list(
      @RequestParam(defaultValue = "PENDING") String status,
      @RequestParam(defaultValue = "50") int limit,
      @RequestParam(required = false) String cursor) {
    CursorPage<InternalMatchView> page =
        matchService.list(authenticatedUser.requireId(), status, limit, cursor);
    return new InternalMatchPageResponse(
        page.items().stream().map(InternalMatchResponse::from).toList(),
        page.nextCursor(),
        page.hasMore());
  }

  @PostMapping("/{id}/confirm")
  public InternalMatchResponse confirm(@PathVariable UUID id) {
    return InternalMatchResponse.from(decisionService.confirm(authenticatedUser.requireId(), id));
  }

  @PostMapping("/{id}/reject")
  public InternalMatchResponse reject(@PathVariable UUID id) {
    return InternalMatchResponse.from(decisionService.reject(authenticatedUser.requireId(), id));
  }

  public record InternalMatchPageResponse(
      List<InternalMatchResponse> items, String nextCursor, boolean hasMore) {}
}
