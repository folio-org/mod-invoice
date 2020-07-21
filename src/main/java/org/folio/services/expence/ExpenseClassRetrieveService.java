package org.folio.services.expence;

import static java.util.stream.Collectors.toList;
import static org.folio.invoices.utils.HelperUtils.collectResultsOnSuccess;
import static org.folio.invoices.utils.HelperUtils.convertIdsToCqlQuery;
import static org.folio.invoices.utils.ResourcePathResolver.EXPENSE_CLASSES_URL;
import static org.folio.rest.RestConstants.MAX_IDS_FOR_GET_RQ;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.folio.invoices.utils.ResourcePathResolver;
import org.folio.rest.acq.model.finance.ExpenseClass;
import org.folio.rest.acq.model.finance.ExpenseClassCollection;
import org.folio.rest.core.RestClient;

import io.vertx.core.Context;
import one.util.streamex.StreamEx;

public class ExpenseClassRetrieveService {
  private final RestClient restClient;

  public ExpenseClassRetrieveService(RestClient restClient) {
    this.restClient = restClient;
  }

  private static class SingletonHolder {
    public static final RestClient restClient = new RestClient(ResourcePathResolver.resourcesPath(EXPENSE_CLASSES_URL));
    public static final ExpenseClassRetrieveService HOLDER_INSTANCE = new ExpenseClassRetrieveService(restClient);
  }

  public static ExpenseClassRetrieveService getInstance() {
    return ExpenseClassRetrieveService.SingletonHolder.HOLDER_INSTANCE;
  }

  public CompletableFuture<ExpenseClassCollection> getExpenseClasses(String query, int offset, int limit, Context context,
                                                                     Map<String, String> headers) {
    return restClient.get(query, offset, limit, context, headers, ExpenseClassCollection.class);
  }

  public CompletableFuture<List<ExpenseClass>> getExpenseClasses(List<String> expenseClassIds, Context context, Map<String, String> headers) {
    List<CompletableFuture<ExpenseClassCollection>> expenseClassesFutureList = StreamEx
      .ofSubLists(expenseClassIds, MAX_IDS_FOR_GET_RQ)
      .map(ids ->  getExpenseClassesChunk(expenseClassIds, context, headers))
      .collect(toList());

    return collectResultsOnSuccess(expenseClassesFutureList)
                      .thenApply(expenseClassCollections ->
                                  expenseClassCollections.stream().flatMap(col -> col.getExpenseClasses().stream()).collect(toList())
                                );
  }

  public CompletableFuture<ExpenseClass> getExpenseClassById(String id, Context context, Map<String, String> headers) {
    return restClient.getById(id, context, headers, ExpenseClass.class);
  }

  public CompletableFuture<ExpenseClassCollection> getExpenseClassesChunk(List<String> expenseClassIds, Context context, Map<String, String> headers) {
    String query = convertIdsToCqlQuery(new ArrayList<>(expenseClassIds));
    return this.getExpenseClasses(query, 0, expenseClassIds.size(), context, headers);
  }
}
