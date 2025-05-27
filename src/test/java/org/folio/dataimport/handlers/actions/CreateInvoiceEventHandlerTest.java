package org.folio.dataimport.handlers.actions;

import static io.vertx.core.Future.succeededFuture;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.folio.ActionProfile.Action.CREATE;
import static org.folio.ApiTestSuite.KAFKA_ENV_VALUE;
import static org.folio.ApiTestSuite.observeTopic;
import static org.folio.ApiTestSuite.createKafkaProducer;
import static org.folio.DataImportEventTypes.DI_COMPLETED;
import static org.folio.DataImportEventTypes.DI_ERROR;
import static org.folio.DataImportEventTypes.DI_INCOMING_EDIFACT_RECORD_PARSED;
import static org.folio.DataImportEventTypes.DI_INVOICE_CREATED;
import static org.folio.dataimport.handlers.actions.CreateInvoiceEventHandler.INVOICE_LINES_ERRORS_KEY;
import static org.folio.dataimport.handlers.actions.CreateInvoiceEventHandler.INVOICE_LINES_KEY;
import static org.folio.dataimport.utils.DataImportUtils.DATA_IMPORT_PAYLOAD_OKAPI_PERMISSIONS;
import static org.folio.dataimport.utils.DataImportUtils.DATA_IMPORT_PAYLOAD_OKAPI_USER_ID;
import static org.folio.kafka.KafkaTopicNameHelper.getDefaultNameSpace;
import static org.folio.rest.impl.MockServer.DI_POST_INVOICE_LINES_SUCCESS_TENANT;
import static org.folio.rest.impl.MockServer.DUPLICATE_ERROR_TENANT;
import static org.folio.rest.impl.MockServer.EDIFACTS_MOCK_DATA_PATH;
import static org.folio.rest.impl.MockServer.ERROR_TENANT;
import static org.folio.rest.impl.MockServer.MOCK_DATA_PATH_PATTERN;
import static org.folio.rest.impl.MockServer.PO_LINES_MOCK_DATA_PATH;
import static org.folio.rest.impl.MockServer.addMockEntry;
import static org.folio.rest.jaxrs.model.EntityType.EDIFACT_INVOICE;
import static org.folio.rest.jaxrs.model.EntityType.INVOICE;
import static org.folio.rest.jaxrs.model.FundDistribution.DistributionType.PERCENTAGE;
import static org.folio.rest.jaxrs.model.ProfileType.ACTION_PROFILE;
import static org.folio.rest.jaxrs.model.ProfileType.JOB_PROFILE;
import static org.folio.rest.jaxrs.model.ProfileType.MAPPING_PROFILE;
import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.type.TypeReference;
import io.vertx.core.Future;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.json.jackson.DatabindCodec;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.SneakyThrows;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.folio.ActionProfile;
import org.folio.ApiTestSuite;
import org.folio.DataImportEventPayload;
import org.folio.JobProfile;
import org.folio.MappingProfile;
import org.folio.ParsedRecord;
import org.folio.Record;
import org.folio.domain.relationship.EntityTable;
import org.folio.domain.relationship.RecordToEntity;
import org.folio.invoices.utils.AcqDesiredPermissions;
import org.folio.kafka.KafkaTopicNameHelper;
import org.folio.processing.events.EventManager;
import org.folio.processing.events.services.handler.EventHandler;
import org.folio.rest.acq.model.orders.PoLine;
import org.folio.rest.acq.model.orders.PoLineCollection;
import org.folio.rest.core.RestClient;
import org.folio.rest.core.models.RequestContext;
import org.folio.rest.core.models.RequestEntry;
import org.folio.rest.impl.ApiTestBase;
import org.folio.rest.jaxrs.model.Event;
import org.folio.rest.jaxrs.model.Invoice;
import org.folio.rest.jaxrs.model.InvoiceLine.InvoiceLineStatus;
import org.folio.rest.jaxrs.model.InvoiceLineCollection;
import org.folio.rest.jaxrs.model.MappingDetail;
import org.folio.rest.jaxrs.model.MappingRule;
import org.folio.rest.jaxrs.model.ProfileSnapshotWrapper;
import org.folio.rest.jaxrs.model.RepeatableSubfieldMapping;
import org.folio.services.invoice.InvoiceIdStorageService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;

@ExtendWith(VertxExtension.class)
public class CreateInvoiceEventHandlerTest extends ApiTestBase {

  private static String edifactParsedContent;
  private static  String parsedContentInvoiceLine3HasNoSubTotal;
  private static final String OKAPI_URL = "http://localhost:" + ApiTestSuite.mockPort;
  private static final String TENANT_ID = "diku";
  private static final String TOKEN = "test-token";
  private static final String PO_LINE_ID_1 = "0000edd1-b463-41ba-bf64-1b1d9f9d0001";
  private static final String PO_LINE_ID_3 = "0000edd1-b463-41ba-bf64-1b1d9f9d0003";
  private static final String JOB_PROFILE_SNAPSHOTS_MOCK = "jobProfileSnapshots";
  private static final String JOB_PROFILE_SNAPSHOT_ID_KEY = "JOB_PROFILE_SNAPSHOT_ID";
  private static final String ERROR_MSG_KEY = "ERROR";
  private static final String RECORD_ID_HEADER = "recordId";
  private static final String USER_ID = "userId";
  private static final String INVOICE_ID = "iiiiiiii-2222-4031-a031-70b1c1b2fc5d";
  private static final String RECORD_ID =  "rrrrrrrr-0000-1111-2222-333333333333";
  private static final String FIVE_INVOICE_LINES_FILE = "5-invoice-lines";

  private JobProfile jobProfile = new JobProfile()
    .withId(UUID.randomUUID().toString())
    .withName("Create invoice")
    .withDataType(JobProfile.DataType.EDIFACT);

  private ActionProfile actionProfile = new ActionProfile()
    .withId(UUID.randomUUID().toString())
    .withAction(CREATE)
    .withFolioRecord(ActionProfile.FolioRecord.INVOICE);

  private MappingProfile mappingProfile = new MappingProfile()
    .withId(UUID.randomUUID().toString())
    .withIncomingRecordType(EDIFACT_INVOICE)
    .withExistingRecordType(INVOICE)
    .withMappingDetails(new MappingDetail()
      .withMappingFields(List.of(
        new MappingRule().withPath("invoice.vendorInvoiceNo").withValue("BGM+380+[1]").withEnabled("true"),
        new MappingRule().withPath("invoice.currency").withValue("CUX+2[2]").withEnabled("true"),
        new MappingRule().withPath("invoice.status").withValue("\"Open\"").withEnabled("true"),
        new MappingRule().withPath("invoice.invoiceLines[]").withEnabled("true").withName("invoiceLines")
          .withRepeatableFieldAction(MappingRule.RepeatableFieldAction.EXTEND_EXISTING)
          .withSubfields(List.of(new RepeatableSubfieldMapping()
            .withOrder(0)
            .withPath("invoice.invoiceLines[]")
            .withFields(List.of(
              new MappingRule().withPath("invoice.invoiceLines[].subTotal")
                .withValue("MOA+203[2]"),
              new MappingRule().withPath("invoice.invoiceLines[].quantity")
                .withValue("QTY+47[2]")
            )))))));

  private MappingProfile mappingProfileWithPoLineSyntax = new MappingProfile()
    .withId(UUID.randomUUID().toString())
    .withIncomingRecordType(EDIFACT_INVOICE)
    .withExistingRecordType(INVOICE)
    .withMappingDetails(new MappingDetail()
      .withMappingFields(List.of(
        new MappingRule().withPath("invoice.vendorInvoiceNo").withValue("BGM+380+[1]").withEnabled("true"),
        new MappingRule().withPath("invoice.currency").withValue("CUX+2[2]").withEnabled("true"),
        new MappingRule().withPath("invoice.status").withValue("\"Open\"").withEnabled("true"),
        new MappingRule().withPath("invoice.invoiceLines[]").withEnabled("true").withName("invoiceLines")
          .withRepeatableFieldAction(MappingRule.RepeatableFieldAction.EXTEND_EXISTING)
          .withSubfields(List.of(new RepeatableSubfieldMapping()
            .withOrder(0)
            .withPath("invoice.invoiceLines[]")
            .withFields(List.of(
              new MappingRule().withPath("invoice.invoiceLines[].poLineId")
                .withName("poLineId")
                .withValue("RFF+LI[2]; else {POL_NUMBER}"),
              new MappingRule().withPath("invoice.invoiceLines[].referenceNumbers[]")
                .withRepeatableFieldAction(MappingRule.RepeatableFieldAction.EXTEND_EXISTING)
                .withName("referenceNumbers")
                .withSubfields(List.of(new RepeatableSubfieldMapping()
                  .withOrder(0)
                  .withPath("invoice.invoiceLines[].referenceNumbers[]")
                  .withFields(List.of(
                    new MappingRule().withPath("invoice.invoiceLines[].referenceNumbers[].refNumber")
                      .withName("refNumber")
                      .withValue("RFF+SNA[2]"),
                    new MappingRule().withPath("invoice.invoiceLines[].referenceNumbers[].refNumberType")
                      .withValue("\"Vendor order reference number\"")
                  )))),
              new MappingRule().withPath("invoice.invoiceLines[].description")
                .withValue("{POL_title}; else IMD+L+050+[4]"),
              new MappingRule().withPath("invoice.invoiceLines[].subTotal")
                .withValue("MOA+203[2]"))))))));

