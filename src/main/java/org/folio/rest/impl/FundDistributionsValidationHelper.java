package org.folio.rest.impl;

import static org.folio.invoices.utils.ErrorCodes.ADJUSTMENT_IDS_NOT_UNIQUE;

import javax.money.CurrencyUnit;
import javax.money.Monetary;
import javax.money.MonetaryAmount;
import java.util.List;
import java.util.Map;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import org.apache.commons.collections4.CollectionUtils;
import org.folio.invoices.rest.exceptions.HttpException;
import org.folio.invoices.utils.HelperUtils;
import org.folio.rest.jaxrs.model.FundDistribution;
import org.folio.rest.jaxrs.model.Parameter;
import org.folio.rest.jaxrs.model.ValidateFundDistributionsRequest;
import org.folio.services.adjusment.AdjustmentsService;
import org.folio.services.validator.InvoiceValidator;
import org.folio.spring.SpringContextUtil;
import org.javamoney.moneta.Money;
import org.springframework.beans.factory.annotation.Autowired;

public class FundDistributionsValidationHelper extends AbstractHelper {

  @Autowired
  private AdjustmentsService adjustmentsService;
  @Autowired
  private InvoiceValidator validator;

  public FundDistributionsValidationHelper(Map<String, String> okapiHeaders, Context ctx) {
    super(okapiHeaders, ctx);
    SpringContextUtil.autowireDependencies(this, ctx);
  }

  public Future<Void> validateFundDistributions(ValidateFundDistributionsRequest request) {
    return Future.succeededFuture()
      .map(v -> {
        MonetaryAmount subTotal;
        CurrencyUnit currencyUnit = Monetary.getCurrency(request.getCurrency());
        List<FundDistribution> fundDistributionList = request.getFundDistribution();
        if (CollectionUtils.isNotEmpty(request.getAdjustments())) {
          if (validator.isAdjustmentIdsNotUnique(request.getAdjustments())) {
            var parameter = new Parameter().withKey("adjustments").withValue(request.getAdjustments().toString());
            var error = ADJUSTMENT_IDS_NOT_UNIQUE.toError().withParameters(List.of(parameter));
            logger.error("validateFundDistributions:: Adjustment ids is not unique. '{}'", JsonObject.mapFrom(error).encodePrettily());
            throw new HttpException(400, error);
          }
          subTotal = Money.of(request.getSubTotal(), currencyUnit);
          MonetaryAmount adjustmentAndFundTotals = HelperUtils.calculateAdjustmentsTotal(request.getAdjustments(), subTotal);
          Double total = HelperUtils.convertToDoubleWithRounding(adjustmentAndFundTotals.add(subTotal));
          validator.validateFundDistributions(total, fundDistributionList);
        } else {
          validator.validateFundDistributions(request.getSubTotal(), fundDistributionList);
        }
        return null;
      });
  }
}
