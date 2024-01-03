package org.folio.services.finance.expence;

import static java.util.stream.Collectors.toList;
import static org.folio.invoices.utils.ErrorCodes.EXPENSE_CLASS_NOT_FOUND;
import static org.folio.invoices.utils.HelperUtils.collectResultsOnSuccess;
import static org.folio.invoices.utils.HelperUtils.convertIdsToCqlQuery;
import static org.folio.invoices.utils.ResourcePathResolver.EXPENSE_CLASSES_URL;
import static org.folio.invoices.utils.ResourcePathResolver.resourcesPath;
import static org.folio.rest.RestConstants.MAX_IDS_FOR_GET_RQ;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.folio.invoices.rest.exceptions.HttpException;
import org.folio.invoices.utils.HelperUtils;
import org.folio.rest.acq.model.finance.ExpenseClass;
import org.folio.rest.acq.model.finance.ExpenseClassCollection;
import org.folio.rest.core.RestClient;
import org.folio.rest.core.models.RequestContext;
import org.folio.rest.core.models.RequestEntry;
import org.folio.rest.jaxrs.model.Parameter;
import org.folio.utils.LoggingHelper;
import org.springframework.stereotype.Service;

import io.vertx.core.Future;
import one.util.streamex.StreamEx;

@Service
public class ExpenseClassRetrieveService {

  private static final String EXPENSE_CLASS_ENDPOINT = resourcesPath(EXPENSE_CLASSES_URL);
  private static final String EXPENSE_CLASS_BY_ID_ENDPOINT = EXPENSE_CLASS_ENDPOINT + "/{id}";

  private final RestClient restClient;

  public ExpenseClassRetrieveService(RestClient restClient) {
    this.restClient = restClient;
  }

  public Future<ExpenseClassCollection> getExpenseClasses(String query, int offset, int limit, RequestContext requestContext) {
    RequestEntry requestEntry = new RequestEntry(EXPENSE_CLASS_ENDPOINT)
        .withQuery(query)
        .withOffset(offset)
        .withLimit(limit);
    LoggingHelper.logQuery("getExpenseClasses", requestEntry);
    return restClient.get(requestEntry, ExpenseClassCollection.class, requestContext);
  }

  public Future<List<ExpenseClass>> getExpenseClasses(List<String> expenseClassIds, RequestContext requestContext) {
    List<Future<ExpenseClassCollection>> expenseClassesFutureList = StreamEx
      .ofSubLists(expenseClassIds, MAX_IDS_FOR_GET_RQ)
      .map(ids ->  getExpenseClassesChunk(ids, requestContext))
      .collect(toList());

    return collectResultsOnSuccess(expenseClassesFutureList)
                      .map(expenseClassCollections ->
                                  expenseClassCollections.stream().flatMap(col -> col.getExpenseClasses().stream()).collect(toList())
                                );
  }

  public Future<ExpenseClass> getExpenseClassById(String id, RequestContext requestContext) {
    RequestEntry requestEntry = new RequestEntry(EXPENSE_CLASS_BY_ID_ENDPOINT)
        .withId(id);
    return restClient.get(requestEntry, ExpenseClass.class, requestContext)
            .recover(t -> {
              Throwable cause = t.getCause() == null ? t : t.getCause();
              if (HelperUtils.isNotFound(cause)) {
                List<Parameter> parameters = Collections.singletonList(new Parameter().withValue(id).withKey("expenseClass"));
                  cause = new HttpException(404, EXPENSE_CLASS_NOT_FOUND.toError().withParameters(parameters));
              }
              return Future.failedFuture(cause);
            });
  }

  private Future<ExpenseClassCollection> getExpenseClassesChunk(List<String> expenseClassIds, RequestContext requestContext) {
    String query = convertIdsToCqlQuery(new ArrayList<>(expenseClassIds));
    return this.getExpenseClasses(query, 0, expenseClassIds.size(), requestContext);
  }
}
