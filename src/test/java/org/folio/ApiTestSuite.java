package org.folio;

import static net.mguenther.kafka.junit.EmbeddedKafkaClusterConfig.useDefaults;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.folio.converters.BatchVoucherModelConverterTest;
import org.folio.converters.BatchedVoucherLinesModelConverterTest;
import org.folio.converters.BatchedVoucherModelConverterTest;
import org.folio.dataimport.cache.JobProfileSnapshotCacheTest;
import org.folio.dataimport.handlers.actions.CreateInvoiceEventHandlerTest;
import org.folio.invoices.util.HelperUtilsTest;
import org.folio.jaxb.DefaultJAXBRootElementNameResolverTest;
import org.folio.jaxb.JAXBUtilTest;
import org.folio.jaxb.XMLConverterTest;
import org.folio.rest.RestVerticle;
import org.folio.rest.core.RestClientTest;
import org.folio.rest.impl.BatchGroupsApiTest;
import org.folio.rest.impl.BatchVoucherExportConfigCredentialsTest;
import org.folio.rest.impl.BatchVoucherExportConfigTest;
import org.folio.rest.impl.BatchVoucherExportsApiTest;
import org.folio.rest.impl.BatchVoucherExportsHelperTest;
import org.folio.rest.impl.BatchVoucherImplTest;
import org.folio.rest.impl.DocumentsApiTest;
import org.folio.rest.impl.InvoiceLinesApiTest;
import org.folio.rest.impl.InvoiceLinesProratedAdjustmentsTest;
import org.folio.rest.impl.InvoicesApiTest;
import org.folio.rest.impl.InvoicesProratedAdjustmentsTest;
import org.folio.rest.impl.MockServer;
import org.folio.rest.impl.VoucherLinesApiTest;
import org.folio.rest.impl.VouchersApiTest;
import org.folio.rest.impl.protection.InvoicesProtectionTest;
import org.folio.rest.impl.protection.LinesProtectionTest;
import org.folio.rest.tools.utils.NetworkUtils;
import org.folio.schemas.xsd.BatchVoucherSchemaXSDTest;
import org.folio.services.InvoiceLinesRetrieveServiceTest;
import org.folio.services.InvoiceRetrieveServiceTest;
import org.folio.services.VoucherLinesRetrieveServiceTest;
import org.folio.services.finance.BudgetExpenseClassTest;
import org.folio.services.finance.CurrentFiscalYearServiceTest;
import org.folio.services.finance.ManualCurrencyConversionTest;
import org.folio.services.finance.ManualExchangeRateProviderTest;
import org.folio.services.finance.expense.ExpenseClassRetrieveServiceTest;
import org.folio.services.finance.transaction.BaseTransactionServiceTest;
import org.folio.services.finance.transaction.PendingPaymentWorkflowServiceTest;
import org.folio.services.ftp.FTPVertxCommandLoggerTest;
import org.folio.services.ftp.FtpUploadServiceTest;
import org.folio.services.invoice.InvoiceCancelServiceTest;
import org.folio.services.order.OrderServiceTest;
import org.folio.services.validator.FundAvailabilityHolderValidatorTest;
import org.folio.services.validator.InvoiceLineHolderValidatorTest;
import org.folio.services.voucher.BatchVoucherGenerateServiceTest;
import org.folio.services.voucher.UploadBatchVoucherExportServiceTest;
import org.folio.verticles.DataImportConsumerVerticleTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;

import io.restassured.RestAssured;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import net.mguenther.kafka.junit.EmbeddedKafkaCluster;

public class ApiTestSuite {

  private static final int okapiPort = NetworkUtils.nextFreePort();
  public static final int mockPort = NetworkUtils.nextFreePort();
  public static final String KAFKA_ENV_VALUE = "test-env";
  private static final String KAFKA_HOST = "KAFKA_HOST";
  private static final String KAFKA_PORT = "KAFKA_PORT";
  private static final String KAFKA_ENV = "ENV";
  private static final String OKAPI_URL_KEY = "OKAPI_URL";

  public static EmbeddedKafkaCluster kafkaCluster;
  private static MockServer mockServer;
  private static Vertx vertx;
  private static boolean initialised;

  @BeforeAll
  public static void before() throws InterruptedException, ExecutionException, TimeoutException {
    if (vertx == null) {
      vertx = Vertx.vertx();
    }

    mockServer = new MockServer(mockPort);
    mockServer.start();

    RestAssured.baseURI = "http://localhost:" + okapiPort;
    RestAssured.port = okapiPort;
    RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();

    kafkaCluster = EmbeddedKafkaCluster.provisionWith(useDefaults());
    kafkaCluster.start();
    String[] hostAndPort = kafkaCluster.getBrokerList().split(":");
    System.setProperty(KAFKA_HOST, hostAndPort[0]);
    System.setProperty(KAFKA_PORT, hostAndPort[1]);
    System.setProperty(KAFKA_ENV, KAFKA_ENV_VALUE);
    System.setProperty(OKAPI_URL_KEY, "http://localhost:" + mockPort);

    final JsonObject conf = new JsonObject();
    conf.put("http.port", okapiPort);

    final DeploymentOptions opt = new DeploymentOptions().setConfig(conf);
    CompletableFuture<String> deploymentComplete = new CompletableFuture<>();
    vertx.deployVerticle(RestVerticle.class.getName(), opt, res -> {
      if (res.succeeded()) {
        deploymentComplete.complete(res.result());
      } else {
        deploymentComplete.completeExceptionally(res.cause());
      }
    });
    deploymentComplete.get(60, TimeUnit.SECONDS);
    initialised = true;
  }

