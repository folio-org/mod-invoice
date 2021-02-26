package org.folio.dataimport.handlers.actions;

import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import net.mguenther.kafka.junit.KeyValue;
import net.mguenther.kafka.junit.ObserveKeyValues;
import net.mguenther.kafka.junit.SendKeyValues;
import org.folio.ActionProfile;
import org.folio.ApiTestSuite;
import org.folio.DataImportEventPayload;
import org.folio.JobProfile;
import org.folio.MappingProfile;
import org.folio.ParsedRecord;
import org.folio.Record;
import org.folio.kafka.KafkaTopicNameHelper;
import org.folio.processing.events.services.handler.EventHandler;
import org.folio.processing.events.utils.ZIPArchiver;
import org.folio.rest.impl.ApiTestBase;
import org.folio.rest.jaxrs.model.Event;
import org.folio.rest.jaxrs.model.Invoice;
import org.folio.rest.jaxrs.model.InvoiceLineCollection;
import org.folio.rest.jaxrs.model.MappingDetail;
import org.folio.rest.jaxrs.model.MappingRule;
import org.folio.rest.jaxrs.model.ProfileSnapshotWrapper;
import org.folio.rest.jaxrs.model.RepeatableSubfieldMapping;
import org.folio.dataimport.handlers.actions.CreateInvoiceEventHandler;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.folio.ActionProfile.Action.CREATE;
import static org.folio.ApiTestSuite.KAFKA_ENV_VALUE;
import static org.folio.ApiTestSuite.kafkaCluster;
import static org.folio.DataImportEventTypes.DI_COMPLETED;
import static org.folio.DataImportEventTypes.DI_ERROR;
import static org.folio.kafka.KafkaTopicNameHelper.getDefaultNameSpace;
import static org.folio.rest.impl.MockServer.DI_POST_INVOICE_LINES_SUCCESS_TENANT;
import static org.folio.rest.jaxrs.model.EntityType.EDIFACT_INVOICE;
import static org.folio.rest.jaxrs.model.EntityType.INVOICE;
import static org.folio.rest.jaxrs.model.ProfileSnapshotWrapper.ContentType.ACTION_PROFILE;
import static org.folio.rest.jaxrs.model.ProfileSnapshotWrapper.ContentType.JOB_PROFILE;
import static org.folio.rest.jaxrs.model.ProfileSnapshotWrapper.ContentType.MAPPING_PROFILE;
import static org.folio.dataimport.handlers.actions.CreateInvoiceEventHandler.DI_INVOICE_CREATED_EVENT;
import static org.folio.dataimport.handlers.actions.CreateInvoiceEventHandler.INVOICE_LINES_KEY;
import static org.folio.verticles.DataImportConsumerVerticle.EDIFACT_RECORD_CREATED_EVENT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@ExtendWith(VertxExtension.class)
public class CreateInvoiceEventHandlerTest extends ApiTestBase {