  private MappingProfile mappingProfileWithPoLineFundDistribution = new MappingProfile()
    .withId(UUID.randomUUID().toString())
    .withIncomingRecordType(EDIFACT_INVOICE)
    .withExistingRecordType(INVOICE)
    .withMappingDetails(new MappingDetail()
      .withMappingFields(List.of(
        new MappingRule().withPath("invoice.vendorInvoiceNo").withValue("BGM+380+[1]").withEnabled("true"),
        new MappingRule().withPath("invoice.currency").withValue("CUX+2[2]").withEnabled("true"),
        new MappingRule().withPath("invoice.status").withValue("\"Open\"").withEnabled("true"),
        new MappingRule().withPath("invoice.invoiceLines[]").withEnabled("true").withName("invoiceLines")
          .withRepeatableFieldAction(MappingRule.RepeatableFieldAction.EXTEND_EXISTING)
          .withSubfields(List.of(new RepeatableSubfieldMapping()
            .withOrder(0)
            .withPath("invoice.invoiceLines[]")
            .withFields(List.of(
              new MappingRule().withPath("invoice.invoiceLines[].poLineId")
                .withName("poLineId")
                .withValue("RFF+LI[2]; else {POL_NUMBER}"),
              new MappingRule().withPath("invoice.invoiceLines[].fundDistributions[]")
                .withName("fundDistributions")
                .withRepeatableFieldAction(MappingRule.RepeatableFieldAction.EXTEND_EXISTING)
                .withValue("{POL_FUND_DISTRIBUTIONS}"),
              new MappingRule().withPath("invoice.invoiceLines[].subTotal")
                .withValue("MOA+203[2]"))))))));

  private MappingProfile mappingProfileWithMixedFundDistributionMapping = new MappingProfile()
    .withId(UUID.randomUUID().toString())
    .withIncomingRecordType(EDIFACT_INVOICE)
    .withExistingRecordType(INVOICE)
    .withMappingDetails(new MappingDetail()
      .withMappingFields(List.of(
        new MappingRule().withPath("invoice.vendorInvoiceNo").withValue("BGM+380+[1]").withEnabled("true"),
        new MappingRule().withPath("invoice.currency").withValue("CUX+2[2]").withEnabled("true"),
        new MappingRule().withPath("invoice.status").withValue("\"Open\"").withEnabled("true"),
        new MappingRule().withPath("invoice.invoiceLines[]").withEnabled("true").withName("invoiceLines")
          .withRepeatableFieldAction(MappingRule.RepeatableFieldAction.EXTEND_EXISTING)
          .withSubfields(List.of(new RepeatableSubfieldMapping()
            .withOrder(0)
            .withPath("invoice.invoiceLines[]")
            .withFields(List.of(
              new MappingRule().withPath("invoice.invoiceLines[].poLineId")
                .withName("poLineId")
                .withValue("RFF+LI[2]; else {POL_NUMBER}"),
              new MappingRule().withPath("invoice.invoiceLines[].subTotal")
                .withValue("MOA+203[2]"),
              new MappingRule().withPath("invoice.invoiceLines[].fundDistributions[]")
                .withRepeatableFieldAction(MappingRule.RepeatableFieldAction.EXTEND_EXISTING)
                .withSubfields(List.of(new RepeatableSubfieldMapping()
                  .withOrder(0)
                  .withPath("invoice.invoiceLines[].fundDistributions[]")
                  .withFields(List.of(
                    new MappingRule().withPath("invoice.invoiceLines[].fundDistributions[].fundId")
                      .withValue("RFF+BFN[2]"),
                    new MappingRule().withPath("invoice.invoiceLines[].fundDistributions[].expenseClassId")
                      .withValue("{POL_EXPENSE_CLASS}"),
                    new MappingRule().withPath("invoice.invoiceLines[].fundDistributions[].value")
                      .withValue("\"42\""))))))))))));

  private MappingProfile mappingProfileWithInvalidMappingSyntax = new MappingProfile()
    .withId(UUID.randomUUID().toString())
    .withIncomingRecordType(EDIFACT_INVOICE)
    .withExistingRecordType(INVOICE)
    .withMappingDetails(new MappingDetail()
      .withMappingFields(List.of(new MappingRule().withPath("invoice.vendorInvoiceNo")
        .withValue("test-invalid-expression")
        .withEnabled("true"))));
  private InvoiceIdStorageService invoiceIdStorageService;
  private EventHandler createInvoiceHandler = new CreateInvoiceEventHandler(new RestClient(), invoiceIdStorageService);
  private RestClient mockOrderLinesRestClient;

  @SneakyThrows
  @BeforeEach
  public void setUp(final VertxTestContext context) {
    super.setUp(context);
    mockOrderLinesRestClient = Mockito.mock(RestClient.class);
    invoiceIdStorageService = Mockito.mock(InvoiceIdStorageService.class);
    EventManager.clearEventHandlers();
    EventManager.registerEventHandler(new CreateInvoiceEventHandler(mockOrderLinesRestClient, invoiceIdStorageService));

    RecordToEntity recordToInvoice = RecordToEntity.builder()
      .table(EntityTable.INVOICES)
      .recordId(RECORD_ID)
      .entityId(INVOICE_ID).build();

    when(invoiceIdStorageService.store(any(), any(), any())).thenReturn(Future.succeededFuture(recordToInvoice));
    edifactParsedContent = getMockData(String.format(MOCK_DATA_PATH_PATTERN, EDIFACTS_MOCK_DATA_PATH, "edifact-parsed-content"));
    parsedContentInvoiceLine3HasNoSubTotal = getMockData(String.format(MOCK_DATA_PATH_PATTERN, EDIFACTS_MOCK_DATA_PATH, "invoice-line-3-has-no-subtotal"));
  }

  @Test
  public void shouldCreateInvoiceAndPublishDiCompletedEvent() {
    // given
    Record record = new Record().withParsedRecord(new ParsedRecord().withContent(edifactParsedContent)).withId(RECORD_ID);
    ProfileSnapshotWrapper profileSnapshotWrapper = buildProfileSnapshotWrapper(jobProfile, actionProfile, mappingProfile);
    addMockEntry(JOB_PROFILE_SNAPSHOTS_MOCK, profileSnapshotWrapper);
    String testId = UUID.randomUUID().toString();

    HashMap<String, String> payloadContext = new HashMap<>();
    payloadContext.put(EDIFACT_INVOICE.value(), Json.encode(record));
    payloadContext.put(JOB_PROFILE_SNAPSHOT_ID_KEY, profileSnapshotWrapper.getId());
    payloadContext.put("testId", testId);

    DataImportEventPayload dataImportEventPayload = new DataImportEventPayload()
      .withEventType(DI_INCOMING_EDIFACT_RECORD_PARSED.value())
      .withTenant(DI_POST_INVOICE_LINES_SUCCESS_TENANT)
      .withOkapiUrl(OKAPI_URL)
      .withToken(TOKEN)
      .withContext(payloadContext);

    String topic = KafkaTopicNameHelper.formatTopicName(KAFKA_ENV_VALUE, getDefaultNameSpace(), DI_POST_INVOICE_LINES_SUCCESS_TENANT, dataImportEventPayload.getEventType());
    Event event = new Event().withEventPayload(Json.encode(dataImportEventPayload));
    ProducerRecord<String, String> producerRecord = new ProducerRecord<>(topic, "test-key", Json.encode(event));
    producerRecord.headers().add(RECORD_ID_HEADER, record.getId().getBytes(UTF_8));

    // when
    try (KafkaProducer<String, String> producer = createKafkaProducer()) {
      producer.send(producerRecord);
    }

    // then
    String topicToObserve = KafkaTopicNameHelper.formatTopicName(KAFKA_ENV_VALUE, getDefaultNameSpace(), DI_POST_INVOICE_LINES_SUCCESS_TENANT, DI_COMPLETED.value());

    List<String> observedValues = observeValuesAndFilterByTestId(testId, topicToObserve, 1);

    Event obtainedEvent = Json.decodeValue(observedValues.get(0), Event.class);
    DataImportEventPayload eventPayload = Json.decodeValue(obtainedEvent.getEventPayload(), DataImportEventPayload.class);
    assertEquals(DI_INVOICE_CREATED.value(), eventPayload.getEventsChain().get(eventPayload.getEventsChain().size() -1));

    assertNotNull(eventPayload.getContext().get(INVOICE.value()));
    Invoice createdInvoice = Json.decodeValue(eventPayload.getContext().get(INVOICE.value()), Invoice.class);
    String actualInvoiceId = createdInvoice.getId();
    assertNotNull(actualInvoiceId);
    assertEquals(INVOICE_ID, actualInvoiceId);
    assertNotNull(createdInvoice.getVendorInvoiceNo());
    assertNotNull(createdInvoice.getCurrency());
    assertEquals("Open", createdInvoice.getStatus().value());
    assertEquals(Invoice.Source.EDI, createdInvoice.getSource());

    assertNotNull(eventPayload.getContext().get(EDIFACT_INVOICE.value()));
    Record sourceRecord = Json.decodeValue(eventPayload.getContext().get(EDIFACT_INVOICE.value()), Record.class);
    assertNull(sourceRecord.getParsedRecord());
    assertNull(sourceRecord.getRawRecord());

    assertNotNull(eventPayload.getContext().get(INVOICE_LINES_KEY));
    InvoiceLineCollection createdInvoiceLines = Json.decodeValue(eventPayload.getContext().get(INVOICE_LINES_KEY), InvoiceLineCollection.class);
    assertEquals(3, createdInvoiceLines.getTotalRecords());
    assertEquals(3, createdInvoiceLines.getInvoiceLines().size());
    createdInvoiceLines.getInvoiceLines().forEach(invLine -> {
      assertEquals(createdInvoice.getId(), invLine.getInvoiceId());
      assertEquals(InvoiceLineStatus.OPEN, invLine.getInvoiceLineStatus());
    });
  }

