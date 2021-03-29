package org.folio.services.finance;

import static java.util.stream.Collectors.toList;
import static one.util.streamex.StreamEx.ofSubLists;
import static org.apache.commons.collections4.CollectionUtils.isNotEmpty;
import static org.folio.invoices.utils.ErrorCodes.EXTERNAL_ACCOUNT_NUMBER_IS_MISSING;
import static org.folio.invoices.utils.ErrorCodes.FUNDS_NOT_FOUND;
import static org.folio.invoices.utils.HelperUtils.collectResultsOnSuccess;
import static org.folio.invoices.utils.HelperUtils.convertIdsToCqlQuery;
import static org.folio.invoices.utils.ResourcePathResolver.FUNDS;
import static org.folio.invoices.utils.ResourcePathResolver.resourcesPath;
import static org.folio.rest.RestConstants.MAX_IDS_FOR_GET_RQ;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.stream.Collectors;

import org.folio.invoices.rest.exceptions.HttpException;
import org.folio.invoices.utils.ErrorCodes;
import org.folio.invoices.utils.HelperUtils;
import org.folio.rest.acq.model.finance.CompositeFund;
import org.folio.rest.acq.model.finance.Fund;
import org.folio.rest.acq.model.finance.FundCollection;
import org.folio.rest.core.RestClient;
import org.folio.rest.core.models.RequestContext;
import org.folio.rest.core.models.RequestEntry;
import org.folio.rest.jaxrs.model.Error;
import org.folio.rest.jaxrs.model.Parameter;

public class FundService {

  private static final String FUNDS_ENDPOINT = resourcesPath(FUNDS);
  private static final String FUNDS_BY_ID_ENDPOINT = FUNDS_ENDPOINT + "/{id}";

   private final RestClient restClient;

  public FundService(RestClient restClient) {
    this.restClient = restClient;
  }

  public CompletableFuture<List<Fund>> getFunds(Collection<String> fundIds, RequestContext requestContext) {
    return collectResultsOnSuccess(ofSubLists(new ArrayList<>(fundIds), MAX_IDS_FOR_GET_RQ)
      .map(ids -> getFundsByIds(ids, requestContext))
      .toList())
      .thenApply(lists -> lists.stream().flatMap(Collection::stream)
      .collect(Collectors.toList()));
  }


  private CompletableFuture<List<Fund>> getFundsByIds(Collection<String> ids, RequestContext requestContext) {
    String query = convertIdsToCqlQuery(ids);
    RequestEntry requestEntry = new RequestEntry(FUNDS_ENDPOINT)
        .withQuery(query)
        .withOffset(0)
        .withLimit(MAX_IDS_FOR_GET_RQ);
    return restClient.get(requestEntry, requestContext, FundCollection.class)
      .thenApply(fundCollection -> verifyThatAllFundsFound(fundCollection.getFunds(), ids));
  }

  public CompletableFuture<Fund> getFundById(String fundId, RequestContext requestContext) {
    RequestEntry requestEntry = new RequestEntry(FUNDS_BY_ID_ENDPOINT).withId(fundId);
    return restClient.get(requestEntry, requestContext, CompositeFund.class)
      .thenApply(CompositeFund::getFund)
      .exceptionally(t -> {
        Throwable cause = t.getCause() == null ? t : t.getCause();
        if (HelperUtils.isNotFound(cause)) {
          List<Parameter> parameters = Collections.singletonList(new Parameter().withValue(fundId).withKey("funds"));
          throw new HttpException(404, FUNDS_NOT_FOUND.toError().withParameters(parameters));
        }
        throw new CompletionException(cause);
      });
  }

  private List<Fund> verifyThatAllFundsFound(List<Fund> existingFunds, Collection<String> fundIds) {
    List<String> fundIdsWithoutExternalAccNo = getFundIdsWithoutExternalAccNo(existingFunds);
    if (isNotEmpty(fundIdsWithoutExternalAccNo)) {
      throw new HttpException(500, buildFundError(fundIdsWithoutExternalAccNo, EXTERNAL_ACCOUNT_NUMBER_IS_MISSING));
    }
    if (fundIds.size() != existingFunds.size()) {
      List<String> idsNotFound = collectFundIdsThatWasNotFound(existingFunds, fundIds);
      if (isNotEmpty(idsNotFound)) {
        throw new HttpException(404, buildFundError(idsNotFound, FUNDS_NOT_FOUND));
      }
    }
    return existingFunds;
  }

  private List<String> getFundIdsWithoutExternalAccNo(List<Fund> existingFunds) {
    return existingFunds.stream()
      .filter(fund -> Objects.isNull(fund.getExternalAccountNo()))
      .map(Fund::getId)
      .collect(toList());
  }

  private List<String> collectFundIdsThatWasNotFound(List<Fund> existingFunds, Collection<String> fundIds) {
    return fundIds.stream()
      .filter(id -> existingFunds.stream()
        .map(Fund::getId)
        .noneMatch(existingId -> existingId.equals(id)))
      .collect(toList());
  }

  private Error buildFundError(List<String> fundIds, ErrorCodes errorCode) {
    String fundIdsString = String.join(", ", fundIds);
    Parameter parameter = new Parameter().withKey("fundIds").withValue(fundIdsString);
    return errorCode.toError().withParameters(Collections.singletonList(parameter));
  }

}
