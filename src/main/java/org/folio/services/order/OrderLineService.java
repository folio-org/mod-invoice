package org.folio.services.order;

import org.folio.completablefuture.FolioVertxCompletableFuture;
import org.folio.invoices.rest.exceptions.HttpException;
import org.folio.rest.acq.model.orders.CompositePoLine;
import org.folio.rest.core.RestClient;
import org.folio.rest.core.models.RequestContext;
import org.folio.rest.core.models.RequestEntry;
import org.folio.rest.jaxrs.model.Parameter;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static org.folio.invoices.utils.ErrorCodes.PO_LINE_NOT_FOUND;
import static org.folio.invoices.utils.ErrorCodes.PO_LINE_UPDATE_FAILURE;
import static org.folio.invoices.utils.ResourcePathResolver.ORDER_LINES;
import static org.folio.invoices.utils.ResourcePathResolver.resourcesPath;

public class OrderLineService {
  private static final String ORDER_LINES_ENDPOINT = resourcesPath(ORDER_LINES);
  private static final String ORDER_LINES_BY_ID_ENDPOINT = ORDER_LINES_ENDPOINT + "/{id}";

  private final RestClient restClient;

  public OrderLineService(RestClient restClient) {
    this.restClient = restClient;
  }

  public CompletableFuture<CompositePoLine> getPoLine(String poLineId, RequestContext requestContext) {
    RequestEntry requestEntry = new RequestEntry(ORDER_LINES_BY_ID_ENDPOINT).withId(poLineId);
    return restClient.get(requestEntry, requestContext, CompositePoLine.class)
      .exceptionally(throwable -> {
        List<Parameter> parameters = Collections.singletonList(new Parameter().withKey("poLineId").withValue(poLineId));
        throw new HttpException(404, PO_LINE_NOT_FOUND.toError().withParameters(parameters));
      });
  }

  public CompletableFuture<Void> updatePoLine(CompositePoLine poLine, RequestContext requestContext) {
    RequestEntry requestEntry = new RequestEntry(ORDER_LINES_BY_ID_ENDPOINT).withId(poLine.getId());
    return restClient.put(requestEntry, poLine, requestContext);
  }

  public CompletionStage<Void> updateCompositePoLines(List<CompositePoLine> poLines, RequestContext requestContext) {
    return FolioVertxCompletableFuture.allOf(requestContext.getContext(), poLines.stream()
      .map(poLine -> updatePoLine(poLine, requestContext)
        .exceptionally(t -> {
          throw new HttpException(400, PO_LINE_UPDATE_FAILURE.toError());
        }))
      .toArray(CompletableFuture[]::new));
  }
}
