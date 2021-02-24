package org.folio.services.finance.budget;

import org.folio.completablefuture.FolioVertxCompletableFuture;
import org.folio.invoices.rest.exceptions.HttpException;
import org.folio.rest.acq.model.finance.Budget;
import org.folio.rest.core.RestClient;
import org.folio.rest.core.models.RequestContext;
import org.folio.rest.jaxrs.model.Parameter;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toList;
import static org.folio.invoices.utils.ErrorCodes.BUDGET_NOT_FOUND;

public class BudgetService {

    private final RestClient activeBudgetRestClient;

    public BudgetService(RestClient activeBudgetRestClient) {
        this.activeBudgetRestClient = activeBudgetRestClient;
    }

    public CompletableFuture<List<Budget>> fetchBudgetsByFundIds(Collection<String> fundIds, RequestContext requestContext) {
        List<CompletableFuture<Budget>> futureList = fundIds.stream()
                .distinct()
                .map(fundId -> getActiveBudgetByFundId(fundId, requestContext))
                .collect(toList());

        return FolioVertxCompletableFuture.allOf(requestContext.getContext(), futureList.toArray(new CompletableFuture[0]))
                .thenApply(v -> futureList.stream().map(CompletableFuture::join).collect(Collectors.toList()));
    }

    private CompletableFuture<Budget> getActiveBudgetByFundId(String fundId, RequestContext requestContext) {
        return activeBudgetRestClient.getById(fundId, requestContext, Budget.class)
                .exceptionally(t -> {
                    Throwable cause = Objects.isNull(t.getCause()) ? t : t.getCause();
                    if (cause instanceof HttpException) {
                        throw new HttpException(404, BUDGET_NOT_FOUND
                                .toError().withParameters(Collections.singletonList(new Parameter().withKey("fund").withValue(fundId))));
                    }
                    throw new CompletionException(t.getCause());
                });
    }
}
