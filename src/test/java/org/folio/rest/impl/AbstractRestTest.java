package org.folio.rest.impl;

import static net.mguenther.kafka.junit.EmbeddedKafkaCluster.provisionWith;
import static net.mguenther.kafka.junit.EmbeddedKafkaClusterConfig.defaultClusterConfig;
import static org.folio.dataimport.util.RestUtil.OKAPI_TENANT_HEADER;
import static org.folio.kafka.KafkaTopicNameHelper.getDefaultNameSpace;
import static org.folio.rest.util.OkapiConnectionParams.OKAPI_TOKEN_HEADER;
import static org.folio.rest.util.OkapiConnectionParams.OKAPI_URL_HEADER;
import static org.testcontainers.shaded.org.awaitility.Awaitility.await;

import com.github.tomakehurst.wiremock.admin.NotFoundException;
import com.github.tomakehurst.wiremock.common.ConsoleNotifier;
import com.github.tomakehurst.wiremock.common.FileSource;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.extension.Parameters;
import com.github.tomakehurst.wiremock.extension.ResponseTransformer;
import com.github.tomakehurst.wiremock.http.Request;
import com.github.tomakehurst.wiremock.http.Response;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.impl.VertxImpl;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.kafka.admin.KafkaAdminClient;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import net.mguenther.kafka.junit.EmbeddedKafkaCluster;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.folio.kafka.KafkaTopicNameHelper;
import org.folio.rest.RestVerticle;
import org.folio.rest.client.TenantClient;
import org.folio.rest.jaxrs.model.Event;
import org.folio.rest.jaxrs.model.TenantAttributes;
import org.folio.rest.jaxrs.model.TenantJob;
import org.folio.rest.persist.Criteria.Criterion;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.tools.utils.Envs;
import org.folio.rest.tools.utils.ModuleName;
import org.folio.rest.tools.utils.NetworkUtils;
import org.folio.rest.util.OkapiConnectionParams;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Abstract test for the REST API testing needs.
 */
public abstract class AbstractRestTest {

  public static final String POSTGRES_IMAGE = "postgres:12-alpine";
  private static PostgreSQLContainer<?> postgresSQLContainer;

  private static final String INVOICES_TABLE = "records_invoices";
  protected static final String TOKEN = "token";
  private static final String HTTP_PORT = "http.port";
  private static int port;
  private static String useExternalDatabase;
  private static final String postedSnapshotResponseBody = UUID.randomUUID().toString();
  protected static Vertx vertx;
  protected static final String TENANT_ID = "diku";
  protected static RequestSpecification spec;

  protected static final String okapiUserIdHeader = UUID.randomUUID().toString();
  private static final String KAFKA_HOST = "KAFKA_HOST";
  private static final String KAFKA_PORT = "KAFKA_PORT";
  private static final String KAFKA_ENV = "ENV";
  private static final String KAFKA_ENV_VALUE = "test-env";
  public static final String OKAPI_URL_ENV = "OKAPI_URL";
  private static final int PORT = NetworkUtils.nextFreePort();
  protected static final String OKAPI_URL = "http://localhost:" + PORT;

  @Rule
  public WireMockRule snapshotMockServer = new WireMockRule(
    WireMockConfiguration.wireMockConfig()
      .dynamicPort()
      .notifier(new ConsoleNotifier(true))
      .extensions(new RequestToResponseTransformer())
  );

  public static EmbeddedKafkaCluster kafkaCluster;

  @BeforeClass
  public static void setUpClass(final TestContext context) throws Exception {
    vertx = Vertx.vertx();
    kafkaCluster = provisionWith(defaultClusterConfig());
    kafkaCluster.start();
    String[] hostAndPort = kafkaCluster.getBrokerList().split(":");

    System.setProperty(KAFKA_HOST, hostAndPort[0]);
    System.setProperty(KAFKA_PORT, hostAndPort[1]);
    System.setProperty(KAFKA_ENV, KAFKA_ENV_VALUE);
    System.setProperty(OKAPI_URL_ENV, OKAPI_URL);
    runDatabase();
    deployVerticle(context);
  }

  @AfterClass
  public static void tearDownClass(final TestContext context) {
    Async async = context.async();
    vertx.close(context.asyncAssertSuccess(res -> {
      if (useExternalDatabase.equals("embedded")) {
        PostgresClient.stopPostgresTester();
      }
      kafkaCluster.stop();
      async.complete();
    }));
  }

