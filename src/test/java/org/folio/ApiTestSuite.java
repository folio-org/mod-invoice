package org.folio;

import io.restassured.RestAssured;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import lombok.SneakyThrows;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.folio.builders.InvoiceWorkFlowDataHolderBuilderTest;
import org.folio.converters.BatchVoucherModelConverterTest;
import org.folio.converters.BatchedVoucherLinesModelConverterTest;
import org.folio.converters.BatchedVoucherModelConverterTest;
import org.folio.dao.EntityIdStorageDaoImplTest;
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
import org.folio.rest.impl.VoucherLinesApiTest;
import org.folio.rest.impl.VouchersApiTest;
import org.folio.rest.impl.protection.InvoicesProtectionTest;
import org.folio.rest.impl.protection.LinesProtectionTest;
import org.folio.rest.tools.utils.NetworkUtils;
import org.folio.schemas.xsd.BatchVoucherSchemaXSDTest;
import org.folio.services.InvoiceLinesRetrieveServiceTest;
import org.folio.services.InvoiceRetrieveServiceTest;
import org.folio.services.VoucherLineServiceTest;
import org.folio.services.exchange.CacheableExchangeRateServiceTest;
import org.folio.services.finance.CurrentFiscalYearServiceTest;
import org.folio.services.exchange.CustomExchangeRateProviderTest;
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
import org.folio.services.invoice.InvoiceIdStorageServiceTest;
import org.folio.services.invoice.PoLinePaymentStatusUpdateServiceTest;
import org.folio.services.order.OrderLineServiceTest;
import org.folio.services.order.OrderServiceTest;
import org.folio.services.validator.FundAvailabilityHolderValidatorTest;
import org.folio.services.validator.InvoiceLineHolderValidatorTest;
import org.folio.services.validator.InvoiceValidatorServiceTest;
import org.folio.services.validator.ProtectedFieldsValidatorTest;
import org.folio.services.voucher.BatchVoucherGenerateServiceTest;
import org.folio.services.voucher.UploadBatchVoucherExportServiceTest;
import org.folio.verticles.DataImportConsumerVerticleTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

@ExtendWith(VertxExtension.class)
public class ApiTestSuite {

  private static final int okapiPort = NetworkUtils.nextFreePort();
  public static final int mockPort = NetworkUtils.nextFreePort();
  public static final String KAFKA_ENV_VALUE = "test-env";
  private static final String KAFKA_HOST = "KAFKA_HOST";
  private static final String KAFKA_PORT = "KAFKA_PORT";
  private static final String KAFKA_ENV = "ENV";
  private static final String OKAPI_URL_KEY = "OKAPI_URL";

  private static final DockerImageName KAFKA_IMAGE_NAME = DockerImageName.parse("apache/kafka-native:3.8.0");
  private static final KafkaContainer kafkaContainer = new KafkaContainer(KAFKA_IMAGE_NAME).withStartupAttempts(3);
  private static MockServer mockServer;
  public static Vertx vertx;
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

    kafkaContainer.start();
    System.setProperty(KAFKA_HOST, kafkaContainer.getHost());
    System.setProperty(KAFKA_PORT, kafkaContainer.getFirstMappedPort().toString());
    System.setProperty(KAFKA_ENV, KAFKA_ENV_VALUE);
    System.setProperty(OKAPI_URL_KEY, "http://localhost:" + mockPort);

    final JsonObject conf = new JsonObject();
    conf.put("http.port", okapiPort);

    final DeploymentOptions opt = new DeploymentOptions().setConfig(conf);
    Promise<String> deploymentComplete = Promise.promise();
    vertx.deployVerticle(RestVerticle.class.getName(), opt, res -> {
      if (res.succeeded()) {
        deploymentComplete.complete(res.result());
      } else {
        deploymentComplete.fail(res.cause());
      }
    });
    deploymentComplete.future().toCompletionStage().toCompletableFuture().get(60, TimeUnit.SECONDS);
    initialised = true;
  }

  @AfterAll
  public static void after(VertxTestContext testContext) {
    vertx.close(ar -> {
      if (ar.succeeded()) {
        kafkaContainer.stop();
        mockServer.close();
        testContext.completeNow();
      } else {
        testContext.failNow(ar.cause());
      }
    });
    initialised = false;
  }

  public static KafkaProducer<String, String> createKafkaProducer() {
    Properties producerProperties = new Properties();
    producerProperties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaContainer.getBootstrapServers());
    producerProperties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
    producerProperties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
    return new KafkaProducer<>(producerProperties);
  }

  public static KafkaConsumer<String, String> createKafkaConsumer() {
    Properties consumerProperties = new Properties();
    consumerProperties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaContainer.getBootstrapServers());
    consumerProperties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
    consumerProperties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
    consumerProperties.put(ConsumerConfig.GROUP_ID_CONFIG, "test-group");
    consumerProperties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
    return new KafkaConsumer<>(consumerProperties);
  }

  @SneakyThrows
  public static void sendToTopic(ProducerRecord<String, String> producerRecord) {
    try (KafkaProducer<String, String> producer = createKafkaProducer()) {
      producer.send(producerRecord).get(10, TimeUnit.SECONDS);
    }
  }

  public static List<String> observeTopic(String topic, Duration duration) {
    List<String> result = new ArrayList<>();
    ConsumerRecords<String, String> records;
    try (var kafkaConsumer = createKafkaConsumer()) {
      kafkaConsumer.subscribe(List.of(topic));
      records = kafkaConsumer.poll(duration);
    }
    records.forEach(rec -> result.add(rec.value()));
    return result;
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
  class InvoiceValidatorServiceTestNested extends InvoiceValidatorServiceTest {
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
  class CustomExchangeRateProviderNested extends CustomExchangeRateProviderTest {
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
  class OrderLineServiceTestNested extends OrderLineServiceTest {
  }

  @Nested
  class InvoiceCancelServiceTestNested extends InvoiceCancelServiceTest {
  }

  @Nested
  class PoLinePaymentStatusUpdateServiceTestNested extends PoLinePaymentStatusUpdateServiceTest {
  }

  @Nested
  class InvoiceFiscalYearsServiceTestNested extends InvoiceFiscalYearsServiceTest {
  }

  @Nested
  class SftpUploadServiceTestNested extends SftpUploadServiceTest {
  }

  @Nested
  class InvoiceIdStorageServiceTestNested extends InvoiceIdStorageServiceTest {
  }

  @Nested
  class EntityIdStorageDaoImplTestNested extends EntityIdStorageDaoImplTest{
  }

  @Nested
  class CacheableExchangeRateServiceTestNested extends CacheableExchangeRateServiceTest {
  }
}
