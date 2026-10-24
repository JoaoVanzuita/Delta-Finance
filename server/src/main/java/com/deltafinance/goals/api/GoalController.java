package com.deltafinance.goals.api;

import com.deltafinance.goals.application.GoalService;
import com.deltafinance.security.AuthenticatedUser;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Metas financeiras.
 *
 * <p>Nenhum endpoint move dinheiro, cria ordem ou executa ação bancária; as metas apenas calculam
 * progresso sobre alocações explícitas, patrimônio líquido ou dívida inicial.
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
    return new GoalListResponse(
        goalService.list(authenticatedUser.requireId()).stream().map(GoalResponse::from).toList());
  }

  @GetMapping("/{id}")
  public GoalResponse get(@PathVariable UUID id) {
    return GoalResponse.from(goalService.get(authenticatedUser.requireId(), id));
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public GoalResponse create(@Valid @RequestBody GoalRequest request) {
    return GoalResponse.from(goalService.create(authenticatedUser.requireId(), request));
  }

  @PatchMapping("/{id}")
  public GoalResponse update(@PathVariable UUID id, @Valid @RequestBody GoalRequest request) {
    return GoalResponse.from(goalService.update(authenticatedUser.requireId(), id, request));
  }

  @PostMapping("/{id}/status")
  public GoalResponse updateStatus(
      @PathVariable UUID id, @Valid @RequestBody GoalStatusRequest request) {
    return GoalResponse.from(
        goalService.updateStatus(authenticatedUser.requireId(), id, request.status()));
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@PathVariable UUID id) {
    goalService.delete(authenticatedUser.requireId(), id);
  }
}
