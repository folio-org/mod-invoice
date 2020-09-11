package org.folio.services.finance;

import static java.util.stream.Collectors.toList;
import static one.util.streamex.StreamEx.ofSubLists;
import static org.folio.invoices.utils.ErrorCodes.FUNDS_NOT_FOUND;
import static org.folio.invoices.utils.HelperUtils.collectResultsOnSuccess;
import static org.folio.invoices.utils.HelperUtils.convertIdsToCqlQuery;
import static org.folio.rest.RestConstants.MAX_IDS_FOR_GET_RQ;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.stream.Collectors;

import org.apache.commons.collections4.CollectionUtils;
import org.folio.invoices.rest.exceptions.HttpException;
import org.folio.invoices.utils.HelperUtils;
import org.folio.rest.acq.model.finance.Fund;
import org.folio.rest.acq.model.finance.FundCollection;
import org.folio.rest.core.RestClient;
import org.folio.rest.core.models.RequestContext;
import org.folio.rest.jaxrs.model.Parameter;

import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

public class FundService {

  private static final Logger logger = LoggerFactory.getLogger(FundService.class);

  private final RestClient fundRestClient;

  public FundService(RestClient fundRestClient) {
    this.fundRestClient = fundRestClient;
  }

  public CompletableFuture<List<Fund>> getFunds(List<String> fundIds, RequestContext requestContext) {
    return collectResultsOnSuccess(ofSubLists(fundIds, MAX_IDS_FOR_GET_RQ)
      .map(ids -> getFundsByIds(ids, requestContext))
      .toList())
      .thenApply(lists -> lists.stream().flatMap(Collection::stream)
      .collect(Collectors.toList()));
  }


  private CompletableFuture<List<Fund>> getFundsByIds(List<String> ids, RequestContext requestContext) {
    String query = convertIdsToCqlQuery(ids);
    return fundRestClient.get(query, 0, MAX_IDS_FOR_GET_RQ, requestContext, FundCollection.class)
      .thenApply(fundCollection -> {
        if (ids.size() == fundCollection.getFunds().size()) {
          return fundCollection.getFunds();
        }
        String missingIds = String.join(", ", CollectionUtils.subtract(ids, fundCollection.getFunds().stream().map(Fund::getId).collect(toList())));
        logger.info("Funds with ids - {} are missing.", missingIds);
        throw new HttpException(404, FUNDS_NOT_FOUND.toError().withParameters(Collections.singletonList(new Parameter().withKey("funds").withValue(missingIds))));
      });
  }

  public CompletableFuture<Fund> getFundById(String fundId, RequestContext requestContext) {
    return fundRestClient.getById(fundId, requestContext, Fund.class)
      .exceptionally(t -> {
        Throwable cause = t.getCause() == null ? t : t.getCause();
        if (HelperUtils.isNotFound(cause)) {
          List<Parameter> parameters = Collections.singletonList(new Parameter().withValue(fundId).withKey("fundId"));
          throw new HttpException(404, FUNDS_NOT_FOUND.toError().withParameters(parameters));
        }
        throw new CompletionException(cause);
      });
  }

}