  @Test
  public void shouldNotProcessEventWhenRecordToInvoiceFutureFails() {
    // given
    Record record = new Record().withParsedRecord(new ParsedRecord().withContent(edifactParsedContent)).withId(RECORD_ID);
    ProfileSnapshotWrapper profileSnapshotWrapper = buildProfileSnapshotWrapper(jobProfile, actionProfile, mappingProfile);
    addMockEntry(JOB_PROFILE_SNAPSHOTS_MOCK, profileSnapshotWrapper);
    String testId = UUID.randomUUID().toString();

    HashMap<String, String> payloadContext = new HashMap<>();
    payloadContext.put(EDIFACT_INVOICE.value(), Json.encode(record));
    payloadContext.put(JOB_PROFILE_SNAPSHOT_ID_KEY, profileSnapshotWrapper.getId());
    payloadContext.put("testId", testId);

    DataImportEventPayload dataImportEventPayload = new DataImportEventPayload()
      .withEventType(DI_INCOMING_EDIFACT_RECORD_PARSED.value())
      .withTenant(DI_POST_INVOICE_LINES_SUCCESS_TENANT)
      .withOkapiUrl(OKAPI_URL)
      .withToken(TOKEN)
      .withContext(payloadContext);

    String topic = KafkaTopicNameHelper.formatTopicName(KAFKA_ENV_VALUE, getDefaultNameSpace(), DI_POST_INVOICE_LINES_SUCCESS_TENANT, dataImportEventPayload.getEventType());
    Event event = new Event().withEventPayload(Json.encode(dataImportEventPayload));
    ProducerRecord<String, String>  producerRecord = new ProducerRecord<>(topic, "test-key", Json.encode(event));
    producerRecord.headers().add(RECORD_ID_HEADER, record.getId().getBytes(UTF_8));

    // when
    when(invoiceIdStorageService.store(any(), any(), any())).thenReturn(Future.failedFuture(new Exception()));
    try (KafkaProducer<String, String> producer = createKafkaProducer()) {
      producer.send(producerRecord);
    }

    // then
    String topicToObserve = KafkaTopicNameHelper.formatTopicName(KAFKA_ENV_VALUE, getDefaultNameSpace(), DI_POST_INVOICE_LINES_SUCCESS_TENANT, DI_ERROR.value());
    List<String> observedValues = observeValuesAndFilterByTestId(testId, topicToObserve, 1);

    Event publishedEvent = Json.decodeValue(observedValues.get(0), Event.class);
    DataImportEventPayload eventPayload = Json.decodeValue(publishedEvent.getEventPayload(), DataImportEventPayload.class);
    assertEquals(DI_ERROR.value(), eventPayload.getEventType());
    assertEquals(DI_INVOICE_CREATED.value(), eventPayload.getEventsChain().get(eventPayload.getEventsChain().size() -1));
  }


  @Test
  public void shouldCreateInvoiceLinesWithCorrectOrderFromEdifactFile() throws IOException {
    // given
    String parsedEdifact = getMockData(String.format(MOCK_DATA_PATH_PATTERN, EDIFACTS_MOCK_DATA_PATH, FIVE_INVOICE_LINES_FILE));

    Record record = new Record().withParsedRecord(new ParsedRecord().withContent(parsedEdifact)).withId(RECORD_ID);
    ProfileSnapshotWrapper profileSnapshotWrapper = buildProfileSnapshotWrapper(jobProfile, actionProfile, mappingProfileWithPoLineSyntax);
    addMockEntry(JOB_PROFILE_SNAPSHOTS_MOCK, profileSnapshotWrapper);
    String testId = UUID.randomUUID().toString();

    HashMap<String, String> payloadContext = new HashMap<>();
    payloadContext.put(EDIFACT_INVOICE.value(), Json.encode(record));
    payloadContext.put(JOB_PROFILE_SNAPSHOT_ID_KEY, profileSnapshotWrapper.getId());
    payloadContext.put(DATA_IMPORT_PAYLOAD_OKAPI_PERMISSIONS, Json.encode(Collections.singletonList(AcqDesiredPermissions.ASSIGN.getPermission())));
    payloadContext.put(DATA_IMPORT_PAYLOAD_OKAPI_USER_ID, USER_ID);
    payloadContext.put("testId", testId);

    DataImportEventPayload dataImportEventPayload = new DataImportEventPayload()
      .withEventType(DI_INCOMING_EDIFACT_RECORD_PARSED.value())
      .withTenant(DI_POST_INVOICE_LINES_SUCCESS_TENANT)
      .withOkapiUrl(OKAPI_URL)
      .withToken(TOKEN)
      .withContext(payloadContext);

    String topic = KafkaTopicNameHelper.formatTopicName(KAFKA_ENV_VALUE, getDefaultNameSpace(), DI_POST_INVOICE_LINES_SUCCESS_TENANT, dataImportEventPayload.getEventType());
    Event event = new Event().withEventPayload(Json.encode(dataImportEventPayload));
    ProducerRecord<String, String>  producerRecord = new ProducerRecord<>(topic, "test-key", Json.encode(event));
    producerRecord.headers().add(RECORD_ID_HEADER, record.getId().getBytes(UTF_8));

    // when
    try (KafkaProducer<String, String> producer = createKafkaProducer()) {
      producer.send(producerRecord);
    }

    // then
    String topicToObserve = KafkaTopicNameHelper.formatTopicName(KAFKA_ENV_VALUE, getDefaultNameSpace(), DI_POST_INVOICE_LINES_SUCCESS_TENANT, DI_COMPLETED.value());
    List<String> observedValues = observeValuesAndFilterByTestId(testId, topicToObserve, 1);

    Event obtainedEvent = Json.decodeValue(observedValues.get(0), Event.class);
    DataImportEventPayload eventPayload = Json.decodeValue(obtainedEvent.getEventPayload(), DataImportEventPayload.class);
    assertEquals(DI_INVOICE_CREATED.value(), eventPayload.getEventsChain().get(eventPayload.getEventsChain().size() -1));

    assertNotNull(eventPayload.getContext().get(INVOICE.value()));
    Invoice createdInvoice = Json.decodeValue(eventPayload.getContext().get(INVOICE.value()), Invoice.class);

    assertNotNull(eventPayload.getContext().get(INVOICE_LINES_KEY));
    InvoiceLineCollection createdInvoiceLines = Json.decodeValue(eventPayload.getContext().get(INVOICE_LINES_KEY), InvoiceLineCollection.class);
    assertEquals(5, createdInvoiceLines.getTotalRecords());
    assertEquals(5, createdInvoiceLines.getInvoiceLines().size());
    createdInvoiceLines.getInvoiceLines().forEach(invLine -> assertEquals(createdInvoice.getId(), invLine.getInvoiceId()));

    assertNull(createdInvoiceLines.getInvoiceLines().get(1).getPoLineId());
    assertEquals("LAST HUMANITY: THE NEW ECOLOGICAL SCIENCE; TRANS. BY ANTHONY PAUL SMITH.", createdInvoiceLines.getInvoiceLines().get(2).getDescription());
  }