  @AfterAll
  public static void after() {
    kafkaCluster.stop();
    mockServer.close();
    vertx.close();
    initialised = false;
  }

  public static boolean isNotInitialised() {
    return !initialised;
  }

  @Nested
  class InvoicesApiTestNested extends InvoicesApiTest {
  }

  @Nested
  class InvoiceLinesApiTestNested extends InvoiceLinesApiTest {
  }

  @Nested
  class VouchersApiTestNested extends VouchersApiTest {
  }

  @Nested
  class VoucherLinesApiTestNested extends VoucherLinesApiTest {
  }

  @Nested
  class DocumentsApiTestNested extends DocumentsApiTest {
  }

  @Nested
  class InvoicesProtectionTestNested extends InvoicesProtectionTest {
  }

  @Nested
  class LinesProtectionTestNested extends LinesProtectionTest {
  }

  @Nested
  class InvoicesProratedAdjustmentsTestNested extends InvoicesProratedAdjustmentsTest {
  }

  @Nested
  class InvoiceLinesProratedAdjustmentsTestNested extends InvoiceLinesProratedAdjustmentsTest {
  }

  @Nested
  class BatchVoucherExportConfigTestNested extends BatchVoucherExportConfigTest {
  }

  @Nested
  class BatchVoucherExportConfigCredentialsTestNested extends BatchVoucherExportConfigCredentialsTest {
  }

  @Nested
  class BatchGroupsApiTestNested extends BatchGroupsApiTest {
  }

  @Nested
  class BatchVoucherSchemaXSDTestNested extends BatchVoucherSchemaXSDTest {
  }

  @Nested
  class BatchVoucherImplTestNested extends BatchVoucherImplTest {
  }

  @Nested
  class BatchedVoucherLinesModelConverterTestNested extends BatchedVoucherLinesModelConverterTest {
  }

  @Nested
  class BatchedVoucherModelConverterTestNested extends BatchedVoucherModelConverterTest {
  }

  @Nested
  class BatchVoucherModelConverterTestNested extends BatchVoucherModelConverterTest {
  }

  @Nested
  class DefaultJAXBRootElementNameResolverTestNested extends DefaultJAXBRootElementNameResolverTest {
  }

  @Nested
  class JAXBUtilTestNested extends JAXBUtilTest {
  }

  @Nested
  class XMLConverterTestNested extends XMLConverterTest {
  }

  @Nested
  class FtpUploadServiceTestNested extends FtpUploadServiceTest {
  }

  @Nested
  class BatchVoucherExportsApiTestNested extends BatchVoucherExportsApiTest {
  }

  @Nested
  class HelperUtilsTestNested extends HelperUtilsTest {
  }

  @Nested
  class InvoiceRetrieveServiceTestNested extends InvoiceRetrieveServiceTest {
  }

  @Nested
  class InvoiceLinesRetrieveServiceTestNested extends InvoiceLinesRetrieveServiceTest {
  }

  @Nested
  class VoucherLinesRetrieveServiceTestNested extends VoucherLinesRetrieveServiceTest {
  }

  @Nested
  class BatchVoucherGenerateServiceTestNested extends BatchVoucherGenerateServiceTest {
  }

  @Nested
  class BatchVoucherExportsHelperTestNested extends BatchVoucherExportsHelperTest {
  }

  @Nested
  class FTPVertxCommandLoggerTestNested extends FTPVertxCommandLoggerTest {
  }

  @Nested
  class UploadBatchVoucherExportServiceTestNested extends UploadBatchVoucherExportServiceTest {
  }

  @Nested
  class InvoiceLineHolderValidatorTestNested extends InvoiceLineHolderValidatorTest {
  }

  @Nested
  class BaseTransactionServiceTestNested extends BaseTransactionServiceTest {
  }

  @Nested
  class RestClientTestNested extends RestClientTest {
  }

  @Nested
  class CurrentFiscalYearServiceTestNested extends CurrentFiscalYearServiceTest {
  }

  @Nested
  class ManualCurrencyConversionTestNested extends ManualCurrencyConversionTest {
  }

  @Nested
  class ManualExchangeRateProviderTestNested extends ManualExchangeRateProviderTest {
  }

  @Nested
  class FundAvailabilityHolderValidatorTestNested extends FundAvailabilityHolderValidatorTest {
  }

  @Nested
  class PendingPaymentWorkflowServiceTestNested extends PendingPaymentWorkflowServiceTest {
  }

  @Nested
  class BudgetExpenseClassTestNested extends BudgetExpenseClassTest {
  }

  @Nested
  class ExpenseClassRetrieveServiceTestNested extends ExpenseClassRetrieveServiceTest {
  }

  @Nested
  class DataImportConsumerVerticleTestNested extends DataImportConsumerVerticleTest {
  }

  @Nested
  class CreateInvoiceEventHandlerTestNested extends CreateInvoiceEventHandlerTest {
  }

  @Nested
  class JobProfileSnapshotCacheTestNested extends JobProfileSnapshotCacheTest {
  }

  @Nested
  class OrderServiceTestNested extends OrderServiceTest {
  }

  @Nested
  class InvoiceCancelServiceTestNested extends InvoiceCancelServiceTest {
  }

}
