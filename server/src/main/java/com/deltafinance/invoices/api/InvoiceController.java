package com.deltafinance.invoices.api;

import com.deltafinance.invoices.application.InvoiceItemCorrectionView;
import com.deltafinance.invoices.application.InvoiceItemService;
import com.deltafinance.invoices.application.InvoiceItemView;
import com.deltafinance.invoices.application.InvoicePaymentService;
import com.deltafinance.invoices.application.InvoicePaymentView;
import com.deltafinance.invoices.application.InvoiceQueryService;
import com.deltafinance.invoices.application.InvoiceView;
import com.deltafinance.security.AuthenticatedUser;
import com.deltafinance.shared.pagination.CursorPage;
import jakarta.validation.Valid;
import java.util.List;
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

@RestController
@RequestMapping("/api/v1")
public class InvoiceController {

  private final InvoiceQueryService queryService;
  private final InvoiceItemService itemService;
  private final InvoicePaymentService paymentService;
  private final AuthenticatedUser authenticatedUser;

  public InvoiceController(
      InvoiceQueryService queryService,
      InvoiceItemService itemService,
      InvoicePaymentService paymentService,
      AuthenticatedUser authenticatedUser) {
    this.queryService = queryService;
    this.itemService = itemService;
    this.paymentService = paymentService;
    this.authenticatedUser = authenticatedUser;
  }

  @GetMapping("/cards/{accountId}/invoices")
  public InvoicePageResponse list(
      @PathVariable UUID accountId,
      @RequestParam(required = false) String cursor,
      @RequestParam(defaultValue = "20") int limit) {
    CursorPage<InvoiceView> page =
        queryService.list(authenticatedUser.requireId(), accountId, cursor, limit);
    return new InvoicePageResponse(
        page.items().stream().map(InvoiceResponse::from).toList(),
        page.nextCursor(),
        page.hasMore());
  }

  @GetMapping("/invoices/{id}")
  public InvoiceDetailResponse detail(@PathVariable UUID id) {
    return InvoiceDetailResponse.from(queryService.detail(authenticatedUser.requireId(), id));
  }

  @PostMapping("/invoices/{id}/items")
  @ResponseStatus(HttpStatus.CREATED)
  public InvoiceItemResponse addItem(
      @PathVariable UUID id, @Valid @RequestBody InvoiceItemRequest request) {
    InvoiceItemView view = itemService.add(authenticatedUser.requireId(), id, request);
    return InvoiceItemResponse.from(view);
  }

  @PatchMapping("/invoice-items/{id}")
  public InvoiceItemResponse updateItem(
      @PathVariable UUID id, @Valid @RequestBody InvoiceItemRequest request) {
    return InvoiceItemResponse.from(itemService.update(authenticatedUser.requireId(), id, request));
  }

  @DeleteMapping("/invoice-items/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void removeItem(@PathVariable UUID id) {
    itemService.remove(authenticatedUser.requireId(), id);
  }

  @GetMapping("/invoice-items/{id}/corrections")
  public List<InvoiceItemCorrectionResponse> corrections(@PathVariable UUID id) {
    List<InvoiceItemCorrectionView> views =
        itemService.corrections(authenticatedUser.requireId(), id);
    return views.stream().map(InvoiceItemCorrectionResponse::from).toList();
  }

  @PostMapping("/invoices/{id}/payments")
  @ResponseStatus(HttpStatus.CREATED)
  public InvoicePaymentResponse addPayment(
      @PathVariable UUID id, @Valid @RequestBody InvoicePaymentRequest request) {
    InvoicePaymentView view = paymentService.add(authenticatedUser.requireId(), id, request);
    return InvoicePaymentResponse.from(view);
  }
}
