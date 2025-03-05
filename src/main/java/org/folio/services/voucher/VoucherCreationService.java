package org.folio.services.voucher;

import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import org.apache.commons.lang3.StringUtils;
import org.folio.invoices.utils.HelperUtils;
import org.folio.models.FundExtNoExpenseClassExtNoPair;
import org.folio.okapi.common.GenericCompositeFuture;
import org.folio.rest.acq.model.finance.ExpenseClass;
import org.folio.rest.acq.model.finance.Fund;
import org.folio.rest.core.models.RequestContext;
import org.folio.rest.jaxrs.model.FundDistribution;
import org.folio.rest.jaxrs.model.Invoice;
import org.folio.rest.jaxrs.model.InvoiceLine;
import org.folio.rest.jaxrs.model.Voucher;
import org.folio.rest.jaxrs.model.VoucherLine;
import org.folio.services.VoucherLineService;
import org.folio.services.finance.FundService;
import org.folio.services.finance.expence.ExpenseClassRetrieveService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import static java.util.Objects.nonNull;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.apache.commons.lang3.StringUtils.isEmpty;
import static org.folio.invoices.utils.HelperUtils.calculateVoucherLineAmount;

public class VoucherCreationService {

  private final ExpenseClassRetrieveService expenseClassRetrieveService;
  private final FundService fundService;
  private final VoucherLineService voucherLineService;
  private final VoucherService voucherService;

  public VoucherCreationService(ExpenseClassRetrieveService expenseClassRetrieveService, FundService fundService,
      VoucherLineService voucherLineService, VoucherService voucherService) {
    this.expenseClassRetrieveService = expenseClassRetrieveService;
    this.fundService = fundService;
    this.voucherLineService = voucherLineService;
    this.voucherService = voucherService;
  }

  /**
   * Handles creation (or update) of prepared voucher and voucher lines creation
   *
   * @param fundDistributions {@link List < FundDistribution >} associated with processed invoice
   * @param voucher           associated with processed invoice
   * @return CompletableFuture that indicates when handling is completed
   */
  public Future<Void> handleVoucherWithLines(List<FundDistribution> fundDistributions, Voucher voucher, RequestContext requestContext) {
    return groupFundDistrosByExternalAcctNo(fundDistributions, requestContext)
      .map(fundDistrosGroupedByExternalAcctNo -> buildVoucherLineRecords(fundDistrosGroupedByExternalAcctNo, voucher))
      .compose(voucherLines -> {
        Double calculatedAmount = HelperUtils.calculateVoucherAmount(voucher, voucherLines);
        voucher.setAmount(calculatedAmount);
        return handleVoucher(voucher, requestContext)
          .onSuccess(voucherWithId -> populateVoucherId(voucherLines, voucherWithId))
          .compose(v -> createVoucherLinesRecords(voucherLines, requestContext));
      });
  }

  /**
   * Prepares the data necessary for the generation of voucher lines based on the invoice lines found
   *
   * @param fundDistributions {@link List< InvoiceLine >} associated with processed {@link Invoice}
   * @return {@link InvoiceLine#fundDistributions} grouped by {@link Fund#externalAccountNo}
   */
  private Future<Map<FundExtNoExpenseClassExtNoPair, List<FundDistribution>>> groupFundDistrosByExternalAcctNo(
      List<FundDistribution> fundDistributions, RequestContext requestContext) {

    Map<String, List<FundDistribution>> fundDistrosGroupedByFundId = groupFundDistrosByFundId(fundDistributions);
    var groupedFundDistrosFuture = fundService.getFunds(fundDistrosGroupedByFundId.keySet(), requestContext)
      .map(this::groupFundsByExternalAcctNo);
    var fundsGroupedByExternalAcctNoFuture = groupFundDistrByFundIdByExpenseClassExtNo(fundDistributions, requestContext);

    return CompositeFuture.join(groupedFundDistrosFuture, fundsGroupedByExternalAcctNoFuture)
      .map(cf -> mapExternalAcctNoToFundDistros(fundsGroupedByExternalAcctNoFuture.result(), groupedFundDistrosFuture.result()));
  }