  @Test
  public void shouldMatchPoLinesByPoLineNumberAndCreateInvoiceLinesWithDescriptionFromPoLines() throws IOException {
    // given
    PoLine poLine1 = Json.decodeValue(getMockData(String.format(MOCK_DATA_PATH_PATTERN, PO_LINES_MOCK_DATA_PATH, PO_LINE_ID_1)), PoLine.class);
    PoLine poLine3 = Json.decodeValue(getMockData(String.format(MOCK_DATA_PATH_PATTERN, PO_LINES_MOCK_DATA_PATH, PO_LINE_ID_3)), PoLine.class);
    PoLineCollection poLineCollection = new PoLineCollection().withPoLines(List.of(poLine1, poLine3));

    when(mockOrderLinesRestClient.get(any(RequestEntry.class), eq(PoLineCollection.class), any(RequestContext.class)))
      .thenReturn(succeededFuture(poLineCollection));

    Record record = new Record().withParsedRecord(new ParsedRecord().withContent(edifactParsedContent)).withId(RECORD_ID);
    ProfileSnapshotWrapper profileSnapshotWrapper = buildProfileSnapshotWrapper(jobProfile, actionProfile, mappingProfileWithPoLineSyntax);
    addMockEntry(JOB_PROFILE_SNAPSHOTS_MOCK, profileSnapshotWrapper);
    String testId = UUID.randomUUID().toString();

    HashMap<String, String> payloadContext = new HashMap<>();
    payloadContext.put(EDIFACT_INVOICE.value(), Json.encode(record));
    payloadContext.put(JOB_PROFILE_SNAPSHOT_ID_KEY, profileSnapshotWrapper.getId());
    payloadContext.put(DATA_IMPORT_PAYLOAD_OKAPI_PERMISSIONS, Json.encode(Collections.singletonList(AcqDesiredPermissions.ASSIGN.getPermission())));
    payloadContext.put(DATA_IMPORT_PAYLOAD_OKAPI_USER_ID, USER_ID);
    payloadContext.put("testId", testId);

    DataImportEventPayload dataImportEventPayload = new DataImportEventPayload()
      .withEventType(DI_INCOMING_EDIFACT_RECORD_PARSED.value())
      .withTenant(DI_POST_INVOICE_LINES_SUCCESS_TENANT)
      .withOkapiUrl(OKAPI_URL)
      .withToken(TOKEN)
      .withContext(payloadContext);

    String topic = KafkaTopicNameHelper.formatTopicName(KAFKA_ENV_VALUE, getDefaultNameSpace(), DI_POST_INVOICE_LINES_SUCCESS_TENANT, dataImportEventPayload.getEventType());
    Event event = new Event().withEventPayload(Json.encode(dataImportEventPayload));
    ProducerRecord<String, String>  producerRecord = new ProducerRecord<>(topic, "test-key", Json.encode(event));
    producerRecord.headers().add(RECORD_ID_HEADER, record.getId().getBytes(UTF_8));

    // when
    try (KafkaProducer<String, String> producer = createKafkaProducer()) {
      producer.send(producerRecord);
    }

    // then
    String topicToObserve = KafkaTopicNameHelper.formatTopicName(KAFKA_ENV_VALUE, getDefaultNameSpace(), DI_POST_INVOICE_LINES_SUCCESS_TENANT, DI_COMPLETED.value());
    List<String> observedValues = observeValuesAndFilterByTestId(testId, topicToObserve, 1);

    Event obtainedEvent = Json.decodeValue(observedValues.get(0), Event.class);
    DataImportEventPayload eventPayload = Json.decodeValue(obtainedEvent.getEventPayload(), DataImportEventPayload.class);
    assertEquals(DI_INVOICE_CREATED.value(), eventPayload.getEventsChain().get(eventPayload.getEventsChain().size() -1));

    assertNotNull(eventPayload.getContext().get(INVOICE.value()));
    Invoice createdInvoice = Json.decodeValue(eventPayload.getContext().get(INVOICE.value()), Invoice.class);

    assertNotNull(eventPayload.getContext().get(INVOICE_LINES_KEY));
    InvoiceLineCollection createdInvoiceLines = Json.decodeValue(eventPayload.getContext().get(INVOICE_LINES_KEY), InvoiceLineCollection.class);
    assertEquals(3, createdInvoiceLines.getTotalRecords());
    assertEquals(3, createdInvoiceLines.getInvoiceLines().size());
    createdInvoiceLines.getInvoiceLines().forEach(invLine -> assertEquals(createdInvoice.getId(), invLine.getInvoiceId()));

    assertEquals(poLine1.getId(), createdInvoiceLines.getInvoiceLines().get(0).getPoLineId());
    assertEquals(poLine3.getId(), createdInvoiceLines.getInvoiceLines().get(2).getPoLineId());
    assertNull(createdInvoiceLines.getInvoiceLines().get(1).getPoLineId());
    assertEquals(poLine1.getTitleOrPackage(), createdInvoiceLines.getInvoiceLines().get(0).getDescription());
    assertEquals(poLine3.getTitleOrPackage(), createdInvoiceLines.getInvoiceLines().get(2).getDescription());
    assertEquals("ACI MATERIALS JOURNAL - ONLINE   -", createdInvoiceLines.getInvoiceLines().get(1).getDescription());
  }

  @Test
  public void shouldMatchPoLinesByRefNumberAndCreateInvoiceLinesWithDescriptionFromPoLines() throws IOException {
    // given
    PoLine poLine1 = Json.decodeValue(getMockData(String.format(MOCK_DATA_PATH_PATTERN, PO_LINES_MOCK_DATA_PATH, PO_LINE_ID_1)), PoLine.class);
    PoLine poLine3 = Json.decodeValue(getMockData(String.format(MOCK_DATA_PATH_PATTERN, PO_LINES_MOCK_DATA_PATH, PO_LINE_ID_3)), PoLine.class);

    when(mockOrderLinesRestClient.get(any(RequestEntry.class), eq(PoLineCollection.class), any(RequestContext.class)))
      .thenReturn(succeededFuture(new PoLineCollection().withPoLines(new ArrayList<>())))
      .thenReturn(succeededFuture(new PoLineCollection().withPoLines(List.of(poLine1))))
      .thenReturn(succeededFuture(new PoLineCollection().withPoLines(new ArrayList<>())))
      .thenReturn(succeededFuture(new PoLineCollection().withPoLines(List.of(poLine3))));

    ProfileSnapshotWrapper profileSnapshotWrapper = buildProfileSnapshotWrapper(jobProfile, actionProfile, mappingProfileWithPoLineSyntax);
    addMockEntry(JOB_PROFILE_SNAPSHOTS_MOCK, profileSnapshotWrapper);
    String testId = UUID.randomUUID().toString();

    Record record = new Record().withParsedRecord(new ParsedRecord().withContent(edifactParsedContent)).withId(RECORD_ID);
    HashMap<String, String> payloadContext = new HashMap<>();
    payloadContext.put(EDIFACT_INVOICE.value(), Json.encode(record));
    payloadContext.put(JOB_PROFILE_SNAPSHOT_ID_KEY, profileSnapshotWrapper.getId());
    payloadContext.put("testId", testId);

    DataImportEventPayload dataImportEventPayload = new DataImportEventPayload()
      .withEventType(DI_INCOMING_EDIFACT_RECORD_PARSED.value())
      .withTenant(DI_POST_INVOICE_LINES_SUCCESS_TENANT)
      .withOkapiUrl(OKAPI_URL)
      .withToken(TOKEN)
      .withContext(payloadContext);

    String topic = KafkaTopicNameHelper.formatTopicName(KAFKA_ENV_VALUE, getDefaultNameSpace(), DI_POST_INVOICE_LINES_SUCCESS_TENANT, dataImportEventPayload.getEventType());
    Event event = new Event().withEventPayload(Json.encode(dataImportEventPayload));
    ProducerRecord<String, String>  producerRecord = new ProducerRecord<>(topic, "test-key", Json.encode(event));
    producerRecord.headers().add(RECORD_ID_HEADER, record.getId().getBytes(UTF_8));

    // when
    try (KafkaProducer<String, String> producer = createKafkaProducer()) {
      producer.send(producerRecord);
    }

    // then
    String topicToObserve = KafkaTopicNameHelper.formatTopicName(KAFKA_ENV_VALUE, getDefaultNameSpace(), DI_POST_INVOICE_LINES_SUCCESS_TENANT, DI_COMPLETED.value());
    List<String> observedValues = observeValuesAndFilterByTestId(testId, topicToObserve, 1);

    Event obtainedEvent = Json.decodeValue(observedValues.get(0), Event.class);
    DataImportEventPayload eventPayload = Json.decodeValue(obtainedEvent.getEventPayload(), DataImportEventPayload.class);
    assertEquals(DI_INVOICE_CREATED.value(), eventPayload.getEventsChain().get(eventPayload.getEventsChain().size() -1));

    assertNotNull(eventPayload.getContext().get(INVOICE.value()));
    Invoice createdInvoice = Json.decodeValue(eventPayload.getContext().get(INVOICE.value()), Invoice.class);
    assertNotNull(createdInvoice.getVendorInvoiceNo());

    assertNotNull(eventPayload.getContext().get(INVOICE_LINES_KEY));
    InvoiceLineCollection createdInvoiceLines = Json.decodeValue(eventPayload.getContext().get(INVOICE_LINES_KEY), InvoiceLineCollection.class);
    assertEquals(3, createdInvoiceLines.getTotalRecords());
    assertEquals(3, createdInvoiceLines.getInvoiceLines().size());
    createdInvoiceLines.getInvoiceLines().forEach(invLine -> assertEquals(createdInvoice.getId(), invLine.getInvoiceId()));

    assertEquals(poLine1.getId(), createdInvoiceLines.getInvoiceLines().get(0).getPoLineId());
    assertEquals(poLine3.getId(), createdInvoiceLines.getInvoiceLines().get(2).getPoLineId());
    assertNull(createdInvoiceLines.getInvoiceLines().get(1).getPoLineId());
    assertEquals(poLine1.getTitleOrPackage(), createdInvoiceLines.getInvoiceLines().get(0).getDescription());
    assertEquals(poLine3.getTitleOrPackage(), createdInvoiceLines.getInvoiceLines().get(2).getDescription());
    assertEquals("ACI MATERIALS JOURNAL - ONLINE   -", createdInvoiceLines.getInvoiceLines().get(1).getDescription());

    assertFalse(createdInvoiceLines.getInvoiceLines().get(0).getReferenceNumbers().isEmpty());
    assertFalse(createdInvoiceLines.getInvoiceLines().get(1).getReferenceNumbers().isEmpty());
    assertFalse(createdInvoiceLines.getInvoiceLines().get(2).getReferenceNumbers().isEmpty());

    assertThat(poLine1.getVendorDetail().getReferenceNumbers(), hasItem(
      hasProperty("refNumber", is(createdInvoiceLines.getInvoiceLines().get(0).getReferenceNumbers().get(0).getRefNumber()))));
    assertThat(poLine3.getVendorDetail().getReferenceNumbers(), hasItem(
      hasProperty("refNumber", is(createdInvoiceLines.getInvoiceLines().get(2).getReferenceNumbers().get(0).getRefNumber()))));
  }

