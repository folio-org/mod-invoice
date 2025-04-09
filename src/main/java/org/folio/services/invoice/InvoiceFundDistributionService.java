package org.folio.services.invoice;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import org.folio.invoices.utils.HelperUtils;
import org.folio.rest.core.models.RequestContext;
import org.folio.rest.jaxrs.model.FundDistribution;
import org.folio.rest.jaxrs.model.Invoice;
import org.folio.rest.jaxrs.model.InvoiceLine;
import org.folio.services.adjusment.AdjustmentsService;
import org.folio.services.configuration.ConfigurationService;
import org.folio.services.exchange.CacheableExchangeRateService;
import org.folio.services.exchange.CentralExchangeRateProvider;
import org.javamoney.moneta.Money;

import javax.money.MonetaryAmount;
import javax.money.convert.CurrencyConversion;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static javax.money.Monetary.getDefaultRounding;
import static org.folio.invoices.utils.HelperUtils.getAdjustmentFundDistributionAmount;
import static org.folio.invoices.utils.HelperUtils.getFundDistributionAmount;

public class InvoiceFundDistributionService {

  private final AdjustmentsService adjustmentsService;
  private final ConfigurationService configurationService;
  private final CacheableExchangeRateService cacheableExchangeRateService;

  public InvoiceFundDistributionService(AdjustmentsService adjustmentsService,
                                        ConfigurationService configurationService,
                                        CacheableExchangeRateService cacheableExchangeRateService) {
    this.adjustmentsService = adjustmentsService;
    this.configurationService = configurationService;
    this.cacheableExchangeRateService = cacheableExchangeRateService;
  }

  public Future<List<FundDistribution>> getAllFundDistributions(List<InvoiceLine> invoiceLines, Invoice invoice,
                                                                RequestContext requestContext) {
    return configurationService.getSystemCurrency(requestContext)
      .compose(systemCurrency -> cacheableExchangeRateService.getExchangeRate(invoice.getCurrency(), systemCurrency, invoice.getExchangeRate(), requestContext)
        .compose(exchangeRate -> {
          var conversionQuery = HelperUtils.buildConversionQuery(invoice.getCurrency(), systemCurrency, exchangeRate.getExchangeRate());
          var exchangeRateProvider = new CentralExchangeRateProvider();
          var conversion = exchangeRateProvider.getCurrencyConversion(conversionQuery);
          var fundDistributions = getInvoiceLineFundDistributions(invoiceLines, invoice, conversion);
          fundDistributions.addAll(getAdjustmentFundDistributions(invoice, conversion));
          return Future.succeededFuture(fundDistributions);
        }));
  }

  private List<FundDistribution> getInvoiceLineFundDistributions(List<InvoiceLine> invoiceLines, Invoice invoice,
                                                                 CurrencyConversion conversion) {
    Map<InvoiceLine, List<FundDistribution>> fdsByLine = invoiceLines.stream()
      .collect(toMap(Function.identity(), InvoiceLine::getFundDistributions));

    return fdsByLine.entrySet()
      .stream()
      .map(invoiceLine -> {
        var invoiceLineTotalWithConversion = Money.of(invoiceLine.getKey().getTotal(), invoice.getCurrency()).with(conversion);
        List<FundDistribution> fds = invoiceLine.getValue().stream()
          .map(fD -> JsonObject.mapFrom(fD)
            .mapTo(FundDistribution.class)
            .withInvoiceLineId(invoiceLine.getKey().getId())
            .withDistributionType(FundDistribution.DistributionType.AMOUNT)
            .withValue(getFundDistributionAmountWithConversion(fD, invoiceLineTotalWithConversion, conversion)))
          .collect(toList());
        return getRedistributedFunds(fds, invoiceLineTotalWithConversion, conversion);
      })
      .flatMap(Collection::stream)
      .collect(Collectors.toList());
  }

  private List<FundDistribution> getRedistributedFunds(List<FundDistribution> fds, MonetaryAmount expectedTotal,
                                                       CurrencyConversion conversion) {
    MonetaryAmount actualDistributedAmount = fds.stream()
      .map(FundDistribution::getValue)
      .map(amount -> Money.of(amount, conversion.getCurrency()))
      .reduce(Money::add)
      .orElseGet(() -> Money.of(0d, conversion.getCurrency()));

    MonetaryAmount remainedAmount = expectedTotal.subtract(actualDistributedAmount);

    if (remainedAmount.isNegative()) {
      FundDistribution fdForUpdate = fds.getFirst();
      // Subtract from the first fd
      var currentFdAmount = Money.of(fdForUpdate.getValue(), conversion.getCurrency());
      var updatedFdAmount = currentFdAmount.subtract(remainedAmount.abs());
      fdForUpdate.setValue(updatedFdAmount.getNumber().doubleValue());
    } else if (remainedAmount.isPositive()) {
      // add to the last fd
      FundDistribution fdForUpdate = fds.getLast();
      var currentFdAmount = Money.of(fdForUpdate.getValue(), conversion.getCurrency());
      var updatedFdAmount = currentFdAmount.add(remainedAmount);
      fdForUpdate.setValue(updatedFdAmount.getNumber().doubleValue());
    }
    return fds;
  }

  private double getFundDistributionAmountWithConversion(FundDistribution fundDistribution, MonetaryAmount totalAmount,
                                                         CurrencyConversion conversion) {
    MonetaryAmount amount = getFundDistributionAmount(fundDistribution, totalAmount).with(conversion).with(getDefaultRounding());
    return amount.getNumber().doubleValue();
  }

  private List<FundDistribution> getAdjustmentFundDistributions(Invoice invoice, CurrencyConversion conversion) {
    return adjustmentsService.getNotProratedAdjustments(invoice).stream()
      .flatMap(adjustment -> adjustment.getFundDistributions()
        .stream()
        .map(fundDistribution -> JsonObject.mapFrom(fundDistribution).mapTo(FundDistribution.class)
          .withValue(getAdjustmentFundDistributionAmount(fundDistribution, adjustment, invoice).with(conversion).getNumber().doubleValue())
          .withDistributionType(FundDistribution.DistributionType.AMOUNT)
        )
      )
      .collect(toList());
  }
}
