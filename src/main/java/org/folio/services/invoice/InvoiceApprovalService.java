package org.folio.services.invoice;

import io.vertx.core.Future;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.collections4.CollectionUtils;
import org.folio.InvoiceWorkflowDataHolderBuilder;
import org.folio.invoices.rest.exceptions.HttpException;
import org.folio.rest.acq.model.Organization;
import org.folio.rest.core.models.RequestContext;
import org.folio.rest.jaxrs.model.Invoice;
import org.folio.rest.jaxrs.model.InvoiceLine;
import org.folio.rest.jaxrs.model.Parameter;
import org.folio.rest.jaxrs.model.Voucher;
import org.folio.services.VendorRetrieveService;
import org.folio.services.configuration.ConfigurationService;
import org.folio.services.finance.budget.BudgetExpenseClassService;
import org.folio.services.finance.fiscalyear.CurrentFiscalYearService;
import org.folio.services.finance.transaction.EncumbranceService;
import org.folio.services.finance.transaction.PendingPaymentWorkflowService;
import org.folio.services.validator.InvoiceValidator;
import org.folio.services.voucher.VoucherCommandService;
import org.folio.services.voucher.VoucherCreationService;
import org.folio.services.voucher.VoucherService;

import java.util.Date;
import java.util.List;

import static io.vertx.core.Future.succeededFuture;
import static java.util.Objects.nonNull;
import static org.folio.invoices.utils.ErrorCodes.ORG_IS_NOT_VENDOR;
import static org.folio.invoices.utils.ErrorCodes.ORG_NOT_FOUND;
import static org.folio.rest.impl.AbstractHelper.SYSTEM_CONFIG_QUERY;
import static org.folio.services.voucher.VoucherCommandService.VOUCHER_NUMBER_PREFIX_CONFIG_QUERY;
import static org.folio.utils.FutureUtils.asFuture;


@Log4j2
public class InvoiceApprovalService {

  private final BudgetExpenseClassService budgetExpenseClassService;
  private final ConfigurationService configurationService;
  private final CurrentFiscalYearService currentFiscalYearService;
  private final EncumbranceService encumbranceService;
  private final InvoiceFundDistributionService invoiceFundDistributionService;
  private final InvoiceLineService invoiceLineService;
  private final InvoiceValidator validator;
  private final InvoiceWorkflowDataHolderBuilder holderBuilder;
  private final PendingPaymentWorkflowService pendingPaymentWorkflowService;
  private final VendorRetrieveService vendorService;
  private final VoucherCommandService voucherCommandService;
  private final VoucherCreationService voucherCreationService;
  private final VoucherService voucherService;


  public InvoiceApprovalService(BudgetExpenseClassService budgetExpenseClassService, ConfigurationService configurationService,
      CurrentFiscalYearService currentFiscalYearService, EncumbranceService encumbranceService,
      InvoiceFundDistributionService invoiceFundDistributionService, InvoiceLineService invoiceLineService,
      InvoiceValidator validator, InvoiceWorkflowDataHolderBuilder holderBuilder,
      PendingPaymentWorkflowService pendingPaymentWorkflowService,
      VendorRetrieveService vendorService,
      VoucherCommandService voucherCommandService, VoucherCreationService voucherCreationService,
      VoucherService voucherService) {
    this.budgetExpenseClassService = budgetExpenseClassService;
    this.configurationService = configurationService;
    this.currentFiscalYearService = currentFiscalYearService;
    this.encumbranceService = encumbranceService;
    this.invoiceFundDistributionService = invoiceFundDistributionService;
    this.invoiceLineService = invoiceLineService;
    this.validator = validator;
    this.holderBuilder = holderBuilder;
    this.pendingPaymentWorkflowService = pendingPaymentWorkflowService;
    this.vendorService = vendorService;
    this.voucherCommandService = voucherCommandService;
    this.voucherCreationService = voucherCreationService;
    this.voucherService = voucherService;
  }