  @Test
  public void shouldMatchPoLinesByPoLineNumberAndCreateInvoiceLinesWithPoLinesFundDistributions() throws IOException {
    // given
    PoLine poLine1 = Json.decodeValue(getMockData(String.format(MOCK_DATA_PATH_PATTERN, PO_LINES_MOCK_DATA_PATH, PO_LINE_ID_1)), PoLine.class);
    PoLine poLine3 = Json.decodeValue(getMockData(String.format(MOCK_DATA_PATH_PATTERN, PO_LINES_MOCK_DATA_PATH, PO_LINE_ID_3)), PoLine.class);
    PoLineCollection poLineCollection = new PoLineCollection().withPoLines(List.of(poLine1, poLine3));

    when(mockOrderLinesRestClient.get(any(RequestEntry.class), eq(PoLineCollection.class), any(RequestContext.class)))
      .thenReturn(succeededFuture(poLineCollection));

    ProfileSnapshotWrapper profileSnapshotWrapper = buildProfileSnapshotWrapper(jobProfile, actionProfile, mappingProfileWithPoLineFundDistribution);
    addMockEntry(JOB_PROFILE_SNAPSHOTS_MOCK, profileSnapshotWrapper);
    String testId = UUID.randomUUID().toString();

    Record record = new Record().withParsedRecord(new ParsedRecord().withContent(edifactParsedContent)).withId(RECORD_ID);
    HashMap<String, String> payloadContext = new HashMap<>();
    payloadContext.put(EDIFACT_INVOICE.value(), Json.encode(record));
    payloadContext.put(JOB_PROFILE_SNAPSHOT_ID_KEY, profileSnapshotWrapper.getId());
    payloadContext.put("testId", testId);

    DataImportEventPayload dataImportEventPayload = new DataImportEventPayload()
      .withEventType(DI_INCOMING_EDIFACT_RECORD_PARSED.value())
      .withTenant(DI_POST_INVOICE_LINES_SUCCESS_TENANT)
      .withOkapiUrl(OKAPI_URL)
      .withToken(TOKEN)
      .withContext(payloadContext);

    String topic = KafkaTopicNameHelper.formatTopicName(KAFKA_ENV_VALUE, getDefaultNameSpace(), DI_POST_INVOICE_LINES_SUCCESS_TENANT, dataImportEventPayload.getEventType());
    Event event = new Event().withEventPayload(Json.encode(dataImportEventPayload));
    ProducerRecord<String, String>  producerRecord = new ProducerRecord<>(topic, "test-key", Json.encode(event));
    producerRecord.headers().add(RECORD_ID_HEADER, record.getId().getBytes(UTF_8));

    // when
    try (KafkaProducer<String, String> producer = createKafkaProducer()) {
      producer.send(producerRecord);
    }

    // then
    String topicToObserve = KafkaTopicNameHelper.formatTopicName(KAFKA_ENV_VALUE, getDefaultNameSpace(), DI_POST_INVOICE_LINES_SUCCESS_TENANT, DI_COMPLETED.value());
    List<String> observedValues = observeValuesAndFilterByTestId(testId, topicToObserve, 1);

    Event obtainedEvent = Json.decodeValue(observedValues.get(0), Event.class);
    DataImportEventPayload eventPayload = Json.decodeValue(obtainedEvent.getEventPayload(), DataImportEventPayload.class);
    assertEquals(DI_INVOICE_CREATED.value(), eventPayload.getEventsChain().get(eventPayload.getEventsChain().size() -1));

    assertNotNull(eventPayload.getContext().get(INVOICE.value()));
    Invoice createdInvoice = Json.decodeValue(eventPayload.getContext().get(INVOICE.value()), Invoice.class);

    assertNotNull(eventPayload.getContext().get(INVOICE_LINES_KEY));
    InvoiceLineCollection createdInvoiceLines = Json.decodeValue(eventPayload.getContext().get(INVOICE_LINES_KEY), InvoiceLineCollection.class);
    assertEquals(3, createdInvoiceLines.getTotalRecords());
    assertEquals(3, createdInvoiceLines.getInvoiceLines().size());
    createdInvoiceLines.getInvoiceLines().forEach(invLine -> assertEquals(createdInvoice.getId(), invLine.getInvoiceId()));

    assertEquals(poLine1.getId(), createdInvoiceLines.getInvoiceLines().get(0).getPoLineId());
    assertEquals(poLine3.getId(), createdInvoiceLines.getInvoiceLines().get(2).getPoLineId());
    assertNull(createdInvoiceLines.getInvoiceLines().get(1).getPoLineId());

    // compare fundDistributions as JsonObject since fund distributions are represented by different classes in invoice line and po line
    assertEquals(new JsonArray(Json.encode(poLine1.getFundDistribution())), new JsonArray(Json.encode(createdInvoiceLines.getInvoiceLines().get(0).getFundDistributions())));
    assertEquals(new JsonArray(Json.encode(poLine3.getFundDistribution())), new JsonArray(Json.encode(createdInvoiceLines.getInvoiceLines().get(2).getFundDistributions())));
    assertTrue(createdInvoiceLines.getInvoiceLines().get(1).getFundDistributions().isEmpty());
  }

  @Test
  public void shouldNotLinkInvoiceLinesToPoLinesWhenMultiplePoLinesAreMatchedByRefNumber() throws IOException {
    // given
    PoLine poLine1 = Json.decodeValue(getMockData(String.format(MOCK_DATA_PATH_PATTERN, PO_LINES_MOCK_DATA_PATH, PO_LINE_ID_1)), PoLine.class);
    PoLine poLine3 = Json.decodeValue(getMockData(String.format(MOCK_DATA_PATH_PATTERN, PO_LINES_MOCK_DATA_PATH, PO_LINE_ID_3)), PoLine.class);

    when(mockOrderLinesRestClient.get(any(RequestEntry.class), eq(PoLineCollection.class), any(RequestContext.class)))
      .thenReturn(succeededFuture(new PoLineCollection()))
      .thenReturn(succeededFuture(new PoLineCollection().withPoLines(List.of(poLine1, poLine3))));

    ProfileSnapshotWrapper profileSnapshotWrapper = buildProfileSnapshotWrapper(jobProfile, actionProfile, mappingProfileWithPoLineSyntax);
    addMockEntry(JOB_PROFILE_SNAPSHOTS_MOCK, profileSnapshotWrapper);
    String testId = UUID.randomUUID().toString();

    Record record = new Record().withParsedRecord(new ParsedRecord().withContent(edifactParsedContent)).withId(RECORD_ID);
    HashMap<String, String> payloadContext = new HashMap<>();
    payloadContext.put(EDIFACT_INVOICE.value(), Json.encode(record));
    payloadContext.put(JOB_PROFILE_SNAPSHOT_ID_KEY, profileSnapshotWrapper.getId());
    payloadContext.put("testId", testId);

    DataImportEventPayload dataImportEventPayload = new DataImportEventPayload()
      .withEventType(DI_INCOMING_EDIFACT_RECORD_PARSED.value())
      .withTenant(DI_POST_INVOICE_LINES_SUCCESS_TENANT)
      .withOkapiUrl(OKAPI_URL)
      .withToken(TOKEN)
      .withContext(payloadContext);

    String topic = KafkaTopicNameHelper.formatTopicName(KAFKA_ENV_VALUE, getDefaultNameSpace(), DI_POST_INVOICE_LINES_SUCCESS_TENANT, dataImportEventPayload.getEventType());
    Event event = new Event().withEventPayload(Json.encode(dataImportEventPayload));
    ProducerRecord<String, String>  producerRecord = new ProducerRecord<>(topic, "test-key", Json.encode(event));
    producerRecord.headers().add(RECORD_ID_HEADER, record.getId().getBytes(UTF_8));

    // when
    try (KafkaProducer<String, String> producer = createKafkaProducer()) {
      producer.send(producerRecord);
    }

    // then
    String topicToObserve = KafkaTopicNameHelper.formatTopicName(KAFKA_ENV_VALUE, getDefaultNameSpace(), DI_POST_INVOICE_LINES_SUCCESS_TENANT, DI_COMPLETED.value());
    List<String> observedValues = observeValuesAndFilterByTestId(testId, topicToObserve, 1);

    Event obtainedEvent = Json.decodeValue(observedValues.get(0), Event.class);
    DataImportEventPayload eventPayload = Json.decodeValue(obtainedEvent.getEventPayload(), DataImportEventPayload.class);
    assertEquals(DI_INVOICE_CREATED.value(), eventPayload.getEventsChain().get(eventPayload.getEventsChain().size() -1));

    assertNotNull(eventPayload.getContext().get(INVOICE_LINES_KEY));
    InvoiceLineCollection createdInvoiceLines = Json.decodeValue(eventPayload.getContext().get(INVOICE_LINES_KEY), InvoiceLineCollection.class);
    assertEquals(3, createdInvoiceLines.getTotalRecords());
    assertEquals(3, createdInvoiceLines.getInvoiceLines().size());

    assertNull(createdInvoiceLines.getInvoiceLines().get(0).getPoLineId());
    assertNull(createdInvoiceLines.getInvoiceLines().get(1).getPoLineId());
    assertNull(createdInvoiceLines.getInvoiceLines().get(2).getPoLineId());

    assertNotNull(createdInvoiceLines.getInvoiceLines().get(0).getDescription());
    assertNotNull(createdInvoiceLines.getInvoiceLines().get(1).getDescription());
    assertNotNull(createdInvoiceLines.getInvoiceLines().get(2).getDescription());
  }