  private Map<String, List<Fund>> groupFundsByExternalAcctNo(List<Fund> funds) {
    return funds.stream().collect(groupingBy(Fund::getExternalAccountNo));
  }

  private Map<FundExtNoExpenseClassExtNoPair, List<FundDistribution>> mapExternalAcctNoToFundDistros(
    Map<String, Map<String, List<FundDistribution>>> fundDistrosGroupedByFundIdAndExpenseClassExtNo,
    Map<String, List<Fund>> fundsGroupedByExternalAccountNo) {
    Map<FundExtNoExpenseClassExtNoPair, List<FundDistribution>> groupedFundDistribution = new HashMap<>();
    for (Map.Entry<String, List<Fund>> fundExternalAccountNoPair : fundsGroupedByExternalAccountNo.entrySet()) {
      String fundExternalAccountNo = fundExternalAccountNoPair.getKey();
      for (Fund fund : fundExternalAccountNoPair.getValue()) {
        Map<String, List<FundDistribution>> fundDistrsExpenseClassExtNo = fundDistrosGroupedByFundIdAndExpenseClassExtNo.get(fund.getId());
        for (Map.Entry<String, List<FundDistribution>> fundDistrs : fundDistrsExpenseClassExtNo.entrySet()) {
          String expenseClassExtAccountNo = fundDistrs.getKey();
          FundExtNoExpenseClassExtNoPair key = new FundExtNoExpenseClassExtNoPair(fundExternalAccountNo, expenseClassExtAccountNo);
          List<FundDistribution> fundDistributions = fundDistrs.getValue();
          updateFundDistributionsWithExpenseClassCode(fund, fundDistributions);
          Optional.ofNullable(groupedFundDistribution.get(key)).ifPresentOrElse(
            value -> value.addAll(fundDistributions), () -> groupedFundDistribution.put(key, fundDistributions));
        }
      }
    }
    return groupedFundDistribution;
  }

  private Map<String, List<FundDistribution>> groupFundDistrosByFundId(List<FundDistribution> fundDistributions) {
    return fundDistributions.stream()
      .collect(groupingBy(FundDistribution::getFundId));
  }

  private Future<Map<String, Map<String, List<FundDistribution>>>> groupFundDistrByFundIdByExpenseClassExtNo(
      List<FundDistribution> fundDistrs, RequestContext requestContext) {
    List<String> expenseClassIds = fundDistrs.stream()
      .map(FundDistribution::getExpenseClassId)
      .filter(Objects::nonNull)
      .distinct()
      .collect(toList());
    return expenseClassRetrieveService.getExpenseClasses(expenseClassIds, requestContext)
      .map(expenseClasses -> expenseClasses.stream().collect(toMap(ExpenseClass::getId, Function.identity())))
      .map(expenseClassByIds ->
        fundDistrs.stream()
          .map(fd -> updateWithExpenseClassCode(fd, expenseClassByIds))
          .collect(groupingBy(FundDistribution::getFundId,
            groupingBy(getExpenseClassExtNo(expenseClassByIds)))
          )
      );
  }

  private FundDistribution updateWithExpenseClassCode(FundDistribution fundDistribution, Map<String, ExpenseClass> expenseClassByIds) {
    if (fundDistribution.getExpenseClassId() != null && !expenseClassByIds.isEmpty()) {
      String expenseClassName = expenseClassByIds.get(fundDistribution.getExpenseClassId()).getCode();
      fundDistribution.setCode(expenseClassName);
    } else {
      fundDistribution.setCode("");
    }
    return fundDistribution;
  }

  private void updateFundDistributionsWithExpenseClassCode(Fund fund, List<FundDistribution> fundDistributions) {
    fundDistributions.forEach(fundDistribution -> {
      String fundCode = isEmpty(fundDistribution.getCode()) ? fund.getCode() : fund.getCode() + "-" + fundDistribution.getCode();
      fundDistribution
        .setCode(fundCode);
    });
  }

