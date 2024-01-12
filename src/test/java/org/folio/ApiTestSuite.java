package org.folio;

import static net.mguenther.kafka.junit.EmbeddedKafkaClusterConfig.defaultClusterConfig;
import static org.folio.dataimport.util.RestUtil.OKAPI_TENANT_HEADER;
import static org.folio.rest.RestVerticle.OKAPI_HEADER_TENANT;
import static org.folio.rest.RestVerticle.OKAPI_HEADER_TOKEN;

import io.restassured.RestAssured;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.mguenther.kafka.junit.EmbeddedKafkaCluster;
import org.folio.builders.InvoiceWorkFlowDataHolderBuilderTest;
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
import org.folio.rest.impl.BatchGroupsApiTest;
import org.folio.rest.impl.BatchVoucherExportConfigCredentialsTest;
import org.folio.rest.impl.BatchVoucherExportConfigTest;
import org.folio.rest.impl.BatchVoucherExportsApiTest;
import org.folio.rest.impl.BatchVoucherImplTest;
import org.folio.rest.impl.DocumentsApiTest;
import org.folio.rest.impl.InvoiceLinesApiTest;
import org.folio.rest.impl.InvoiceLinesProratedAdjustmentsTest;
import org.folio.rest.impl.InvoicesApiTest;
import org.folio.rest.impl.InvoicesProratedAdjustmentsTest;
import org.folio.rest.impl.MockServer;
import org.folio.rest.impl.TenantAPI;
import org.folio.rest.impl.VoucherLinesApiTest;
import org.folio.rest.impl.VouchersApiTest;
import org.folio.rest.impl.protection.InvoicesProtectionTest;
import org.folio.rest.impl.protection.LinesProtectionTest;
import org.folio.rest.jaxrs.model.TenantAttributes;
import org.folio.rest.jaxrs.model.TenantJob;
import org.folio.rest.persist.Criteria.Criterion;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.tools.utils.Envs;
import org.folio.rest.tools.utils.ModuleName;
import org.folio.rest.tools.utils.NetworkUtils;
import org.folio.schemas.xsd.BatchVoucherSchemaXSDTest;
import org.folio.services.InvoiceLinesRetrieveServiceTest;
import org.folio.services.InvoiceRetrieveServiceTest;
import org.folio.services.VoucherLineServiceTest;
import org.folio.services.finance.CurrentFiscalYearServiceTest;
import org.folio.services.finance.ManualCurrencyConversionTest;
import org.folio.services.finance.ManualExchangeRateProviderTest;
import org.folio.services.finance.budget.BudgetExpenseClassTest;
import org.folio.services.finance.budget.BudgetServiceTest;
import org.folio.services.finance.expense.ExpenseClassRetrieveServiceTest;
import org.folio.services.finance.transaction.BaseTransactionServiceTest;
import org.folio.services.finance.transaction.EncumbranceServiceTest;
import org.folio.services.finance.transaction.PendingPaymentWorkflowServiceTest;
import org.folio.services.ftp.FTPVertxCommandLoggerTest;
import org.folio.services.ftp.FtpUploadServiceTest;
import org.folio.services.ftp.SftpUploadServiceTest;
import org.folio.services.invoice.InvoiceCancelServiceTest;
import org.folio.services.invoice.InvoiceFiscalYearsServiceTest;
import org.folio.services.order.OrderServiceTest;
import org.folio.services.validator.FundAvailabilityHolderValidatorTest;
import org.folio.services.validator.InvoiceLineHolderValidatorTest;
import org.folio.services.validator.ProtectedFieldsValidatorTest;
import org.folio.services.voucher.BatchVoucherGenerateServiceTest;
import org.folio.services.voucher.UploadBatchVoucherExportServiceTest;
import org.folio.verticles.DataImportConsumerVerticleTest;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.jupiter.api.Nested;
import org.testcontainers.containers.PostgreSQLContainer;

public class ApiTestSuite {

  public static final String POSTGRES_IMAGE = "postgres:12-alpine";
  private static PostgreSQLContainer<?> postgresSQLContainer;

  private static final String INVOICES_TABLE = "records_invoices";
  protected static final String TOKEN = "token";
  private static final String HTTP_PORT = "http.port";
  private static String useExternalDatabase;
  public static final String TENANT_ID = "test_tenant";
  protected static RequestSpecification spec;

  protected static final String okapiUserIdHeader = UUID.randomUUID().toString();
  public static final String OKAPI_URL_ENV = "OKAPI_URL";
  public static final int mockPort = NetworkUtils.nextFreePort();
  public static final int okapiPort = NetworkUtils.nextFreePort();
  protected static final String OKAPI_URL = "http://localhost:" + okapiPort;
  public static final String KAFKA_ENV_VALUE = "test-env";
  private static final String KAFKA_HOST = "KAFKA_HOST";
  private static final String KAFKA_PORT = "KAFKA_PORT";
  private static final String KAFKA_ENV = "ENV";
  private static final String OKAPI_URL_KEY = "OKAPI_URL";

  public static EmbeddedKafkaCluster kafkaCluster;
  private static MockServer mockServer;
  public static Vertx vertx;
  private static boolean initialised;

  @BeforeClass
  public static void before(TestContext context) throws Exception {
    if (vertx == null) {
      vertx = Vertx.vertx();
    }

    mockServer = new MockServer(mockPort);
    mockServer.start();

    RestAssured.baseURI = "http://localhost:" + mockPort;
    RestAssured.port = mockPort;
    RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();

    kafkaCluster = EmbeddedKafkaCluster.provisionWith(defaultClusterConfig());
    kafkaCluster.start();
    String[] hostAndPort = kafkaCluster.getBrokerList().split(":");
    System.setProperty(KAFKA_HOST, hostAndPort[0]);
    System.setProperty(KAFKA_PORT, hostAndPort[1]);
    System.setProperty(KAFKA_ENV, KAFKA_ENV_VALUE);
    System.setProperty(OKAPI_URL_KEY, OKAPI_URL);
    runDatabase();
    deployVerticle(context);
  }