  /**
   * Handles transition of given invoice to {@link Invoice.Status#APPROVED} status.
   * Transition triggers if the current {@link Invoice.Status} is {@link Invoice.Status#REVIEWED} or {@link Invoice.Status#OPEN}
   * and exist at least one {@link InvoiceLine} associated with this invoice
   *
   * @param invoice {@link Invoice}to be approved
   * @return CompletableFuture that indicates when transition is completed
   */
  public Future<Void> approveInvoice(Invoice invoice, List<InvoiceLine> lines, RequestContext requestContext) {
    invoice.setApprovalDate(new Date());
    invoice.setApprovedBy(invoice.getMetadata().getUpdatedByUserId());

    return configurationService.getConfigurationsEntries(requestContext, SYSTEM_CONFIG_QUERY, VOUCHER_NUMBER_PREFIX_CONFIG_QUERY)
      .compose(v -> vendorService.getVendor(invoice.getVendorId(), requestContext))
      .compose(organization -> asFuture(() -> validateBeforeApproval(organization, invoice, lines)))
      .compose(v -> holderBuilder.buildCompleteHolders(invoice, lines, requestContext))
      .compose(holders -> encumbranceService.updateInvoiceLinesEncumbranceLinks(holders,
          holders.get(0).getFiscalYear().getId(), requestContext)
        .compose(linesToUpdate -> invoiceLineService.persistInvoiceLines(linesToUpdate, requestContext))
        .map(v -> holders))
      .compose(holders -> budgetExpenseClassService.checkExpenseClasses(holders, requestContext))
      .compose(holders -> pendingPaymentWorkflowService.handlePendingPaymentsCreation(holders, invoice, requestContext))
      .compose(holders -> prepareVoucher(invoice, requestContext)
        .compose(voucher -> updateVoucherWithSystemCurrency(voucher, lines, requestContext))
        .compose(voucher -> voucherCommandService.updateVoucherWithExchangeRate(voucher, invoice, requestContext))
        .compose(voucher -> invoiceFundDistributionService.getAllFundDistributions(lines, invoice, requestContext)
          .compose(fundDistributions -> voucherCreationService.handleVoucherWithLines(fundDistributions,
            voucher, requestContext))
        ).recover(t -> {
          log.error("approveInvoice:: error after creating the pending payments; rolling back...", t);
          return pendingPaymentWorkflowService.rollbackCreationOfPendingPayments(holders, requestContext)
            .compose(v -> Future.failedFuture(t));
        })
      );
  }

  private void validateBeforeApproval(Organization organization, Invoice invoice, List<InvoiceLine> lines) {
    if (organization == null) {
      throw new HttpException(404, ORG_NOT_FOUND);
    }
    if (Boolean.FALSE.equals(organization.getIsVendor())) {
      var param = new Parameter().withKey("organizationId").withValue(organization.getId());
      log.error("validateBeforeApproval:: Organization '{}' is not vendor", organization.getId());
      throw new HttpException(400, ORG_IS_NOT_VENDOR, List.of(param));
    }

    validator.validateBeforeApproval(invoice, lines);
  }

  /**
   * Prepares a new voucher or updates existing one for further processing
   *
   * @param invoice {@link Invoice} to be approved on the basis of which the voucher is prepared
   * @return completable future with {@link Voucher} on success
   */
  private Future<Voucher> prepareVoucher(Invoice invoice, RequestContext requestContext) {
    return voucherService.getVoucherByInvoiceId(invoice.getId(), requestContext)
      .compose(voucher -> {
        if (nonNull(voucher)) {
          return succeededFuture(voucher);
        }
        return voucherCommandService.buildNewVoucher(invoice, requestContext);
      })
      .map(voucher -> {
        invoice.setVoucherNumber(voucher.getVoucherNumber());
        voucher.setAcqUnitIds(invoice.getAcqUnitIds());
        return withRequiredFields(voucher, invoice);
      });
  }

  /**
   * Updates state of {@link Voucher} linked with processed {@link Invoice}
   *
   * @param voucher {@link Voucher} from voucher-storage related to processed invoice
   * @param invoice invoice {@link Invoice} to be approved
   * @return voucher
   */
  private Voucher withRequiredFields(Voucher voucher, Invoice invoice) {
    voucher.setVoucherDate(new Date());
    voucher.setInvoiceCurrency(invoice.getCurrency());
    voucher.setExportToAccounting(invoice.getExportToAccounting());
    voucher.setAccountingCode(invoice.getAccountingCode());
    voucher.setBatchGroupId(invoice.getBatchGroupId());
    voucher.setEnclosureNeeded(invoice.getEnclosureNeeded());
    voucher.setAccountNo(invoice.getAccountNo());
    voucher.setVendorId(invoice.getVendorId());
    voucher.setType(Voucher.Type.VOUCHER);
    voucher.setStatus(Voucher.Status.AWAITING_PAYMENT);

    return voucher;
  }

  private Future<Voucher> updateVoucherWithSystemCurrency(Voucher voucher, List<InvoiceLine> lines,
      RequestContext requestContext) {
    if (!CollectionUtils.isEmpty(lines) && !CollectionUtils.isEmpty(lines.get(0).getFundDistributions())) {
      String fundId = lines.get(0).getFundDistributions().get(0).getFundId();
      return currentFiscalYearService.getCurrentFiscalYearByFund(fundId, requestContext)
        .map(fiscalYear -> voucher.withSystemCurrency(fiscalYear.getCurrency()));
    }
    return configurationService.getSystemCurrency(requestContext)
      .map(voucher::withSystemCurrency);
  }

}
