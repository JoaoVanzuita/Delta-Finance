package com.deltafinance.categories.api;

import com.deltafinance.categories.application.CategoryService;
import com.deltafinance.categories.application.CategoryView;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/categories")
public class CategoryController {

  private final CategoryService categoryService;
  private final AuthenticatedUser authenticatedUser;

  public CategoryController(CategoryService categoryService, AuthenticatedUser authenticatedUser) {
    this.categoryService = categoryService;
    this.authenticatedUser = authenticatedUser;
  }

  @GetMapping
  public CategoryListResponse list() {
    return new CategoryListResponse(
        categoryService.list(authenticatedUser.requireId()).stream()
            .map(CategoryResponse::from)
            .toList());
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public CategoryResponse create(@Valid @RequestBody CategoryRequest request) {
    CategoryView view =
        categoryService.create(authenticatedUser.requireId(), request.name(), request.kind());
    return CategoryResponse.from(view);
  }

  @PatchMapping("/{id}")
  public CategoryResponse update(
      @PathVariable UUID id, @Valid @RequestBody CategoryRequest request) {
    return CategoryResponse.from(
        categoryService.update(authenticatedUser.requireId(), id, request.name(), request.kind()));
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void deactivate(@PathVariable UUID id, @RequestParam(required = false) UUID reassignTo) {
    categoryService.deactivate(authenticatedUser.requireId(), id, reassignTo);
  }
}
