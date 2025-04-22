package org.folio.services.order;

import static org.folio.invoices.utils.ErrorCodes.PO_LINE_NOT_FOUND;
import static org.folio.invoices.utils.ErrorCodes.PO_LINE_UPDATE_FAILURE;
import static org.folio.invoices.utils.ErrorCodes.USER_NOT_A_MEMBER_OF_THE_ACQ;
import static org.folio.invoices.utils.HelperUtils.collectResultsOnSuccess;
import static org.folio.invoices.utils.ResourcePathResolver.ORDER_LINES;
import static org.folio.invoices.utils.ResourcePathResolver.resourcesPath;
import static org.folio.rest.RestConstants.MAX_IDS_FOR_GET_RQ;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import io.vertx.core.Future;
import one.util.streamex.StreamEx;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.invoices.rest.exceptions.HttpException;
import org.folio.invoices.utils.HelperUtils;
import org.folio.okapi.common.GenericCompositeFuture;
import org.folio.rest.acq.model.orders.PoLine;
import org.folio.rest.acq.model.orders.PoLineCollection;
import org.folio.rest.core.RestClient;
import org.folio.rest.core.models.RequestContext;
import org.folio.rest.core.models.RequestEntry;
import org.folio.rest.jaxrs.model.Parameter;
import org.folio.utils.ExceptionUtil;

public class OrderLineService {
  private static final Logger logger = LogManager.getLogger();

  private static final String ORDER_LINES_ENDPOINT = resourcesPath(ORDER_LINES);
  private static final String ORDER_LINES_BY_ID_ENDPOINT = ORDER_LINES_ENDPOINT + "/{id}";

  private final RestClient restClient;

  public OrderLineService(RestClient restClient) {
    this.restClient = restClient;
  }

  public Future<List<PoLine>> getPoLinesByQuery(String query, RequestContext requestContext) {
    RequestEntry requestEntry = new RequestEntry(ORDER_LINES_ENDPOINT)
      .withQuery(query)
      .withOffset(0)
      .withLimit(Integer.MAX_VALUE);
    return restClient.get(requestEntry, PoLineCollection.class, requestContext)
      .map(PoLineCollection::getPoLines);
  }

  public Future<PoLine> getPoLineById(String poLineId, RequestContext requestContext) {
    RequestEntry requestEntry = new RequestEntry(ORDER_LINES_BY_ID_ENDPOINT).withId(poLineId);
    return restClient.get(requestEntry, PoLine.class, requestContext)
      .recover(cause -> {
        if (ExceptionUtil.matches(cause, USER_NOT_A_MEMBER_OF_THE_ACQ)) {
          var typeParam = new Parameter().withKey("type").withValue("order");
          throw new HttpException(403, USER_NOT_A_MEMBER_OF_THE_ACQ, List.of(typeParam));
        } else {
          var param = new Parameter().withKey("poLineId").withValue(poLineId);
          var causeParam = new Parameter().withKey("cause").withValue(cause.getMessage());
          throw new HttpException(404, PO_LINE_NOT_FOUND, List.of(param, causeParam));
        }
      });
  }

  public Future<List<PoLine>> getPoLinesByIdAndQuery(List<String> poLineIds, Function<List<String>, String> queryFunction,
      RequestContext requestContext) {
    List<Future<List<PoLine>>> futureList = StreamEx
      .ofSubLists(poLineIds, MAX_IDS_FOR_GET_RQ)
      .map(queryFunction)
      .map(query -> getPoLinesByQuery(query, requestContext))
      .toList();

    return collectResultsOnSuccess(futureList)
      .map(col -> col.stream().flatMap(List::stream).toList());
  }

  public Future<List<PoLine>> getPoLinesByIds(List<String> ids, RequestContext requestContext) {
    return getPoLinesByIdAndQuery(ids, HelperUtils::convertIdsToCqlQuery, requestContext)
      .map(poLines -> {
        if (poLines.size() != ids.size()) {
          List<String> foundIds = poLines.stream().map(PoLine::getId).toList();
          List<String> missingIds = ids.stream().filter(id -> !foundIds.contains(id)).toList();
          logger.error("getPoLinesByIds:: Could not find these po lines by ids: {}", missingIds);
          var param = new Parameter().withKey("poLineIds").withValue(missingIds.toString());
          throw new HttpException(404, PO_LINE_NOT_FOUND, List.of(param));
        }
        return poLines;
      });
  }

  public Future<Void> updatePoLine(PoLine poLine, RequestContext requestContext) {
    RequestEntry requestEntry = new RequestEntry(ORDER_LINES_BY_ID_ENDPOINT).withId(poLine.getId());
    return restClient.put(requestEntry, poLine, requestContext);
  }

  public Future<Void> updatePoLines(List<PoLine> poLines, RequestContext requestContext) {
    var futures = poLines.stream()
      .map(poLine -> updatePoLine(poLine, requestContext)
        .recover(cause -> {
          if (ExceptionUtil.matches(cause, USER_NOT_A_MEMBER_OF_THE_ACQ)) {
            var typeParam = new Parameter().withKey("type").withValue("order");
            var poLineNumberParam = new Parameter().withKey("poLineNumber").withValue(poLine.getPoLineNumber());
            throw new HttpException(403, USER_NOT_A_MEMBER_OF_THE_ACQ, List.of(typeParam, poLineNumberParam));
          } else {
            var causeParam = new Parameter().withKey("cause").withValue(cause.getMessage());
            throw new HttpException(400, PO_LINE_UPDATE_FAILURE, List.of(causeParam));
          }
        }))
      .collect(Collectors.toList());
    return GenericCompositeFuture.join(futures).mapEmpty();
  }

}