  private static final String EDIFACT_PARSED_CONTENT = "{\"segments\": [{\"tag\": \"UNA\", \"dataElements\": []}, {\"tag\": \"UNB\", \"dataElements\": [{\"components\": [{\"data\": \"UNOC\"}, {\"data\": \"3\"}]}, {\"components\": [{\"data\": \"EBSCO\"}, {\"data\": \"92\"}]}, {\"components\": [{\"data\": \"KOH0002\"}, {\"data\": \"91\"}]}, {\"components\": [{\"data\": \"200610\"}, {\"data\": \"0105\"}]}, {\"components\": [{\"data\": \"5162\"}]}]}, {\"tag\": \"UNH\", \"dataElements\": [{\"components\": [{\"data\": \"5162\"}]}, {\"components\": [{\"data\": \"INVOIC\"}, {\"data\": \"D\"}, {\"data\": \"96A\"}, {\"data\": \"UN\"}, {\"data\": \"EAN008\"}]}]}, {\"tag\": \"BGM\", \"dataElements\": [{\"components\": [{\"data\": \"380\"}, {\"data\": \"\"}, {\"data\": \"\"}, {\"data\": \"JINV\"}]}, {\"components\": [{\"data\": \"0704159\"}]}, {\"components\": [{\"data\": \"43\"}]}]}, {\"tag\": \"DTM\", \"dataElements\": [{\"components\": [{\"data\": \"137\"}, {\"data\": \"20191002\"}, {\"data\": \"102\"}]}]}, {\"tag\": \"NAD\", \"dataElements\": [{\"components\": [{\"data\": \"BY\"}]}, {\"components\": [{\"data\": \"BR1624506\"}, {\"data\": \"\"}, {\"data\": \"91\"}]}]}, {\"tag\": \"NAD\", \"dataElements\": [{\"components\": [{\"data\": \"SR\"}]}, {\"components\": [{\"data\": \"EBSCO\"}, {\"data\": \"\"}, {\"data\": \"92\"}]}]}, {\"tag\": \"CUX\", \"dataElements\": [{\"components\": [{\"data\": \"2\"}, {\"data\": \"USD\"}, {\"data\": \"4\"}]}]}, {\"tag\": \"LIN\", \"dataElements\": [{\"components\": [{\"data\": \"1\"}]}]}, {\"tag\": \"PIA\", \"dataElements\": [{\"components\": [{\"data\": \"5\"}]}, {\"components\": [{\"data\": \"004362033\"}, {\"data\": \"SA\"}]}, {\"components\": [{\"data\": \"1941-6067\"}, {\"data\": \"IS\"}]}]}, {\"tag\": \"PIA\", \"dataElements\": [{\"components\": [{\"data\": \"5S\"}]}, {\"components\": [{\"data\": \"1941-6067(20200101)14;1-F\"}, {\"data\": \"SI\"}, {\"data\": \"\"}, {\"data\": \"28\"}]}]}, {\"tag\": \"PIA\", \"dataElements\": [{\"components\": [{\"data\": \"5E\"}]}, {\"components\": [{\"data\": \"1941-6067(20201231)14;1-F\"}, {\"data\": \"SI\"}, {\"data\": \"\"}, {\"data\": \"28\"}]}]}, {\"tag\": \"IMD\", \"dataElements\": [{\"components\": [{\"data\": \"L\"}]}, {\"components\": [{\"data\": \"050\"}]}, {\"components\": [{\"data\": \"\"}, {\"data\": \"\"}, {\"data\": \"\"}, {\"data\": \"ACADEMY OF MANAGEMENT ANNALS -   ON\"}, {\"data\": \"LINE FOR INSTITUTIONS\"}]}]}, {\"tag\": \"QTY\", \"dataElements\": [{\"components\": [{\"data\": \"47\"}, {\"data\": \"1\"}]}]}, {\"tag\": \"DTM\", \"dataElements\": [{\"components\": [{\"data\": \"194\"}, {\"data\": \"20200101\"}, {\"data\": \"102\"}]}]}, {\"tag\": \"DTM\", \"dataElements\": [{\"components\": [{\"data\": \"206\"}, {\"data\": \"20201231\"}, {\"data\": \"102\"}]}]}, {\"tag\": \"MOA\", \"dataElements\": [{\"components\": [{\"data\": \"203\"}, {\"data\": \"208.59\"}, {\"data\": \"USD\"}, {\"data\": \"4\"}]}]}, {\"tag\": \"PRI\", \"dataElements\": [{\"components\": [{\"data\": \"AAB\"}, {\"data\": \"205\"}]}]}, {\"tag\": \"RFF\", \"dataElements\": [{\"components\": [{\"data\": \"LI\"}, {\"data\": \"S255699\"}]}]}, {\"tag\": \"RFF\", \"dataElements\": [{\"components\": [{\"data\": \"SNA\"}, {\"data\": \"C6546362\"}]}]}, {\"tag\": \"ALC\", \"dataElements\": [{\"components\": [{\"data\": \"C\"}]}, {\"components\": [{\"data\": \"\"}]}, {\"components\": [{\"data\": \"\"}]}, {\"components\": [{\"data\": \"\"}]}, {\"components\": [{\"data\": \"G74\"}, {\"data\": \"\"}, {\"data\": \"28\"}, {\"data\": \"LINE SERVICE CHARGE\"}]}]}, {\"tag\": \"MOA\", \"dataElements\": [{\"components\": [{\"data\": \"8\"}, {\"data\": \"3.59\"}]}]}, {\"tag\": \"LIN\", \"dataElements\": [{\"components\": [{\"data\": \"2\"}]}]}, {\"tag\": \"PIA\", \"dataElements\": [{\"components\": [{\"data\": \"5\"}]}, {\"components\": [{\"data\": \"006288237\"}, {\"data\": \"SA\"}]}, {\"components\": [{\"data\": \"1944-737X\"}, {\"data\": \"IS\"}]}]}, {\"tag\": \"PIA\", \"dataElements\": [{\"components\": [{\"data\": \"5S\"}]}, {\"components\": [{\"data\": \"1944-737X(20200301)117;1-F\"}, {\"data\": \"SI\"}, {\"data\": \"\"}, {\"data\": \"28\"}]}]}, {\"tag\": \"PIA\", \"dataElements\": [{\"components\": [{\"data\": \"5E\"}]}, {\"components\": [{\"data\": \"1944-737X(20210228)118;1-F\"}, {\"data\": \"SI\"}, {\"data\": \"\"}, {\"data\": \"28\"}]}]}, {\"tag\": \"IMD\", \"dataElements\": [{\"components\": [{\"data\": \"L\"}]}, {\"components\": [{\"data\": \"050\"}]}, {\"components\": [{\"data\": \"\"}, {\"data\": \"\"}, {\"data\": \"\"}, {\"data\": \"ACI MATERIALS JOURNAL - ONLINE   -\"}, {\"data\": \"MULTI USER\"}]}]}, {\"tag\": \"QTY\", \"dataElements\": [{\"components\": [{\"data\": \"47\"}, {\"data\": \"1\"}]}]}, {\"tag\": \"DTM\", \"dataElements\": [{\"components\": [{\"data\": \"194\"}, {\"data\": \"20200301\"}, {\"data\": \"102\"}]}]}, {\"tag\": \"DTM\", \"dataElements\": [{\"components\": [{\"data\": \"206\"}, {\"data\": \"20210228\"}, {\"data\": \"102\"}]}]}, {\"tag\": \"MOA\", \"dataElements\": [{\"components\": [{\"data\": \"203\"}, {\"data\": \"726.5\"}, {\"data\": \"USD\"}, {\"data\": \"4\"}]}]}, {\"tag\": \"PRI\", \"dataElements\": [{\"components\": [{\"data\": \"AAB\"}, {\"data\": \"714\"}]}]}, {\"tag\": \"RFF\", \"dataElements\": [{\"components\": [{\"data\": \"LI\"}, {\"data\": \"S283902\"}]}]}, {\"tag\": \"RFF\", \"dataElements\": [{\"components\": [{\"data\": \"SNA\"}, {\"data\": \"E9498295\"}]}]}, {\"tag\": \"ALC\", \"dataElements\": [{\"components\": [{\"data\": \"C\"}]}, {\"components\": [{\"data\": \"\"}]}, {\"components\": [{\"data\": \"\"}]}, {\"components\": [{\"data\": \"\"}]}, {\"components\": [{\"data\": \"G74\"}, {\"data\": \"\"}, {\"data\": \"28\"}, {\"data\": \"LINE SERVICE CHARGE\"}]}]}, {\"tag\": \"MOA\", \"dataElements\": [{\"components\": [{\"data\": \"8\"}, {\"data\": \"12.5\"}]}]}, {\"tag\": \"LIN\", \"dataElements\": [{\"components\": [{\"data\": \"3\"}]}]}, {\"tag\": \"PIA\", \"dataElements\": [{\"components\": [{\"data\": \"5\"}]}, {\"components\": [{\"data\": \"006289532\"}, {\"data\": \"SA\"}]}, {\"components\": [{\"data\": \"1944-7361\"}, {\"data\": \"IS\"}]}]}, {\"tag\": \"PIA\", \"dataElements\": [{\"components\": [{\"data\": \"5S\"}]}, {\"components\": [{\"data\": \"1944-7361(20200301)117;1-F\"}, {\"data\": \"SI\"}, {\"data\": \"\"}, {\"data\": \"28\"}]}]}, {\"tag\": \"PIA\", \"dataElements\": [{\"components\": [{\"data\": \"5E\"}]}, {\"components\": [{\"data\": \"1944-7361(20210228)118;1-F\"}, {\"data\": \"SI\"}, {\"data\": \"\"}, {\"data\": \"28\"}]}]}, {\"tag\": \"IMD\", \"dataElements\": [{\"components\": [{\"data\": \"L\"}]}, {\"components\": [{\"data\": \"050\"}]}, {\"components\": [{\"data\": \"\"}, {\"data\": \"\"}, {\"data\": \"\"}, {\"data\": \"GRADUATE PROGRAMS IN PHYSICS, ASTRO\"}, {\"data\": \"NOMY AND \"}]}]}, {\"tag\": \"IMD\", \"dataElements\": [{\"components\": [{\"data\": \"L\"}]}, {\"components\": [{\"data\": \"050\"}]}, {\"components\": [{\"data\": \"\"}, {\"data\": \"\"}, {\"data\": \"\"}, {\"data\": \"RELATED FIELDS.\"}]}]}, {\"tag\": \"QTY\", \"dataElements\": [{\"components\": [{\"data\": \"47\"}, {\"data\": \"1\"}]}]}, {\"tag\": \"DTM\", \"dataElements\": [{\"components\": [{\"data\": \"194\"}, {\"data\": \"20200301\"}, {\"data\": \"102\"}]}]}, {\"tag\": \"DTM\", \"dataElements\": [{\"components\": [{\"data\": \"206\"}, {\"data\": \"20210228\"}, {\"data\": \"102\"}]}]}, {\"tag\": \"MOA\", \"dataElements\": [{\"components\": [{\"data\": \"203\"}, {\"data\": \"726.5\"}, {\"data\": \"USD\"}, {\"data\": \"4\"}]}]}, {\"tag\": \"PRI\", \"dataElements\": [{\"components\": [{\"data\": \"AAB\"}, {\"data\": \"714\"}]}]}, {\"tag\": \"RFF\", \"dataElements\": [{\"components\": [{\"data\": \"LI\"}, {\"data\": \"S283901\"}]}]}, {\"tag\": \"RFF\", \"dataElements\": [{\"components\": [{\"data\": \"SNA\"}, {\"data\": \"E9498296\"}]}]}, {\"tag\": \"ALC\", \"dataElements\": [{\"components\": [{\"data\": \"C\"}]}, {\"components\": [{\"data\": \"\"}]}, {\"components\": [{\"data\": \"\"}]}, {\"components\": [{\"data\": \"\"}]}, {\"components\": [{\"data\": \"G74\"}, {\"data\": \"\"}, {\"data\": \"28\"}, {\"data\": \"LINE SERVICE CHARGE\"}]}]}, {\"tag\": \"MOA\", \"dataElements\": [{\"components\": [{\"data\": \"8\"}, {\"data\": \"12.5\"}]}]}, {\"tag\": \"UNS\", \"dataElements\": [{\"components\": [{\"data\": \"S\"}]}]}, {\"tag\": \"CNT\", \"dataElements\": [{\"components\": [{\"data\": \"1\"}, {\"data\": \"3\"}]}]}, {\"tag\": \"CNT\", \"dataElements\": [{\"components\": [{\"data\": \"2\"}, {\"data\": \"3\"}]}]}, {\"tag\": \"MOA\", \"dataElements\": [{\"components\": [{\"data\": \"79\"}, {\"data\": \"18929.07\"}]}]}, {\"tag\": \"MOA\", \"dataElements\": [{\"components\": [{\"data\": \"9\"}, {\"data\": \"18929.07\"}]}]}, {\"tag\": \"ALC\", \"dataElements\": [{\"components\": [{\"data\": \"C\"}]}, {\"components\": [{\"data\": \"\"}]}, {\"components\": [{\"data\": \"\"}]}, {\"components\": [{\"data\": \"\"}]}, {\"components\": [{\"data\": \"G74\"}, {\"data\": \"\"}, {\"data\": \"28\"}, {\"data\": \"TOTAL SERVICE CHARGE\"}]}]}, {\"tag\": \"MOA\", \"dataElements\": [{\"components\": [{\"data\": \"8\"}, {\"data\": \"325.59\"}]}]}, {\"tag\": \"UNT\", \"dataElements\": [{\"components\": [{\"data\": \"294\"}]}, {\"components\": [{\"data\": \"5162-1\"}]}]}, {\"tag\": \"UNZ\", \"dataElements\": [{\"components\": [{\"data\": \"1\"}]}, {\"components\": [{\"data\": \"5162\"}]}]}]}";
  private static final String OKAPI_URL = "http://localhost:" + ApiTestSuite.mockPort;
  private static final String TENANT_ID = "diku";
  private static final String TOKEN = "test-token";

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
        new MappingRule().withPath("invoice.invoiceLines[]").withEnabled("true")
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

