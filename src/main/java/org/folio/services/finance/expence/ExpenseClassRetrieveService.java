package org.folio.services.finance.expence;

import static java.util.stream.Collectors.toList;
import static org.folio.invoices.utils.ErrorCodes.EXPENSE_CLASS_NOT_FOUND;
import static org.folio.invoices.utils.HelperUtils.collectResultsOnSuccess;
import static org.folio.invoices.utils.HelperUtils.convertIdsToCqlQuery;
import static org.folio.rest.RestConstants.MAX_IDS_FOR_GET_RQ;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import org.folio.invoices.rest.exceptions.HttpException;
import org.folio.invoices.utils.HelperUtils;
import org.folio.rest.acq.model.finance.ExpenseClass;
import org.folio.rest.acq.model.finance.ExpenseClassCollection;
import org.folio.rest.core.RestClient;
import org.folio.rest.core.models.RequestContext;
import org.folio.rest.jaxrs.model.Parameter;

import one.util.streamex.StreamEx;

public class ExpenseClassRetrieveService {
  private final RestClient restClient;

  public ExpenseClassRetrieveService(RestClient restClient) {
    this.restClient = restClient;
  }

  public CompletableFuture<ExpenseClassCollection> getExpenseClasses(String query, int offset, int limit, RequestContext requestContext) {
    return restClient.get(query, offset, limit, requestContext, ExpenseClassCollection.class);
  }

  public CompletableFuture<List<ExpenseClass>> getExpenseClasses(List<String> expenseClassIds, RequestContext requestContext) {
    List<CompletableFuture<ExpenseClassCollection>> expenseClassesFutureList = StreamEx
      .ofSubLists(expenseClassIds, MAX_IDS_FOR_GET_RQ)
      .map(ids ->  getExpenseClassesChunk(ids, requestContext))
      .collect(toList());

    return collectResultsOnSuccess(expenseClassesFutureList)
                      .thenApply(expenseClassCollections ->
                                  expenseClassCollections.stream().flatMap(col -> col.getExpenseClasses().stream()).collect(toList())
                                );
  }

  public CompletableFuture<ExpenseClass> getExpenseClassById(String id, RequestContext requestContext) {
    return restClient.getById(id, requestContext, ExpenseClass.class)
            .exceptionally(t -> {
              Throwable cause = t.getCause() == null ? t : t.getCause();
              if (HelperUtils.isNotFound(cause)) {
                List<Parameter> parameters = Collections.singletonList(new Parameter().withValue(id).withKey("expenseClass"));
                  cause = new HttpException(404, EXPENSE_CLASS_NOT_FOUND.toError().withParameters(parameters));
              }
              throw new CompletionException(cause);
            });
  }

  private CompletableFuture<ExpenseClassCollection> getExpenseClassesChunk(List<String> expenseClassIds, RequestContext requestContext) {
    String query = convertIdsToCqlQuery(new ArrayList<>(expenseClassIds));
    return this.getExpenseClasses(query, 0, expenseClassIds.size(), requestContext);
  }
}
