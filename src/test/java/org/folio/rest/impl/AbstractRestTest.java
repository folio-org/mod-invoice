package org.folio.rest.impl;

import static net.mguenther.kafka.junit.EmbeddedKafkaCluster.provisionWith;
import static net.mguenther.kafka.junit.EmbeddedKafkaClusterConfig.defaultClusterConfig;
import static org.folio.ApiTestSuite.mockPort;
import static org.folio.dataimport.util.RestUtil.OKAPI_TENANT_HEADER;
import static org.folio.rest.RestVerticle.OKAPI_HEADER_TENANT;
import static org.folio.rest.RestVerticle.OKAPI_HEADER_TOKEN;
import static org.folio.rest.util.OkapiConnectionParams.OKAPI_URL_HEADER;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import io.restassured.builder.RequestSpecBuilder;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxTestContext;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import javax.ws.rs.core.Response;
import net.mguenther.kafka.junit.EmbeddedKafkaCluster;
import org.folio.rest.RestVerticle;
import org.folio.rest.jaxrs.model.TenantAttributes;
import org.folio.rest.jaxrs.model.TenantJob;
import org.folio.rest.persist.Criteria.Criterion;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.tools.utils.Envs;
import org.folio.rest.tools.utils.ModuleName;
import org.folio.rest.tools.utils.NetworkUtils;
import org.junit.Before;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
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

  public static EmbeddedKafkaCluster kafkaCluster;

  @BeforeAll
  public static void setUpClass(final VertxTestContext context) throws Exception {
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

  @AfterAll
  public static void tearDownClass(final VertxTestContext context) {
    vertx.close(ar -> {
      if (ar.succeeded()) {
        if ("embedded".equals(useExternalDatabase)) {
          PostgresClient.stopPostgresTester();
        }
        if (kafkaCluster != null) {
          kafkaCluster.stop();
        }
        context.completeNow();
      } else {
        context.failNow(ar.cause());
      }
    });
  }

  private static void runDatabase() throws Exception {
    PostgresClient.stopPostgresTester();
    PostgresClient.closeAllClients();
    useExternalDatabase = System.getProperty(
      "org.folio.invoice.test.database",
      "embedded");

    switch (useExternalDatabase) {
      case "environment" -> System.out.println("Using environment settings");
      case "external" -> {
        String postgresConfigPath = System.getProperty(
          "org.folio.invoice.test.config",
          "/postgres-conf-local.json");
        PostgresClient.setConfigFilePath(postgresConfigPath);
      }
      case "embedded" -> {
        postgresSQLContainer = new PostgreSQLContainer<>(POSTGRES_IMAGE);
        postgresSQLContainer.start();
        Envs.setEnv(
          postgresSQLContainer.getHost(),
          postgresSQLContainer.getFirstMappedPort(),
          postgresSQLContainer.getUsername(),
          postgresSQLContainer.getPassword(),
          postgresSQLContainer.getDatabaseName()
        );
      }
      default -> {
        String message = "No understood database choice made." +
          "Please set org.folio.source.record.manager.test.database" +
          "to 'external', 'environment' or 'embedded'";
        throw new Exception(message);
      }
    }
  }

  public static String constructModuleName() {
    return ModuleName.getModuleName().replace("_", "-") + "-" + ModuleName.getModuleVersion();
  }

  private static void deployVerticle(final VertxTestContext testContext) {
    port = NetworkUtils.nextFreePort();
    String okapiUrl = "http://localhost:" + port;
    final DeploymentOptions options = new DeploymentOptions()
      .setConfig(new JsonObject()
        .put(HTTP_PORT, port));
    vertx.deployVerticle(RestVerticle.class.getName(), options).onComplete(deployVerticleAr -> {
      if (deployVerticleAr.failed()) {
        testContext.failNow(deployVerticleAr.cause());
        return;
      }

      TenantAPI tenantAPI = new TenantAPI();
      TenantAttributes tenantAttributes = new TenantAttributes();
      tenantAttributes.setModuleTo(constructModuleName());
      Map<String, String> okapiHeaders = new HashMap<>();
      okapiHeaders.put(OKAPI_HEADER_TOKEN, TOKEN);
      okapiHeaders.put(OKAPI_URL_ENV, okapiUrl);
      okapiHeaders.put(OKAPI_HEADER_TENANT, TENANT_ID);

      Future<Response> future = Future.future(promise ->
        tenantAPI.postTenant(tenantAttributes, okapiHeaders, promise, vertx.getOrCreateContext()));

        future.onComplete(postResult -> {
          if (postResult.failed()) {
            testContext.failNow(postResult.cause());
            return;
          }
          testContext.verify(() -> {
            Response result = postResult.result();
            if (result.getStatus() == 204) {
              testContext.completeNow();
            } else if (result.getStatus() == 201) {
              Future<Response> future2 =
                Future.future(promise -> tenantAPI.getTenantByOperationId(((TenantJob)result.getEntity()).getId(), 60000,
                okapiHeaders, promise, vertx.getOrCreateContext()));

              future2.onComplete(getResult -> testContext.verify(() -> {
                if (getResult.failed()) {
                  testContext.failNow(getResult.cause());
                  return;
                }
                TenantJob tenantJob = (TenantJob) getResult.result().getEntity();
                assertTrue(tenantJob.getComplete(), "Tenant job should be complete.");

                String error = tenantJob.getError();
                if (error != null) {
                  assertTrue(error.contains("EventDescriptor was not registered for eventType"), "Error message should contain expected text.");
                }
                testContext.completeNow();
              }));
            } else {
              fail("Failed to make post tenant. Received status code " + result.getStatus());
            }
          });
        });
    });
  }

  @Before
  public void setUp(VertxTestContext context) throws IOException {
    clearTable(context);
    spec = new RequestSpecBuilder()
      .setContentType(ContentType.JSON)
      .addHeader(OKAPI_URL_HEADER, "http://localhost:" + mockPort)
      .addHeader(OKAPI_TENANT_HEADER, TENANT_ID)
      .addHeader(RestVerticle.OKAPI_USERID_HEADER, okapiUserIdHeader)
      .addHeader("Accept", "text/plain, application/json")
      .setBaseUri("http://localhost:" + port)
      .build();
  }

  private void clearTable(VertxTestContext context) {
    PostgresClient pgClient = PostgresClient.getInstance(vertx, TENANT_ID);
    pgClient.delete(INVOICES_TABLE, new Criterion(), event1 -> {
      if (event1.failed()) {
        context.failNow(event1.cause());
      }
      context.completeNow();
    });
  }
}