  @Before
  public void setUp(TestContext context) throws IOException {
    clearTable(context);
    spec = new RequestSpecBuilder()
      .setContentType(ContentType.JSON)
      .addHeader(OKAPI_TENANT_HEADER, TENANT_ID)
      .addHeader(RestVerticle.OKAPI_USERID_HEADER, okapiUserIdHeader)
      .addHeader("Accept", "text/plain, application/json")
      .setBaseUri(OKAPI_URL)
      .build();
  }

  private void clearTable(TestContext context) {
    Async async = context.async();
    PostgresClient pgClient = PostgresClient.getInstance(vertx, TENANT_ID);
    pgClient.delete(INVOICES_TABLE, new Criterion(), event1 -> {
      if (event1.failed()) {
        context.fail(event1.cause());
      }
      async.complete();
    });
  }

  @AfterClass
  public static void after(TestContext context) {
    Async async = context.async();
    vertx.close(context.asyncAssertSuccess(res -> {
      if (useExternalDatabase.equals("embedded")) {
        PostgresClient.stopPostgresTester();
      }
      kafkaCluster.stop();
      mockServer.close();
      initialised = false;
      async.complete();
    }));
  }

  private static void runDatabase() throws Exception {
    PostgresClient.stopPostgresTester();
    PostgresClient.closeAllClients();
    useExternalDatabase = System.getProperty(
      "org.folio.invoice.test.database",
      "embedded");

    switch (useExternalDatabase) {
      case "environment":
        System.out.println("Using environment settings");
        break;
      case "external":
        String postgresConfigPath = System.getProperty(
          "org.folio.invoice.test.config",
          "/postgres-conf-local.json");
        PostgresClient.setConfigFilePath(postgresConfigPath);
        break;
      case "embedded":
        postgresSQLContainer = new PostgreSQLContainer<>(POSTGRES_IMAGE);
        postgresSQLContainer.start();

        Envs.setEnv(
          postgresSQLContainer.getHost(),
          postgresSQLContainer.getFirstMappedPort(),
          postgresSQLContainer.getUsername(),
          postgresSQLContainer.getPassword(),
          postgresSQLContainer.getDatabaseName()
        );
        break;
      default:
        String message = "No understood database choice made." +
          "Please set org.folio.source.record.manager.test.database" +
          "to 'external', 'environment' or 'embedded'";
        throw new Exception(message);
    }
  }

  public static String constructModuleName() {
    return ModuleName.getModuleName().replace("_", "-") + "-" + ModuleName.getModuleVersion();
  }

  private static void deployVerticle(final TestContext context) {
    Async async = context.async();
    final DeploymentOptions options = new DeploymentOptions()
      .setConfig(new JsonObject()
        .put(HTTP_PORT, okapiPort));
    vertx.deployVerticle(RestVerticle.class.getName(), options, deployVerticleAr -> {
      try {
        TenantAPI tenantAPI = new TenantAPI();
        TenantAttributes tenantAttributes = new TenantAttributes();
        tenantAttributes.setModuleTo(constructModuleName());

        Map<String, String> okapiHeaders = new HashMap<>();
        okapiHeaders.put(OKAPI_HEADER_TOKEN, TOKEN);
        okapiHeaders.put(OKAPI_URL_ENV, OKAPI_URL);
        okapiHeaders.put(OKAPI_HEADER_TENANT, TENANT_ID);
        tenantAPI.postTenant(tenantAttributes, okapiHeaders, context.asyncAssertSuccess(result -> {
          if (result.getStatus() == 204) {
            return;
          }
          if (result.getStatus() == 201) {
            tenantAPI.getTenantByOperationId(((TenantJob)result.getEntity()).getId(), 60000, okapiHeaders,
              context.asyncAssertSuccess(res3 -> {
                context.assertTrue(((TenantJob) res3.getEntity()).getComplete());
                String error = ((TenantJob) res3.getEntity()).getError();
                if (error != null) {
                  context.assertTrue(error.contains("EventDescriptor was not registered for eventType"));
                }
                initialised = true;
              }), vertx.getOrCreateContext());
          } else {
            context.assertEquals("Failed to make post tenant. Received status code 400", result.getStatus());
          }
          async.complete();
        }), vertx.getOrCreateContext());
      } catch (Exception e) {
        e.printStackTrace();
      }
    });
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
  class VoucherLinesRetrieveServiceTestNested extends VoucherLineServiceTest {
  }

  @Nested
  class BatchVoucherGenerateServiceTestNested extends BatchVoucherGenerateServiceTest {
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
  class ProtectedFieldsValidatorTestNested extends ProtectedFieldsValidatorTest {
  }

  @Nested
  class BaseTransactionServiceTestNested extends BaseTransactionServiceTest {
  }

  @Nested
  class EncumbranceServiceTestNested extends EncumbranceServiceTest {
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
  class InvoiceWorkFlowDataHolderBuilderTestNested extends InvoiceWorkFlowDataHolderBuilderTest {
  }

  @Nested
  class BudgetExpenseClassTestNested extends BudgetExpenseClassTest {
  }

  @Nested
  class BudgetServiceTestNested extends BudgetServiceTest {
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

  @Nested
  class InvoiceFiscalYearsServiceTestNested extends InvoiceFiscalYearsServiceTest {
  }

  @Nested
  class SftpUploadServiceTestNested extends SftpUploadServiceTest {
  }

}