  @Test
  public void shouldMatchPoLineByPoLineNumberAndLeaveEmptyInvoiceLineFundDistributionExpenseClassIdWhenMatchedPoLineHasDifferentExpenseClasses() throws IOException {
    // given
    PoLine poLine3 = Json.decodeValue(getMockData(String.format(MOCK_DATA_PATH_PATTERN, PO_LINES_MOCK_DATA_PATH, PO_LINE_ID_3)), PoLine.class);
    PoLineCollection poLineCollection = new PoLineCollection().withPoLines(List.of(poLine3));

    when(mockOrderLinesRestClient.get(any(RequestEntry.class), eq(PoLineCollection.class), any(RequestContext.class)))
      .thenReturn(succeededFuture(poLineCollection))
      .thenReturn(succeededFuture(new PoLineCollection()));

    ProfileSnapshotWrapper profileSnapshotWrapper = buildProfileSnapshotWrapper(jobProfile, actionProfile, mappingProfileWithMixedFundDistributionMapping);
    addMockEntry(JOB_PROFILE_SNAPSHOTS_MOCK, profileSnapshotWrapper);
    String testId = UUID.randomUUID().toString();

    Record record = new Record().withParsedRecord(new ParsedRecord().withContent(edifactParsedContent)).withId(RECORD_ID);
    HashMap<String, String> payloadContext = new HashMap<>();
    payloadContext.put(EDIFACT_INVOICE.value(), Json.encode(record));
    payloadContext.put(JOB_PROFILE_SNAPSHOT_ID_KEY, profileSnapshotWrapper.getId());
    payloadContext.put("testId", testId);

    DataImportEventPayload dataImportEventPayload = new DataImportEventPayload()
      .withEventType(DI_INCOMING_EDIFACT_RECORD_PARSED.value())
      .withTenant(DI_POST_INVOICE_LINES_SUCCESS_TENANT)
      .withOkapiUrl(OKAPI_URL)
      .withToken(TOKEN)
      .withContext(payloadContext);

    String topic = KafkaTopicNameHelper.formatTopicName(KAFKA_ENV_VALUE, getDefaultNameSpace(), DI_POST_INVOICE_LINES_SUCCESS_TENANT, dataImportEventPayload.getEventType());
    Event event = new Event().withEventPayload(Json.encode(dataImportEventPayload));
    ProducerRecord<String, String>  producerRecord = new ProducerRecord<>(topic, "test-key", Json.encode(event));
    producerRecord.headers().add(RECORD_ID_HEADER, record.getId().getBytes(UTF_8));

    // when
    try (KafkaProducer<String, String> producer = createKafkaProducer()) {
      producer.send(producerRecord);
    }

    // then
    String topicToObserve = KafkaTopicNameHelper.formatTopicName(KAFKA_ENV_VALUE, getDefaultNameSpace(), DI_POST_INVOICE_LINES_SUCCESS_TENANT, DI_COMPLETED.value());
    List<String> observedValues = observeValuesAndFilterByTestId(testId, topicToObserve, 1);

    Event obtainedEvent = Json.decodeValue(observedValues.get(0), Event.class);
    DataImportEventPayload eventPayload = Json.decodeValue(obtainedEvent.getEventPayload(), DataImportEventPayload.class);
    assertEquals(DI_INVOICE_CREATED.value(), eventPayload.getEventsChain().get(eventPayload.getEventsChain().size() -1));

    assertNotNull(eventPayload.getContext().get(INVOICE.value()));
    Invoice createdInvoice = Json.decodeValue(eventPayload.getContext().get(INVOICE.value()), Invoice.class);

    assertNotNull(eventPayload.getContext().get(INVOICE_LINES_KEY));
    InvoiceLineCollection createdInvoiceLines = Json.decodeValue(eventPayload.getContext().get(INVOICE_LINES_KEY), InvoiceLineCollection.class);
    assertEquals(3, createdInvoiceLines.getTotalRecords());
    assertEquals(3, createdInvoiceLines.getInvoiceLines().size());
    createdInvoiceLines.getInvoiceLines().forEach(invLine -> assertEquals(createdInvoice.getId(), invLine.getInvoiceId()));

    assertNull(createdInvoiceLines.getInvoiceLines().get(0).getPoLineId());
    assertNull(createdInvoiceLines.getInvoiceLines().get(1).getPoLineId());
    assertEquals(poLine3.getId(), createdInvoiceLines.getInvoiceLines().get(2).getPoLineId());

    assertEquals(1, createdInvoiceLines.getInvoiceLines().get(2).getFundDistributions().size());
    assertNotNull(createdInvoiceLines.getInvoiceLines().get(2).getFundDistributions().get(0).getFundId());
    assertNotNull(createdInvoiceLines.getInvoiceLines().get(2).getFundDistributions().get(0).getValue());

    assertEquals(2, poLine3.getFundDistribution().size());
    assertNotEquals(poLine3.getFundDistribution().get(0).getExpenseClassId(), poLine3.getFundDistribution().get(1).getExpenseClassId());
    assertNull(createdInvoiceLines.getInvoiceLines().get(2).getFundDistributions().get(0).getExpenseClassId());
  }

  @Test
  void shouldCreateInvoiceLinesWithFundDistributionsWhenFundDistributionMappingIsSpecified() {
    // given
    String expectedFundId = "7fbd5d84-62d1-44c6-9c45-6cb173998bbd";
    String expectedFundCode = "AFRICAHIST";
    double expectedDistributionValue = 100;

    MappingProfile mappingProfileWithFundDistribution = new MappingProfile()
      .withId(UUID.randomUUID().toString())
      .withIncomingRecordType(EDIFACT_INVOICE)
      .withExistingRecordType(INVOICE)
      .withMappingDetails(new MappingDetail()
        .withMappingFields(List.of(
          new MappingRule().withPath("invoice.vendorInvoiceNo").withValue("BGM+380+[1]").withEnabled("true"),
          new MappingRule().withPath("invoice.currency").withValue("CUX+2[2]").withEnabled("true"),
          new MappingRule().withPath("invoice.status").withValue("\"Open\"").withEnabled("true"),
          new MappingRule().withPath("invoice.invoiceLines[]").withEnabled("true").withName("invoiceLines")
            .withRepeatableFieldAction(MappingRule.RepeatableFieldAction.EXTEND_EXISTING)
            .withSubfields(List.of(new RepeatableSubfieldMapping()
              .withOrder(0)
              .withPath("invoice.invoiceLines[]")
              .withFields(List.of(
                new MappingRule().withPath("invoice.invoiceLines[].subTotal")
                  .withValue("MOA+203[2]"),
                new MappingRule().withPath("invoice.invoiceLines[].quantity")
                  .withValue("QTY+47[2]"),
                new MappingRule().withPath("invoice.invoiceLines[].fundDistributions[]")
                  .withName("fundDistributions")
                  .withRepeatableFieldAction(MappingRule.RepeatableFieldAction.EXTEND_EXISTING)
                  .withSubfields(List.of(new RepeatableSubfieldMapping()
                    .withOrder(0)
                    .withPath("invoice.invoiceLines[].fundDistributions[]")
                    .withFields(List.of(
                      new MappingRule().withPath("invoice.invoiceLines[].fundDistributions[].fundId")
                        .withName("fundId")
                        .withValue("\"African (History) (AFRICAHIST)\"")
                        .withAcceptedValues(new HashMap<>(Map.of(expectedFundId, "African (History) (AFRICAHIST)"))),
                      new MappingRule().withPath("invoice.invoiceLines[].fundDistributions[].value").withValue("\"100\""),
                      new MappingRule().withPath("invoice.invoiceLines[].fundDistributions[].distributionType")
                        .withValue("\"percentage\"")
                    )))))))))));

    Record record = new Record().withParsedRecord(new ParsedRecord().withContent(edifactParsedContent)).withId(RECORD_ID);
    ProfileSnapshotWrapper profileSnapshotWrapper = buildProfileSnapshotWrapper(jobProfile, actionProfile, mappingProfileWithFundDistribution);
    addMockEntry(JOB_PROFILE_SNAPSHOTS_MOCK, profileSnapshotWrapper);
    String testId = UUID.randomUUID().toString();

    HashMap<String, String> payloadContext = new HashMap<>();
    payloadContext.put(EDIFACT_INVOICE.value(), Json.encode(record));
    payloadContext.put(JOB_PROFILE_SNAPSHOT_ID_KEY, profileSnapshotWrapper.getId());
    payloadContext.put("testId", testId);

    DataImportEventPayload dataImportEventPayload = new DataImportEventPayload()
      .withEventType(DI_INCOMING_EDIFACT_RECORD_PARSED.value())
      .withTenant(DI_POST_INVOICE_LINES_SUCCESS_TENANT)
      .withOkapiUrl(OKAPI_URL)
      .withToken(TOKEN)
      .withContext(payloadContext);

    String topic = KafkaTopicNameHelper.formatTopicName(KAFKA_ENV_VALUE, getDefaultNameSpace(), DI_POST_INVOICE_LINES_SUCCESS_TENANT, dataImportEventPayload.getEventType());
    Event event = new Event().withEventPayload(Json.encode(dataImportEventPayload));
    ProducerRecord<String, String>  producerRecord = new ProducerRecord<>(topic, "test-key", Json.encode(event));
    producerRecord.headers().add(RECORD_ID_HEADER, record.getId().getBytes(UTF_8));

    // when
    try (KafkaProducer<String, String> producer = createKafkaProducer()) {
      producer.send(producerRecord);
    }

    // then
    String topicToObserve = KafkaTopicNameHelper.formatTopicName(KAFKA_ENV_VALUE, getDefaultNameSpace(), DI_POST_INVOICE_LINES_SUCCESS_TENANT, DI_COMPLETED.value());

    List<String> observedValues = observeValuesAndFilterByTestId(testId, topicToObserve, 1);

    Event obtainedEvent = Json.decodeValue(observedValues.get(0), Event.class);
    DataImportEventPayload eventPayload = Json.decodeValue(obtainedEvent.getEventPayload(), DataImportEventPayload.class);
    assertEquals(DI_INVOICE_CREATED.value(), eventPayload.getEventsChain().get(eventPayload.getEventsChain().size() -1));

    assertNotNull(eventPayload.getContext().get(INVOICE.value()));
    assertNotNull(eventPayload.getContext().get(INVOICE_LINES_KEY));
    InvoiceLineCollection createdInvoiceLines = Json.decodeValue(eventPayload.getContext().get(INVOICE_LINES_KEY), InvoiceLineCollection.class);
    assertEquals(3, createdInvoiceLines.getTotalRecords());
    assertEquals(3, createdInvoiceLines.getInvoiceLines().size());

    createdInvoiceLines.getInvoiceLines().forEach(invLine -> {
      assertEquals(1, invLine.getFundDistributions().size());
      assertEquals(expectedFundId, invLine.getFundDistributions().get(0).getFundId());
      assertEquals(expectedFundCode, invLine.getFundDistributions().get(0).getCode());
      assertEquals(expectedDistributionValue, invLine.getFundDistributions().get(0).getValue());
      assertEquals(PERCENTAGE, invLine.getFundDistributions().get(0).getDistributionType());
    });
  }