  private static void runDatabase() throws Exception {
    PostgresClient.stopPostgresTester();
    PostgresClient.closeAllClients();
    useExternalDatabase = System.getProperty(
      "org.folio.source.record.manager.test.database",
      "embedded");

    switch (useExternalDatabase) {
      case "environment":
        System.out.println("Using environment settings");
        break;
      case "external":
        String postgresConfigPath = System.getProperty(
          "org.folio.source.record.manager.test.config",
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
    port = NetworkUtils.nextFreePort();
    String okapiUrl = "http://localhost:" + port;
    TenantClient tenantClient = new TenantClient(okapiUrl, TENANT_ID, TOKEN);
    final DeploymentOptions options = new DeploymentOptions()
      .setConfig(new JsonObject()
        .put(HTTP_PORT, port));
    vertx.deployVerticle(RestVerticle.class.getName(), options, deployVerticleAr -> {
      try {
        TenantAttributes tenantAttributes = new TenantAttributes();
        tenantAttributes.setModuleTo(constructModuleName());
        tenantClient.postTenant(tenantAttributes, res2 -> {
          if (res2.result().statusCode() == 204) {
            return;
          }
          if (res2.result().statusCode() == 201) {
            tenantClient.getTenantByOperationId(res2.result().bodyAsJson(TenantJob.class).getId(), 60000, context.asyncAssertSuccess(res3 -> {
              context.assertTrue(res3.bodyAsJson(TenantJob.class).getComplete());
              String error = res3.bodyAsJson(TenantJob.class).getError();
              if (error != null) {
                context.assertTrue(error.contains("EventDescriptor was not registered for eventType"));
              }
            }));
          } else {
            context.assertEquals("Failed to make post tenant. Received status code 400", res2.result().bodyAsString());
          }
          async.complete();
        });
      } catch (Exception e) {
        e.printStackTrace();
      }
    });
  }

  @Before
  public void setUp(TestContext context) throws IOException {
    clearTable(context);
    spec = new RequestSpecBuilder()
      .setContentType(ContentType.JSON)
      .addHeader(OKAPI_URL_HEADER, "http://localhost:" + snapshotMockServer.port())
      .addHeader(OKAPI_TENANT_HEADER, TENANT_ID)
      .addHeader(RestVerticle.OKAPI_USERID_HEADER, okapiUserIdHeader)
      .addHeader("Accept", "text/plain, application/json")
      .setBaseUri("http://localhost:" + port)
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

  /**
   * Maps a request body to a response body.
   */
  public static class RequestToResponseTransformer extends ResponseTransformer {

    public static final String NAME = "request-to-response-transformer";

    @Override
    public Response transform(Request request, Response response, FileSource files, Parameters parameters) {
      return Response.Builder.like(response).but().body(request.getBody()).build();
    }

    @Override
    public String getName() {
      return NAME;
    }

    @Override
    public boolean applyGlobally() {
      return false;
    }
  }

  protected String formatToKafkaTopicName(String eventType) {
    return KafkaTopicNameHelper.formatTopicName(KAFKA_ENV_VALUE, getDefaultNameSpace(), TENANT_ID, eventType);
  }

  protected  <T> T getBeanFromSpringContext(Vertx vtx, Class<T> clazz) {

    String parentVerticleUUID = vertx.deploymentIDs().stream()
      .filter(v -> !((VertxImpl) vertx).getDeployment(v).isChild())
      .findFirst()
      .orElseThrow(() -> new NotFoundException("Couldn't find the parent verticle."));

    Optional<Object> context = Optional.of(((VertxImpl) vtx).getDeployment(parentVerticleUUID).getContexts().stream()
      .findFirst().map(v -> v.get("springContext")))
      .orElseThrow(() -> new NotFoundException("Couldn't find the spring context."));

    if (context.isPresent()) {
      return ((AnnotationConfigApplicationContext) context.get()).getBean(clazz);
    }
    throw new NotFoundException(String.format("Couldn't find bean %s", clazz.getName()));
  }

  protected ConsumerRecord<String, String> buildConsumerRecord(String topic, Event event) {
    ConsumerRecord<String, String> consumerRecord = new ConsumerRecord("folio", 0, 0, topic, Json.encode(event));
    consumerRecord.headers().add(new RecordHeader(OkapiConnectionParams.OKAPI_TENANT_HEADER, TENANT_ID.getBytes(StandardCharsets.UTF_8)));
    consumerRecord.headers().add(new RecordHeader(OKAPI_URL_HEADER, ("http://localhost:" + snapshotMockServer.port()).getBytes(StandardCharsets.UTF_8)));
    consumerRecord.headers().add(new RecordHeader(OKAPI_TOKEN_HEADER, (TOKEN).getBytes(StandardCharsets.UTF_8)));
    return consumerRecord;
  }

  private static void waitForPostgres() {
    PostgresClient pgClient = PostgresClient.getInstance(vertx);
    String query = "Select 1";
    AtomicBoolean isReady = new AtomicBoolean();
    await()
      .atMost(15, TimeUnit.SECONDS)
      .pollInterval(3, TimeUnit.SECONDS)
      .alias("Is Postgres Up?")
      .until(() -> {
        System.out.println("checking to see if postgres is up");

        vertx.runOnContext((at) -> pgClient.select(query)
          .onSuccess(ar -> isReady.set(true)));

        return isReady.get();
      });

    if (!isReady.get()) throw new RuntimeException("Could not connect to postgres");
  }

  private static void waitForKafka() {
    Supplier<KafkaAdminClient> buildAdminClient = () -> {
      Map<String, String> configs = new HashMap<>();
      configs.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaCluster.getBrokerList());
      return KafkaAdminClient.create(vertx, configs);
    };
    AtomicBoolean isReady = new AtomicBoolean();

    await()
      .atMost(15, TimeUnit.SECONDS)
      .pollDelay(3, TimeUnit.SECONDS)
      .pollInterval(3, TimeUnit.SECONDS)
      .alias("Is Kafka Up?")
      .until(() -> {
        System.out.println("listing topics to see if kafka is up");
        KafkaAdminClient adminClient = buildAdminClient.get();
        adminClient.listTopics()
          .onComplete(ar -> {
            if (ar.succeeded()) {
              System.out.println("Kafka is up");
              isReady.set(true);
            }
            adminClient.close(1000);
          });
        return isReady.get();
      });
  }
}
