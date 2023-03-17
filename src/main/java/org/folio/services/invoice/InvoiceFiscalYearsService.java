package org.folio.services.invoice;

import io.vertx.core.Future;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.InvoiceWorkflowDataHolderBuilder;
import org.folio.invoices.rest.exceptions.HttpException;
import org.folio.models.InvoiceWorkflowDataHolder;
import org.folio.rest.acq.model.finance.Budget;
import org.folio.rest.core.models.RequestContext;
import org.folio.rest.jaxrs.model.Error;
import org.folio.rest.jaxrs.model.FiscalYearCollection;
import org.folio.rest.jaxrs.model.Invoice;
import org.folio.rest.jaxrs.model.InvoiceLine;
import org.folio.rest.jaxrs.model.Parameter;
import org.folio.services.finance.budget.BudgetService;
import org.folio.services.finance.fiscalyear.FiscalYearService;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static java.util.function.Function.identity;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static org.folio.invoices.utils.ErrorCodes.COULD_NOT_FIND_VALID_FISCAL_YEAR;
import static org.folio.invoices.utils.HelperUtils.convertIdsToCqlQuery;

public class InvoiceFiscalYearsService {
  private static final Logger log = LogManager.getLogger();

  @Autowired
  private InvoiceWorkflowDataHolderBuilder holderBuilder;
  @Autowired
  private BudgetService budgetService;
  @Autowired
  private FiscalYearService fiscalYearService;

  public InvoiceFiscalYearsService(InvoiceWorkflowDataHolderBuilder holderBuilder, BudgetService budgetService,
      FiscalYearService fiscalYearService) {
    this.holderBuilder = holderBuilder;
    this.budgetService = budgetService;
    this.fiscalYearService = fiscalYearService;
  }

  public Future<FiscalYearCollection> getFiscalYearsByInvoiceAndLines(Invoice invoice, List<InvoiceLine> lines,
      RequestContext requestContext) {
    List<InvoiceWorkflowDataHolder> dataHolders = holderBuilder.buildHoldersSkeleton(lines, invoice);
    List<String> fundIds = dataHolders.stream().map(InvoiceWorkflowDataHolder::getFundId).distinct().collect(toList());
    if (fundIds.size() == 0)
      throwCouldNotFindValidFiscalYearError(invoice.getId(), fundIds);
    return budgetService.getBudgetListByFundIds(fundIds, requestContext)
      .compose(budgets -> getFiscalYearsByFundIdsAndBudgets(fundIds, budgets, invoice.getId(), requestContext));
  }

  private Future<FiscalYearCollection> getFiscalYearsByFundIdsAndBudgets(List<String> fundIds, List<Budget> budgets,
      String invoiceId, RequestContext requestContext) {
    List<String> fiscalYearIds = budgets.stream()
      .map(Budget::getFiscalYearId)
      .distinct()
      .collect(toList());
    Map<String, List<String>> fundIdsByFiscalYearId = fiscalYearIds.stream()
      .collect(toMap(
        identity(),
        fiscalYearId -> budgets.stream()
          .filter(budget -> fiscalYearId.equals(budget.getFiscalYearId()))
          .map(Budget::getFundId)
          .distinct()
          .collect(toList())
      ));
    List<String> possibleFiscalYearIds = fiscalYearIds.stream()
      .filter(fiscalYearId -> fundIdsByFiscalYearId.get(fiscalYearId).size() == fundIds.size())
      .collect(toList());
    if (possibleFiscalYearIds.size() == 0)
      throwCouldNotFindValidFiscalYearError(invoiceId, fundIds);
    String queryIds = convertIdsToCqlQuery(possibleFiscalYearIds);
    LocalDate now = Instant.now().atOffset(ZoneOffset.UTC).toLocalDate();
    String queryDate = "periodStart<=\"" + now + "\" sortby periodStart/sort.descending";
    String query = String.format("(%s) AND %s", queryIds, queryDate);
    return fiscalYearService.getFiscalYearCollectionByQuery(query, requestContext)
      .map(fyCollection -> {
        if (fyCollection.getTotalRecords() == 0)
          throwCouldNotFindValidFiscalYearError(invoiceId, fundIds);
        return fyCollection;
      });
  }

  private void throwCouldNotFindValidFiscalYearError(String invoiceId, List<String> fundIds) {
    List<Parameter> parameters = List.of(new Parameter().withKey("invoiceId").withValue(invoiceId),
      new Parameter().withKey("fundIds").withValue(fundIds.toString()));
    Error error = COULD_NOT_FIND_VALID_FISCAL_YEAR.toError()
      .withParameters(parameters);
    log.error(error.getMessage() + " - " +
      parameters.stream().map(p -> p.getKey() + "=" + p.getValue()).collect(joining(", ")));
    throw new HttpException(422, error);
  }

}