  @Test
  public void shouldPublishDiErrorEventWhenHasNoSourceRecord() {
    // given
    ProfileSnapshotWrapper profileSnapshotWrapper = buildProfileSnapshotWrapper(jobProfile, actionProfile, mappingProfile);
    addMockEntry(JOB_PROFILE_SNAPSHOTS_MOCK, profileSnapshotWrapper);
    String testId = UUID.randomUUID().toString();

    HashMap<String, String> payloadContext = new HashMap<>();
    payloadContext.put(JOB_PROFILE_SNAPSHOT_ID_KEY, profileSnapshotWrapper.getId());
    payloadContext.put("testId", testId);
    DataImportEventPayload dataImportEventPayload = new DataImportEventPayload()
      .withEventType(DI_INCOMING_EDIFACT_RECORD_PARSED.value())
      .withTenant(TENANT_ID)
      .withOkapiUrl(OKAPI_URL)
      .withToken(TOKEN)
      .withContext(payloadContext);

    String topic = KafkaTopicNameHelper.formatTopicName(KAFKA_ENV_VALUE, getDefaultNameSpace(), TENANT_ID, dataImportEventPayload.getEventType());
    Event event = new Event().withEventPayload(Json.encode(dataImportEventPayload));
    ProducerRecord<String, String>  producerRecord = new ProducerRecord<>(topic, "test-key", Json.encode(event));
    producerRecord.headers().add(RECORD_ID_HEADER, UUID.randomUUID().toString().getBytes(UTF_8));

    // when
    try (KafkaProducer<String, String> producer = createKafkaProducer()) {
      producer.send(producerRecord);
    }

    // then
    String topicToObserve = KafkaTopicNameHelper.formatTopicName(KAFKA_ENV_VALUE, getDefaultNameSpace(), TENANT_ID, DI_ERROR.value());
    List<String> observedValues = observeValuesAndFilterByTestId(testId, topicToObserve, 1);

    Event publishedEvent = Json.decodeValue(observedValues.get(0), Event.class);
    DataImportEventPayload eventPayload = Json.decodeValue(publishedEvent.getEventPayload(), DataImportEventPayload.class);
    assertEquals(DI_ERROR.value(), eventPayload.getEventType());
    assertEquals(DI_INVOICE_CREATED.value(), eventPayload.getEventsChain().get(eventPayload.getEventsChain().size() -1));
  }

  @Test
  public void shouldPublishDiErrorEventWhenPostInvoiceToStorageFailed() {
    // given
    ProfileSnapshotWrapper profileSnapshotWrapper = buildProfileSnapshotWrapper(jobProfile, actionProfile, mappingProfile);
    addMockEntry(JOB_PROFILE_SNAPSHOTS_MOCK, profileSnapshotWrapper);
    String testId = UUID.randomUUID().toString();

    Record record = new Record().withParsedRecord(new ParsedRecord().withContent(edifactParsedContent)).withId(RECORD_ID);
    HashMap<String, String> payloadContext = new HashMap<>();
    payloadContext.put(EDIFACT_INVOICE.value(), Json.encode(record));
    payloadContext.put(JOB_PROFILE_SNAPSHOT_ID_KEY, profileSnapshotWrapper.getId());
    payloadContext.put("testId", testId);

    DataImportEventPayload dataImportEventPayload = new DataImportEventPayload()
      .withEventType(DI_INCOMING_EDIFACT_RECORD_PARSED.value())
      .withTenant(ERROR_TENANT)
      .withOkapiUrl(OKAPI_URL)
      .withToken(TOKEN)
      .withContext(payloadContext);

    String topic = KafkaTopicNameHelper.formatTopicName(KAFKA_ENV_VALUE, getDefaultNameSpace(), ERROR_TENANT, dataImportEventPayload.getEventType());
    Event event = new Event().withEventPayload(Json.encode(dataImportEventPayload));
    ProducerRecord<String, String>  producerRecord = new ProducerRecord<>(topic, "test-key", Json.encode(event));
    producerRecord.headers().add(RECORD_ID_HEADER, record.getId().getBytes(UTF_8));

    // when
    try (KafkaProducer<String, String> producer = createKafkaProducer()) {
      producer.send(producerRecord);
    }

    // then
    String topicToObserve = KafkaTopicNameHelper.formatTopicName(KAFKA_ENV_VALUE, getDefaultNameSpace(), ERROR_TENANT, DI_ERROR.value());
    List<String> observedValues = observeValuesAndFilterByTestId(testId, topicToObserve, 1);

    Event publishedEvent = Json.decodeValue(observedValues.get(0), Event.class);
    DataImportEventPayload eventPayload = Json.decodeValue(publishedEvent.getEventPayload(), DataImportEventPayload.class);
    assertEquals(DI_INVOICE_CREATED.value(), eventPayload.getEventsChain().get(eventPayload.getEventsChain().size() -1));
    assertNotNull(eventPayload.getContext().get(ERROR_MSG_KEY));
    assertNotNull(eventPayload.getContext().get(INVOICE.value()));
    assertNotNull(eventPayload.getContext().get(INVOICE_LINES_KEY));

    assertNotNull(eventPayload.getContext().get(EDIFACT_INVOICE.value()));
    Record sourceRecord = Json.decodeValue(eventPayload.getContext().get(EDIFACT_INVOICE.value()), Record.class);
    assertNull(sourceRecord.getParsedRecord());
    assertNull(sourceRecord.getRawRecord());

    InvoiceLineCollection invoiceLineCollection = Json.decodeValue(eventPayload.getContext().get(INVOICE_LINES_KEY), InvoiceLineCollection.class);
    assertEquals(3, invoiceLineCollection.getTotalRecords());
    assertEquals(3, invoiceLineCollection.getInvoiceLines().size());
  }

  @Test
  public void shouldNotPublishDiErrorEventWhenDuplicateException() {
    // given
    ProfileSnapshotWrapper profileSnapshotWrapper = buildProfileSnapshotWrapper(jobProfile, actionProfile, mappingProfile);
    addMockEntry(JOB_PROFILE_SNAPSHOTS_MOCK, profileSnapshotWrapper);
    String testId = UUID.randomUUID().toString();

    Record record = new Record().withParsedRecord(new ParsedRecord().withContent(edifactParsedContent)).withId(RECORD_ID);
    HashMap<String, String> payloadContext = new HashMap<>();
    payloadContext.put(EDIFACT_INVOICE.value(), Json.encode(record));
    payloadContext.put(JOB_PROFILE_SNAPSHOT_ID_KEY, profileSnapshotWrapper.getId());
    payloadContext.put("testId", testId);

    DataImportEventPayload dataImportEventPayload = new DataImportEventPayload()
      .withEventType(DI_INCOMING_EDIFACT_RECORD_PARSED.value())
      .withTenant(DUPLICATE_ERROR_TENANT)
      .withOkapiUrl(OKAPI_URL)
      .withToken(TOKEN)
      .withContext(payloadContext);

    String topic = KafkaTopicNameHelper.formatTopicName(KAFKA_ENV_VALUE, getDefaultNameSpace(), DUPLICATE_ERROR_TENANT, dataImportEventPayload.getEventType());
    Event event = new Event().withEventPayload(Json.encode(dataImportEventPayload));
    ProducerRecord<String, String>  producerRecord = new ProducerRecord<>(topic, "test-key", Json.encode(event));
    producerRecord.headers().add(RECORD_ID_HEADER, record.getId().getBytes(UTF_8));

    // when
    try (KafkaProducer<String, String> producer = createKafkaProducer()) {
      producer.send(producerRecord);
    }

    // then
    String topicToObserve = KafkaTopicNameHelper.formatTopicName(KAFKA_ENV_VALUE, getDefaultNameSpace(), DUPLICATE_ERROR_TENANT, DI_ERROR.value());
    List<String> observedValues = observeValuesAndFilterByTestId(testId, topicToObserve, 0);

    assertEquals(0, observedValues.size());
  }

