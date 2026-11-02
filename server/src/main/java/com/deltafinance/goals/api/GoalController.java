package com.deltafinance.goals.api;

import com.deltafinance.goals.application.GoalProgressEntryView;
import com.deltafinance.goals.application.GoalService;
import com.deltafinance.goals.application.GoalView;
import com.deltafinance.security.AuthenticatedUser;
import com.deltafinance.shared.pagination.CursorPage;
import com.deltafinance.shared.pagination.PageRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Metas financeiras.
 *
 * <p>Nenhum endpoint move dinheiro, cria ordem ou executa ação bancária; as metas apenas calculam
 * progresso sobre alocações explícitas ou patrimônio líquido.
 */
@RestController
@RequestMapping("/api/v1/goals")
public class GoalController {

  private final GoalService goalService;
  private final AuthenticatedUser authenticatedUser;

  public GoalController(GoalService goalService, AuthenticatedUser authenticatedUser) {
    this.goalService = goalService;
    this.authenticatedUser = authenticatedUser;
  }

  @GetMapping
  public GoalListResponse list() {
    return new GoalListResponse(goalService.list(authenticatedUser.requireId()).stream().toList());
  }

  @GetMapping("/{id}")
  public GoalView get(@PathVariable UUID id) {
    return goalService.get(authenticatedUser.requireId(), id);
  }

  @GetMapping("/{id}/progress")
  public CursorPage<GoalProgressEntryView> progress(
      @PathVariable UUID id,
      @RequestParam(required = false) String cursor,
      @RequestParam(defaultValue = "50") int limit) {
    return goalService.progressHistory(
        authenticatedUser.requireId(), id, PageRequest.of(limit, cursor));
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public GoalView create(@Valid @RequestBody GoalRequest request) {
    return goalService.create(authenticatedUser.requireId(), request);
  }

  @PatchMapping("/{id}")
  public GoalView update(@PathVariable UUID id, @Valid @RequestBody GoalRequest request) {
    return goalService.update(authenticatedUser.requireId(), id, request);
  }

  @PostMapping("/{id}/status")
  public GoalView updateStatus(
      @PathVariable UUID id, @Valid @RequestBody GoalStatusRequest request) {
    return goalService.updateStatus(authenticatedUser.requireId(), id, request.status());
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@PathVariable UUID id) {
    goalService.delete(authenticatedUser.requireId(), id);
  }
}
