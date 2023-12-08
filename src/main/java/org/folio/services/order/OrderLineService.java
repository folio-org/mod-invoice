package org.folio.services.order;

import static org.folio.invoices.utils.ErrorCodes.PO_LINE_NOT_FOUND;
import static org.folio.invoices.utils.ErrorCodes.PO_LINE_UPDATE_FAILURE;
import static org.folio.invoices.utils.ErrorCodes.USER_NOT_A_MEMBER_OF_THE_ACQ;
import static org.folio.invoices.utils.ResourcePathResolver.ORDER_LINES;
import static org.folio.invoices.utils.ResourcePathResolver.resourcesPath;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.folio.invoices.rest.exceptions.HttpException;
import org.folio.okapi.common.GenericCompositeFuture;
import org.folio.rest.acq.model.orders.CompositePoLine;
import org.folio.rest.acq.model.orders.PoLine;
import org.folio.rest.acq.model.orders.PoLineCollection;
import org.folio.rest.core.RestClient;
import org.folio.rest.core.models.RequestContext;
import org.folio.rest.core.models.RequestEntry;
import org.folio.rest.jaxrs.model.Parameter;
import org.folio.utils.ExceptionUtil;

import io.vertx.core.Future;

public class OrderLineService {
  private static final String ORDER_LINES_ENDPOINT = resourcesPath(ORDER_LINES);
  private static final String ORDER_LINES_BY_ID_ENDPOINT = ORDER_LINES_ENDPOINT + "/{id}";

  private final RestClient restClient;

  public OrderLineService(RestClient restClient) {
    this.restClient = restClient;
  }

  public Future<List<PoLine>> getPoLines(String query, RequestContext requestContext) {
    RequestEntry requestEntry = new RequestEntry(ORDER_LINES_ENDPOINT)
      .withQuery(query)
      .withOffset(0)
      .withLimit(Integer.MAX_VALUE);
    return restClient.get(requestEntry, PoLineCollection.class, requestContext)
      .map(PoLineCollection::getPoLines);
  }

  public Future<CompositePoLine> getPoLine(String poLineId, RequestContext requestContext) {
    RequestEntry requestEntry = new RequestEntry(ORDER_LINES_BY_ID_ENDPOINT).withId(poLineId);
    return restClient.get(requestEntry, CompositePoLine.class, requestContext)
      .recover(throwable -> {
        List<Parameter> parameters = Collections.singletonList(new Parameter().withKey("poLineId").withValue(poLineId));
        throw new HttpException(404, PO_LINE_NOT_FOUND.toError().withParameters(parameters));
      });
  }

  public Future<Void> updatePoLine(CompositePoLine poLine, RequestContext requestContext) {
    RequestEntry requestEntry = new RequestEntry(ORDER_LINES_BY_ID_ENDPOINT).withId(poLine.getId());
    return restClient.put(requestEntry, poLine, requestContext);
  }

  public Future<Void> updateCompositePoLines(List<CompositePoLine> poLines, RequestContext requestContext) {
    var futures = poLines.stream()
      .map(poLine -> updatePoLine(poLine, requestContext)
        .recover(cause -> {
          if (ExceptionUtil.matches(cause, USER_NOT_A_MEMBER_OF_THE_ACQ)) {
            throw new HttpException(400, USER_NOT_A_MEMBER_OF_THE_ACQ.toError());
          } else {
            throw new HttpException(400, PO_LINE_UPDATE_FAILURE.toError());
          }
        }))
      .collect(Collectors.toList());
    return GenericCompositeFuture.join(futures).mapEmpty();
  }

}