  private ProfileSnapshotWrapper profileSnapshotWrapper = new ProfileSnapshotWrapper()
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

  private EventHandler createInvoiceHandler = new CreateInvoiceEventHandler();

  @Test
  public void shouldCreateInvoiceAndPublishDiCompletedEvent() throws IOException, InterruptedException {
    // given
    Record record = new Record().withParsedRecord(new ParsedRecord().withContent(EDIFACT_PARSED_CONTENT));
    HashMap<String, String> payloadContext = new HashMap<>();
    payloadContext.put(EDIFACT_INVOICE.value(), Json.encode(record));

    DataImportEventPayload dataImportEventPayload = new DataImportEventPayload()
      .withEventType(EDIFACT_RECORD_CREATED_EVENT)
      .withTenant(DI_POST_INVOICE_LINES_SUCCESS_TENANT)
      .withOkapiUrl(OKAPI_URL)
      .withToken(TOKEN)
      .withContext(payloadContext)
      .withProfileSnapshot(profileSnapshotWrapper);

    String topic = KafkaTopicNameHelper.formatTopicName(KAFKA_ENV_VALUE, getDefaultNameSpace(), DI_POST_INVOICE_LINES_SUCCESS_TENANT, dataImportEventPayload.getEventType());
    Event event = new Event().withEventPayload(ZIPArchiver.zip(Json.encode(dataImportEventPayload)));
    KeyValue<String, String> kafkaRecord = new KeyValue<>("test-key", Json.encode(event));
    SendKeyValues<String, String> request = SendKeyValues.to(topic, Collections.singletonList(kafkaRecord))
      .useDefaults();

    // when
    kafkaCluster.send(request);

    // then
    String topicToObserve = KafkaTopicNameHelper.formatTopicName(KAFKA_ENV_VALUE, getDefaultNameSpace(), DI_POST_INVOICE_LINES_SUCCESS_TENANT, DI_COMPLETED.value());
    List<String> observedValues  = kafkaCluster.observeValues(ObserveKeyValues.on(topicToObserve, 1)
      .observeFor(30, TimeUnit.SECONDS)
      .build());

    Event obtainedEvent = Json.decodeValue(observedValues.get(0), Event.class);
    DataImportEventPayload eventPayload = Json.decodeValue(ZIPArchiver.unzip(obtainedEvent.getEventPayload()), DataImportEventPayload.class);
    assertEquals(DI_INVOICE_CREATED_EVENT, eventPayload.getEventsChain().get(eventPayload.getEventsChain().size() -1));

    assertNotNull(eventPayload.getContext().get(INVOICE.value()));
    Invoice createdInvoice = Json.decodeValue(eventPayload.getContext().get(INVOICE.value()), Invoice.class);
    assertNotNull(createdInvoice.getVendorInvoiceNo());
    assertNotNull(createdInvoice.getCurrency());
    assertEquals("Open", createdInvoice.getStatus().value());

    assertNotNull(eventPayload.getContext().get(INVOICE_LINES_KEY));
    InvoiceLineCollection createdInvoiceLines = Json.decodeValue(eventPayload.getContext().get(INVOICE_LINES_KEY), InvoiceLineCollection.class);
    assertEquals(3, createdInvoiceLines.getTotalRecords());
    assertEquals(3, createdInvoiceLines.getInvoiceLines().size());
    createdInvoiceLines.getInvoiceLines().forEach(invLine -> assertEquals(createdInvoice.getId(), invLine.getInvoiceId()));
  }