  /**
   * If {@link Voucher} has an id, then the record exists in the voucher-storage and must be updated,
   * otherwise a new {@link Voucher} record must be created.
   * <p>
   * If {@link Voucher} record exists, it means that there may be voucher lines associated with this {@link Voucher} that should be deleted.
   *
   * @param voucher Voucher for handling
   * @return {@link Voucher} with id
   */
  private Future<Voucher> handleVoucher(Voucher voucher, RequestContext requestContext) {
    if (nonNull(voucher.getId())) {
      return voucherService.updateVoucher(voucher.getId(), voucher, requestContext)
        .compose(aVoid -> deleteVoucherLinesIfExist(voucher.getId(), requestContext))
        .map(aVoid -> voucher);
    } else {
      return voucherService.createVoucher(voucher, requestContext);
    }
  }

  /**
   * Removes the voucher lines associated with the voucher, if present.
   *
   * @param voucherId Id of {@link Voucher} used to find the voucher lines.
   * @return CompletableFuture that indicates when deletion is completed
   */
  private Future<Void> deleteVoucherLinesIfExist(String voucherId, RequestContext requestContext) {
    return getVoucherLineIdsByVoucherId(voucherId, requestContext)
      .compose(ids -> GenericCompositeFuture.join(ids.stream()
        .map(lineId -> voucherLineService.deleteVoucherLine(lineId, requestContext))
        .collect(toList())))
      .mapEmpty();

  }

  private Future<List<String>> getVoucherLineIdsByVoucherId(String voucherId, RequestContext requestContext) {
    String query = "voucherId==" + voucherId;
    return voucherLineService.getVoucherLines(Integer.MAX_VALUE, 0, query, requestContext)
      .map(voucherLineCollection -> voucherLineCollection.getVoucherLines().
        stream()
        .map(org.folio.rest.acq.model.VoucherLine::getId)
        .collect(toList())
      );
  }

  private void populateVoucherId(List<VoucherLine> voucherLines, Voucher voucher) {
    voucherLines.forEach(voucherLine -> voucherLine.setVoucherId(voucher.getId()));
  }

  private Future<Void> createVoucherLinesRecords(List<VoucherLine> voucherLines, RequestContext requestContext) {
    var futures = voucherLines.stream()
      .map(lineId -> voucherLineService.createVoucherLine(lineId, requestContext))
      .collect(toList());
    return GenericCompositeFuture.join(futures).mapEmpty();
  }

  private Function<FundDistribution, String> getExpenseClassExtNo(Map<String, ExpenseClass> expenseClassByIds) {
    return fundDistrsP -> Optional.ofNullable(expenseClassByIds.get(fundDistrsP.getExpenseClassId()))
      .map(ExpenseClass::getExternalAccountNumberExt)
      .orElse(EMPTY);
  }

  private List<VoucherLine> buildVoucherLineRecords(Map<FundExtNoExpenseClassExtNoPair,
      List<FundDistribution>> fundDistroGroupedByExternalAcctNo, Voucher voucher) {
    return fundDistroGroupedByExternalAcctNo.entrySet().stream()
      .map(entry -> buildVoucherLineRecord(entry, voucher.getSystemCurrency()))
      .collect(Collectors.toList());
  }

  private VoucherLine buildVoucherLineRecord(Map.Entry<FundExtNoExpenseClassExtNoPair, List<FundDistribution>> fundDistroAcctNoEntry,
      String systemCurrency) {
    String externalAccountNumber = fundDistroAcctNoEntry.getKey().toString();
    List<FundDistribution> fundDistributions = fundDistroAcctNoEntry.getValue();

    double voucherLineAmount = calculateVoucherLineAmount(fundDistroAcctNoEntry.getValue(), systemCurrency);

    return new VoucherLine()
      .withExternalAccountNumber(externalAccountNumber)
      .withFundDistributions(fundDistributions)
      .withSourceIds(collectInvoiceLineIds(fundDistributions))
      .withAmount(voucherLineAmount);
  }

  private List<String> collectInvoiceLineIds(List<FundDistribution> fundDistributions) {
    return fundDistributions
      .stream()
      .filter(fundDistribution -> StringUtils.isNotEmpty(fundDistribution.getInvoiceLineId()))
      .map(FundDistribution::getInvoiceLineId)
      .distinct()
      .collect(toList());
  }

}
