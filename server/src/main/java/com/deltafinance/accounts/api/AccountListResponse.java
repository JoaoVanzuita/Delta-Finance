package com.deltafinance.accounts.api;

import com.deltafinance.accounts.application.AccountView;
import java.util.List;

public record AccountListResponse(List<AccountView> items) {}
