package com.deltafinance.investments.api;

import com.deltafinance.investments.application.InvestmentPositionService;
import com.deltafinance.investments.application.InvestmentPositionView;
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
 * Posições manuais de investimento.
 *
 * <p>Não existe endpoint de importação, cotação ou recomendação: o módulo é somente de registro
 * manual, conforme a fase secundária (US8).
 */
@RestController
@RequestMapping("/api/v1/investments/positions")
public class InvestmentController {

  private final InvestmentPositionService positionService;
  private final AuthenticatedUser authenticatedUser;

  public InvestmentController(
      InvestmentPositionService positionService, AuthenticatedUser authenticatedUser) {
    this.positionService = positionService;
    this.authenticatedUser = authenticatedUser;
  }

  @GetMapping
  public InvestmentPositionPageResponse list(
      @RequestParam(required = false) String cursor, @RequestParam(defaultValue = "50") int limit) {
    CursorPage<InvestmentPositionView> page =
        positionService.list(authenticatedUser.requireId(), PageRequest.of(limit, cursor));
    return new InvestmentPositionPageResponse(
        page.items().stream().map(InvestmentPositionResponse::from).toList(),
        page.nextCursor(),
        page.hasMore());
  }

  @GetMapping("/{id}")
  public InvestmentPositionResponse get(@PathVariable UUID id) {
    return InvestmentPositionResponse.from(positionService.get(authenticatedUser.requireId(), id));
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public InvestmentPositionResponse create(@Valid @RequestBody InvestmentPositionRequest request) {
    return InvestmentPositionResponse.from(
        positionService.create(authenticatedUser.requireId(), request));
  }

  @PatchMapping("/{id}")
  public InvestmentPositionResponse update(
      @PathVariable UUID id, @Valid @RequestBody InvestmentPositionRequest request) {
    return InvestmentPositionResponse.from(
        positionService.update(authenticatedUser.requireId(), id, request));
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@PathVariable UUID id) {
    positionService.delete(authenticatedUser.requireId(), id);
  }
}
