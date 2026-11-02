package com.deltafinance.categories.api;

import com.deltafinance.categories.application.CategoryView;
import java.util.List;

public record CategoryListResponse(List<CategoryView> items) {}