  @Test
  public void shouldPublishDiErrorEventWhenHasNoSourceRecord() throws IOException, InterruptedException {
    // given
    DataImportEventPayload dataImportEventPayload = new DataImportEventPayload()
      .withEventType(EDIFACT_RECORD_CREATED_EVENT)
      .withTenant(TENANT_ID)
      .withOkapiUrl(OKAPI_URL)
      .withToken(TOKEN)
      .withContext(new HashMap<>())
      .withProfileSnapshot(profileSnapshotWrapper);

    String topic = KafkaTopicNameHelper.formatTopicName(KAFKA_ENV_VALUE, getDefaultNameSpace(), TENANT_ID, dataImportEventPayload.getEventType());
    Event event = new Event().withEventPayload(ZIPArchiver.zip(Json.encode(dataImportEventPayload)));
    KeyValue<String, String> kafkaRecord = new KeyValue<>("test-key", Json.encode(event));
    SendKeyValues<String, String> request = SendKeyValues.to(topic, Collections.singletonList(kafkaRecord))
      .useDefaults();

    // when
    kafkaCluster.send(request);

    // then
    String topicToObserve = KafkaTopicNameHelper.formatTopicName(KAFKA_ENV_VALUE, getDefaultNameSpace(), TENANT_ID, DI_ERROR.value());
    kafkaCluster.observeValues(ObserveKeyValues.on(topicToObserve, 1)
      .observeFor(30, TimeUnit.SECONDS)
      .build());
  }

  @Test
  public void shouldReturnTrueWhenHandlerIsEligibleForActionProfile() {
    // given
    DataImportEventPayload dataImportEventPayload = new DataImportEventPayload()
      .withEventType(EDIFACT_RECORD_CREATED_EVENT)
      .withProfileSnapshot(profileSnapshotWrapper)
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
      .withEventType(EDIFACT_RECORD_CREATED_EVENT)
      .withProfileSnapshot(profileSnapshotWrapper)
      .withCurrentNode(profileSnapshotWrapper);

    // when
    boolean isEligible = createInvoiceHandler.isEligible(dataImportEventPayload);

    // then
    Assertions.assertFalse(isEligible);
  }

}