  @Test
  public void shouldPublishDiErrorWithInvoiceLineErrorWhenOneOfInvoiceLinesCreationFailed() throws IOException {
    // given
    Record record = new Record().withParsedRecord(new ParsedRecord().withContent(parsedContentInvoiceLine3HasNoSubTotal))
      .withId(RECORD_ID);
    ProfileSnapshotWrapper profileSnapshotWrapper = buildProfileSnapshotWrapper(jobProfile, actionProfile, mappingProfile);
    addMockEntry(JOB_PROFILE_SNAPSHOTS_MOCK, profileSnapshotWrapper);
    String testId = UUID.randomUUID().toString();

    HashMap<String, String> payloadContext = new HashMap<>();
    payloadContext.put(EDIFACT_INVOICE.value(), Json.encode(record));
    payloadContext.put(JOB_PROFILE_SNAPSHOT_ID_KEY, profileSnapshotWrapper.getId());
    payloadContext.put("testId", testId);

    DataImportEventPayload dataImportEventPayload = new DataImportEventPayload()
      .withEventType(DI_INCOMING_EDIFACT_RECORD_PARSED.value())
      .withTenant(DI_POST_INVOICE_LINES_SUCCESS_TENANT)
      .withOkapiUrl(OKAPI_URL)
      .withToken(TOKEN)
      .withContext(payloadContext);

    String topic = KafkaTopicNameHelper.formatTopicName(KAFKA_ENV_VALUE, getDefaultNameSpace(), DI_POST_INVOICE_LINES_SUCCESS_TENANT, dataImportEventPayload.getEventType());
    Event event = new Event().withEventPayload(Json.encode(dataImportEventPayload));
    ProducerRecord<String, String>  producerRecord = new ProducerRecord<>(topic, "test-key", Json.encode(event));
    producerRecord.headers().add(RECORD_ID_HEADER, record.getId().getBytes(UTF_8));

    // when
    try (KafkaProducer<String, String> producer = createKafkaProducer()) {
      producer.send(producerRecord);
    }

    // then
    String topicToObserve = KafkaTopicNameHelper.formatTopicName(KAFKA_ENV_VALUE, getDefaultNameSpace(), DI_POST_INVOICE_LINES_SUCCESS_TENANT, DI_ERROR.value());

    List<String> observedValues = observeValuesAndFilterByTestId(testId, topicToObserve, 1);

    Event obtainedEvent = Json.decodeValue(observedValues.get(0), Event.class);
    DataImportEventPayload eventPayload = Json.decodeValue(obtainedEvent.getEventPayload(), DataImportEventPayload.class);
    assertEquals(DI_INVOICE_CREATED.value(), eventPayload.getEventsChain().get(eventPayload.getEventsChain().size() -1));

    assertNotNull(eventPayload.getContext().get(INVOICE.value()));
    Invoice createdInvoice = Json.decodeValue(eventPayload.getContext().get(INVOICE.value()), Invoice.class);

    assertNotNull(eventPayload.getContext().get(INVOICE_LINES_KEY));
    InvoiceLineCollection invoiceLines = Json.decodeValue(eventPayload.getContext().get(INVOICE_LINES_KEY), InvoiceLineCollection.class);
    assertEquals(3, invoiceLines.getTotalRecords());
    assertEquals(3, invoiceLines.getInvoiceLines().size());
    invoiceLines.getInvoiceLines().forEach(invLine -> assertEquals(createdInvoice.getId(), invLine.getInvoiceId()));

    assertNotNull(eventPayload.getContext().get(INVOICE_LINES_ERRORS_KEY));
    Map<Integer, String> invoiceLinesErrors = DatabindCodec.mapper().readValue(eventPayload.getContext().get(INVOICE_LINES_ERRORS_KEY), new TypeReference<>() {});
    assertEquals(1, invoiceLinesErrors.size());
    assertNull(invoiceLinesErrors.get(1));
    assertNull(invoiceLinesErrors.get(2));
    assertNotNull(invoiceLinesErrors.get(3));
  }

  @Test
  public void shouldPublishDiErrorWhenMappingProfileHasInvalidMappingSyntax() {
    // given
    Record record = new Record().withParsedRecord(new ParsedRecord().withContent(parsedContentInvoiceLine3HasNoSubTotal))
      .withId(RECORD_ID);
    ProfileSnapshotWrapper profileSnapshotWrapper = buildProfileSnapshotWrapper(jobProfile, actionProfile, mappingProfileWithInvalidMappingSyntax);
    addMockEntry(JOB_PROFILE_SNAPSHOTS_MOCK, profileSnapshotWrapper);
    String testId = UUID.randomUUID().toString();

    HashMap<String, String> payloadContext = new HashMap<>();
    payloadContext.put(EDIFACT_INVOICE.value(), Json.encode(record));
    payloadContext.put(JOB_PROFILE_SNAPSHOT_ID_KEY, profileSnapshotWrapper.getId());
    payloadContext.put("testId", testId);

    DataImportEventPayload dataImportEventPayload = new DataImportEventPayload()
      .withEventType(DI_INCOMING_EDIFACT_RECORD_PARSED.value())
      .withTenant(DI_POST_INVOICE_LINES_SUCCESS_TENANT)
      .withOkapiUrl(OKAPI_URL)
      .withToken(TOKEN)
      .withContext(payloadContext);

    String topic = KafkaTopicNameHelper.formatTopicName(KAFKA_ENV_VALUE, getDefaultNameSpace(), DI_POST_INVOICE_LINES_SUCCESS_TENANT, dataImportEventPayload.getEventType());
    Event event = new Event().withEventPayload(Json.encode(dataImportEventPayload));
    ProducerRecord<String, String>  producerRecord = new ProducerRecord<>(topic, "test-key", Json.encode(event));
    producerRecord.headers().add(RECORD_ID_HEADER, record.getId().getBytes(UTF_8));

    // when
    try (KafkaProducer<String, String> producer = createKafkaProducer()) {
      producer.send(producerRecord);
    }

    // then
    String topicToObserve = KafkaTopicNameHelper.formatTopicName(KAFKA_ENV_VALUE, getDefaultNameSpace(), DI_POST_INVOICE_LINES_SUCCESS_TENANT, DI_ERROR.value());
    List<String> observedValues = observeValuesAndFilterByTestId(testId, topicToObserve, 1);

    Event obtainedEvent = Json.decodeValue(observedValues.get(0), Event.class);
    DataImportEventPayload eventPayload = Json.decodeValue(obtainedEvent.getEventPayload(), DataImportEventPayload.class);
    assertEquals(DI_INVOICE_CREATED.value(), eventPayload.getEventsChain().get(eventPayload.getEventsChain().size() -1));
  }

  @Test
  public void shouldReturnTrueWhenHandlerIsEligibleForActionProfile() {
    // given
    ProfileSnapshotWrapper profileSnapshotWrapper = buildProfileSnapshotWrapper(jobProfile, actionProfile, mappingProfile);
    DataImportEventPayload dataImportEventPayload = new DataImportEventPayload()
      .withEventType(DI_INCOMING_EDIFACT_RECORD_PARSED.value())
      .withCurrentNode(profileSnapshotWrapper.getChildSnapshotWrappers().get(0));

    // when
    boolean isEligible = createInvoiceHandler.isEligible(dataImportEventPayload);

    // then
    Assertions.assertTrue(isEligible);
  }

  @Test
  public void shouldReturnFalseWhenHandlerIsNotEligibleForActionProfile() {
    // given
    ActionProfile actionProfile = new ActionProfile()
      .withId(UUID.randomUUID().toString())
      .withName("Create instance")
      .withAction(ActionProfile.Action.CREATE)
      .withFolioRecord(ActionProfile.FolioRecord.INSTANCE);

    ProfileSnapshotWrapper profileSnapshotWrapper = new ProfileSnapshotWrapper()
      .withId(UUID.randomUUID().toString())
      .withProfileId(jobProfile.getId())
      .withContentType(ACTION_PROFILE)
      .withContent(actionProfile);

    DataImportEventPayload dataImportEventPayload = new DataImportEventPayload()
      .withEventType(DI_INCOMING_EDIFACT_RECORD_PARSED.value())
      .withCurrentNode(profileSnapshotWrapper);

    // when
    boolean isEligible = createInvoiceHandler.isEligible(dataImportEventPayload);

    // then
    Assertions.assertFalse(isEligible);
  }

  private ProfileSnapshotWrapper buildProfileSnapshotWrapper(JobProfile jobProfile, ActionProfile actionProfile, MappingProfile mappingProfile) {
    return new ProfileSnapshotWrapper()
      .withId(UUID.randomUUID().toString())
      .withProfileId(jobProfile.getId())
      .withContentType(JOB_PROFILE)
      .withContent(JsonObject.mapFrom(jobProfile).getMap())
      .withChildSnapshotWrappers(Collections.singletonList(
        new ProfileSnapshotWrapper()
          .withId(UUID.randomUUID().toString())
          .withProfileId(actionProfile.getId())
          .withContentType(ACTION_PROFILE)
          .withContent(JsonObject.mapFrom(actionProfile).getMap())
          .withChildSnapshotWrappers(Collections.singletonList(
            new ProfileSnapshotWrapper()
              .withId(UUID.randomUUID().toString())
              .withProfileId(mappingProfile.getId())
              .withContentType(MAPPING_PROFILE)
              .withContent(JsonObject.mapFrom(mappingProfile).getMap())))));
  }

  public List<String> observeValuesAndFilterByTestId(String testId, String topicToObserve, Integer countToObserve) {
    List<String> result = new ArrayList<>();
    List<String> observedValues = observeTopic(topicToObserve, Duration.ofSeconds(30));
    assertEquals(countToObserve, observedValues.size());
    for (String observedValue : observedValues) {
      if (observedValue.contains(testId)) {
        result.add(observedValue);
      }
    }
    return result;
  }

}
