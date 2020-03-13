package org.folio.rest.impl;

import io.vertx.core.Context;
import io.vertx.core.json.JsonObject;
import me.escoffier.vertx.completablefuture.VertxCompletableFuture;
import org.apache.commons.collections4.ListUtils;
import org.folio.invoices.rest.exceptions.HttpException;
import org.folio.invoices.utils.HelperUtils;
import org.folio.rest.acq.model.finance.Transaction;
import org.folio.rest.acq.model.finance.TransactionCollection;
import org.folio.rest.jaxrs.model.FundDistribution;
import org.folio.rest.jaxrs.model.Invoice;
import org.folio.rest.jaxrs.model.InvoiceLine;
import org.folio.rest.jaxrs.model.Parameter;
import org.folio.rest.tools.client.interfaces.HttpClientInterface;
import org.javamoney.moneta.Money;
import org.javamoney.moneta.function.MonetaryOperators;

import javax.money.CurrencyUnit;
import javax.money.Monetary;
import javax.money.MonetaryAmount;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.reducing;
import static java.util.stream.Collectors.toList;
import static org.folio.invoices.utils.ErrorCodes.INVOICE_PAYMENT_FAILURE;
import static org.folio.invoices.utils.HelperUtils.getEndpointWithQuery;
import static org.folio.invoices.utils.ResourcePathResolver.FINANCE_CREDITS;
import static org.folio.invoices.utils.ResourcePathResolver.FINANCE_PAYMENTS;
import static org.folio.invoices.utils.ResourcePathResolver.FINANCE_TRANSACTIONS;
import static org.folio.invoices.utils.ResourcePathResolver.resourcesPath;

public class FinanceHelper extends AbstractHelper {

  private static final String GET_TRANSACTIONS_BY_QUERY = resourcesPath(FINANCE_TRANSACTIONS) + SEARCH_PARAMS;
  private static final String EMPTY = "";

  private final InvoiceLineHelper invoiceLineHelper;

  public FinanceHelper(HttpClientInterface httpClient, Map<String, String> okapiHeaders, Context ctx, String lang) {
    super(httpClient, okapiHeaders, ctx, lang);
    invoiceLineHelper = new InvoiceLineHelper(httpClient, okapiHeaders, ctx, lang);
  }

  /**
   * This method processes payment&credit transactions based on information from invoice (invoice lines & fund distributions)
   * @param invoice {@link Invoice} that should be processed
   * @return {@link CompletableFuture<Void>}
   */
  public CompletableFuture<Void> handlePaymentsAndCredits(Invoice invoice) {
    return isPaymentsAlreadyProcessed(invoice.getId()).thenCompose(isProcessed -> {
        if (isProcessed) {
          return completedFuture(null);
        }
      return invoiceLineHelper.getInvoiceLinesByInvoiceId(invoice.getId())
          .thenCompose(lines -> createPaymentsAndCredits(invoice, getPaymentsAndCredits(invoice, getFundDistributionsGroupedByInvoices(lines))));
    });
  }

  /**
   * This method check if the payments have already been processed
   * @param invoiceId id of invoice for which payments are verifying
   * @return {@link CompletableFuture<Boolean>} with true if payments have already processed otherwise - with false
   */
  public CompletableFuture<Boolean> isPaymentsAlreadyProcessed(String invoiceId) {
    String endpoint = String.format(GET_TRANSACTIONS_BY_QUERY, 0, 0, getEndpointWithQuery("sourceInvoiceId==" + invoiceId, logger), lang);
        return HelperUtils.handleGetRequest(endpoint, httpClient, ctx, okapiHeaders, logger)
      .thenApply(c -> c.mapTo(TransactionCollection.class).getTotalRecords() > 0);
  }

  private Map<InvoiceLine, List<FundDistribution>> getFundDistributionsGroupedByInvoices(List<InvoiceLine> lines) {
    return lines.stream().collect(groupingBy(line -> line, mapping(InvoiceLine::getFundDistributions, reducing(new ArrayList<>(), ListUtils::union))));
  }

  private List<Transaction> getPaymentsAndCredits(Invoice invoice, Map<InvoiceLine, List<FundDistribution>> fundDistributionsSortedByInvoice) {
    return fundDistributionsSortedByInvoice.entrySet().stream().flatMap(x -> x.getValue().stream().map(t -> resolveAndGetTransaction(t, invoice, x.getKey()))).collect(toList());
  }

  private CompletionStage<Void> createPaymentsAndCredits(Invoice invoice, List<Transaction> transactionsList) {
    return VertxCompletableFuture.allOf(ctx, transactionsList.stream().map(tr -> postRecorderWithoutResponseBody(JsonObject.mapFrom(tr), resolveTransactionPath(tr))
      .exceptionally(t -> {
        logger.error("Failed to update invoice with id {}", t, invoice.getId());
        Parameter parameter = new Parameter().withKey("invoiceId").withValue(invoice.getId());
        throw new HttpException(400, INVOICE_PAYMENT_FAILURE.toError().withParameters(Collections.singletonList(parameter)));
      }))
      .toArray(CompletableFuture[]::new));
  }

  private String resolveTransactionPath(Transaction transaction) {
    return resourcesPath(transaction.getTransactionType() == Transaction.TransactionType.PAYMENT ? FINANCE_PAYMENTS : FINANCE_CREDITS);
  }

  private Transaction resolveAndGetTransaction(FundDistribution distribution, Invoice invoice, InvoiceLine invoiceLine) {
    if (distribution.getValue() > 0) {
      return buildPaymentTransaction(distribution, invoice, invoiceLine);
    } else {
      return buildCreditTransaction(distribution, invoice, invoiceLine);
    }
  }

  private Transaction buildPaymentTransaction(FundDistribution distribution, Invoice invoice, InvoiceLine invoiceLine) {
    return buildBaseTransaction(distribution, invoice, invoiceLine)
      .withTransactionType(Transaction.TransactionType.PAYMENT)
      .withToFundId(EMPTY)
      .withFromFundId(distribution.getFundId());
  }

  private Transaction buildCreditTransaction(FundDistribution distribution, Invoice invoice, InvoiceLine invoiceLine) {
    return buildBaseTransaction(distribution, invoice, invoiceLine)
      .withTransactionType(Transaction.TransactionType.CREDIT)
      .withToFundId(distribution.getFundId())
      .withFromFundId(EMPTY);
  }

  private Transaction buildBaseTransaction(FundDistribution distribution, Invoice invoice, InvoiceLine invoiceLine) {
    Transaction transaction = new Transaction();
    CurrencyUnit currency = Monetary.getCurrency(invoice.getCurrency());
    MonetaryAmount amount = Money.of(invoice.getTotal(), currency);
    transaction.setAmount(calculateTransactionAmount(distribution, amount));
    transaction.setCurrency(invoice.getCurrency());
    transaction.setSourceInvoiceId(invoice.getId());
    transaction.setSourceInvoiceLineId(invoiceLine.getId());
    return transaction;
  }

  private double calculateTransactionAmount(FundDistribution distribution, MonetaryAmount estimatedPrice) {
    if (distribution.getDistributionType() == FundDistribution.DistributionType.PERCENTAGE) {
      return estimatedPrice.with(MonetaryOperators.percent(distribution.getValue()))
        .with(MonetaryOperators.rounding())
        .getNumber()
        .doubleValue();
    }
    return Math.abs(distribution.getValue());
  }
}
