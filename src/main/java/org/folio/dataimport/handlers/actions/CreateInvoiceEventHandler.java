package org.folio.dataimport.handlers.actions;

import static io.vertx.core.Future.succeededFuture;
import static java.lang.String.format;
import static one.util.streamex.StreamEx.ofSubLists;
import static org.apache.commons.collections4.CollectionUtils.isEmpty;
import static org.apache.commons.collections4.CollectionUtils.isNotEmpty;
import static org.apache.commons.lang.StringUtils.isNotEmpty;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isEmpty;
import static org.folio.ActionProfile.Action.CREATE;
import static org.folio.ActionProfile.FolioRecord.INVOICE;
import static org.folio.DataImportEventTypes.DI_INVOICE_CREATED;
import static org.folio.invoices.utils.HelperUtils.collectResultsOnSuccess;
import static org.folio.invoices.utils.ResourcePathResolver.ORDER_LINES;
import static org.folio.invoices.utils.ResourcePathResolver.resourcesPath;
import static org.folio.rest.jaxrs.model.EntityType.EDIFACT_INVOICE;
import static org.folio.rest.jaxrs.model.InvoiceLine.InvoiceLineStatus.OPEN;
import static org.folio.rest.jaxrs.model.ProfileType.ACTION_PROFILE;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertxconcurrent.Semaphore;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.ActionProfile;
import org.folio.DataImportEventPayload;
import org.folio.EdifactParsedContent;
import org.folio.MappingProfile;
import org.folio.ParsedRecord;
import org.folio.Record;
import org.folio.dataimport.utils.DataImportUtils;
import org.folio.dbschema.ObjectMapperTool;
import org.folio.domain.relationship.RecordToEntity;
import org.folio.invoices.rest.exceptions.HttpException;
import org.folio.invoices.utils.HelperUtils;
import org.folio.kafka.exception.DuplicateEventException;
import org.folio.processing.events.services.handler.EventHandler;
import org.folio.processing.exceptions.EventProcessingException;
import org.folio.processing.mapping.MappingManager;
import org.folio.processing.mapping.mapper.MappingContext;
import org.folio.processing.mapping.mapper.reader.record.edifact.EdifactRecordReader;
import org.folio.rest.acq.model.orders.FundDistribution;
import org.folio.rest.acq.model.orders.PoLine;
import org.folio.rest.acq.model.orders.PoLineCollection;
import org.folio.rest.core.RestClient;
import org.folio.rest.core.models.RequestContext;
import org.folio.rest.core.models.RequestEntry;
import org.folio.rest.impl.InvoiceHelper;
import org.folio.rest.impl.InvoiceLineHelper;
import org.folio.rest.jaxrs.model.Invoice;
import org.folio.rest.jaxrs.model.InvoiceLine;
import org.folio.rest.jaxrs.model.InvoiceLineCollection;
import org.folio.rest.jaxrs.model.ProfileSnapshotWrapper;
import org.folio.services.invoice.IdStorageService;

public class CreateInvoiceEventHandler implements EventHandler {

  private static final Logger logger = LogManager.getLogger(CreateInvoiceEventHandler.class);
  private static final String PAYLOAD_HAS_NO_DATA_MSG = "Failed to handle event payload, cause event payload context does not contain EDIFACT data";

  public static final String INVOICE_LINES_KEY = "INVOICE_LINES";
  public static final String INVOICE_LINES_ERRORS_KEY = "INVOICE_LINES_ERRORS";
  private static final String INVOICE_FIELD = "invoice";
  private static final String INVOICE_LINES_FIELD = "invoiceLines";
  private static final String PO_LINES_BY_POL_NUMBER_CQL = "purchaseOrder.workflowStatus==Open AND poLineNumber==(%s)";
  private static final String PO_LINES_BY_REF_NUMBER_CQL = "purchaseOrder.workflowStatus==Open AND vendorDetail.referenceNumbers=(%s)";
  private static final String REF_NUMBER_CRITERIA_PATTERN = "\"\\\"refNumber\\\":\\\"%s\\\"\"";
  private static final String POL_TITLE_KEY = "POL_TITLE_%s";
  private static final String PO_LINE_NUMBER_RULE_NAME = "poLineId";
  private static final String INVOICE_LINES_RULE_NAME = "invoiceLines";
  private static final String REFERENCE_NUMBERS_RULE_NAME = "referenceNumbers";
  private static final String REF_NUMBER_RULE_NAME = "refNumber";
  private static final String FUND_DISTRIBUTIONS_RULE_NAME = "fundDistributions";
  private static final String FUND_ID_RULE_NAME = "fundId";
  private static final String POL_NUMBER_KEY = "POL_NUMBER_%s";
  private static final String POL_EXPENSE_CLASS_KEY = "POL_EXPENSE_CLASS_%s";
  private static final String POL_FUND_DISTRIBUTIONS_KEY = "POL_FUND_DISTRIBUTIONS_%s";
  private static final Pattern SEGMENT_QUERY_PATTERN = Pattern.compile("([A-Z]{3}((\\+|<)\\w*)(\\2*\\w*)*(\\?\\w+)?\\[[1-9](-[1-9])?\\])");
  private static final int MAX_CHUNK_SIZE = 15;
  private final int maxActiveThreads;
  private static final String RECORD_ID = "recordId";
  private final RestClient restClient;
  private final IdStorageService idStorageService;
  public static final String UNIQUE_KEY_CONSTRAINT_ERROR = "duplicate key value violates unique constraint";
  private static final char LEFT_BRACKET = '(';
  private static final char RIGHT_BRACKET = ')';

  public CreateInvoiceEventHandler(RestClient restClient, IdStorageService idStorageService) {
    this.restClient = restClient;
    this.idStorageService = idStorageService;
    this.maxActiveThreads = Integer.parseInt(System.getProperty("dataimport.max-active-threads", "1"));
  }

  @Override
  public CompletableFuture<DataImportEventPayload> handle(DataImportEventPayload dataImportEventPayload) {
    CompletableFuture<DataImportEventPayload> future = new CompletableFuture<>();
    dataImportEventPayload.setEventType(DI_INVOICE_CREATED.value());
    try {
      HashMap<String, String> payloadContext = dataImportEventPayload.getContext();
      if (payloadContext == null || isBlank(payloadContext.get(EDIFACT_INVOICE.value()))) {
        logger.error(PAYLOAD_HAS_NO_DATA_MSG);
        return CompletableFuture.failedFuture(new EventProcessingException(PAYLOAD_HAS_NO_DATA_MSG));
      }

      Map<String, String> okapiHeaders = DataImportUtils.getOkapiHeaders(dataImportEventPayload);
      Future<Map<Integer, PoLine>> poLinesFuture = getAssociatedPoLines(dataImportEventPayload, okapiHeaders);

      var invoicesFuture = poLinesFuture
        .map(invLineNoToPoLine -> {
          ensureAdditionalData(dataImportEventPayload, invLineNoToPoLine);
          prepareEventPayloadForMapping(dataImportEventPayload);
          MappingManager.map(dataImportEventPayload, new MappingContext());
          prepareMappingResult(dataImportEventPayload);
          return null;
        });
      var recordId = payloadContext.get(RECORD_ID);
      Future<RecordToEntity> recordToInvoiceFuture = idStorageService.store(recordId, UUID.randomUUID().toString(),
        dataImportEventPayload.getTenant());

      recordToInvoiceFuture.onSuccess(res -> {
        String invoiceId = res.getEntityId();
        invoicesFuture
          .compose(v -> saveInvoice(dataImportEventPayload, okapiHeaders, invoiceId))
          .map(savedInvoice -> prepareInvoiceLinesToSave(savedInvoice.getId(), dataImportEventPayload, poLinesFuture.result()))
          .compose(preparedInvoiceLines -> saveInvoiceLines(preparedInvoiceLines, okapiHeaders))
          .onComplete(result -> {
            makeLightweightReturnPayload(dataImportEventPayload);

            if (result.succeeded()) {
              List<InvoiceLine> invoiceLines = result.result().stream().map(Pair::getLeft).collect(Collectors.toList());
              InvoiceLineCollection invoiceLineCollection = new InvoiceLineCollection().withInvoiceLines(invoiceLines).withTotalRecords(invoiceLines.size());
              dataImportEventPayload.getContext().put(INVOICE_LINES_KEY, Json.encode(invoiceLineCollection));
              Map<Integer, String> invoiceLinesErrors = prepareInvoiceLinesErrors(result.result());
              if (!invoiceLinesErrors.isEmpty()) {
                dataImportEventPayload.getContext().put(INVOICE_LINES_ERRORS_KEY, Json.encode(invoiceLinesErrors));
                future.completeExceptionally(new EventProcessingException("Error during invoice lines creation"));
                return;
              }
              future.complete(dataImportEventPayload);
            } else {
              preparePayloadWithMappedInvoiceLines(dataImportEventPayload);
              if (!(result.cause() instanceof DuplicateEventException)) {
                logger.error("Error during creation invoice in the storage", result.cause());
              }
              future.completeExceptionally(result.cause());
            }
          });
      }).onFailure(failure -> {
        logger.error("Error creating invoice recordId and instanceId relationship by jobExecutionId: '{}' and " +
            "recordId: '{}' ", dataImportEventPayload.getJobExecutionId(), recordId, failure);
        future.completeExceptionally(failure);
      });
    } catch (Exception e) {
      logger.error("Error during creation invoice and invoice lines", e);
      future.completeExceptionally(e);
    }
    return future;
  }

  private Map<Integer, String> prepareInvoiceLinesErrors(List<Pair<InvoiceLine, String>> invoiceLinesSavingResult) {
    Map<Integer, String> invoiceLinesErrors = new HashMap<>();
    for (int i = 0; i < invoiceLinesSavingResult.size(); i++) {
      Pair<InvoiceLine, String> invLineResult = invoiceLinesSavingResult.get(i);
      if (invLineResult.getRight() != null) {
        invoiceLinesErrors.put(i + 1, invLineResult.getRight());
      }
    }
    return invoiceLinesErrors;
  }

  private Future<Map<Integer, PoLine>> getAssociatedPoLines(DataImportEventPayload eventPayload, Map<String, String> okapiHeaders) {
    String recordAsString = eventPayload.getContext().get(EDIFACT_INVOICE.value());
    Record sourceRecord = Json.decodeValue(recordAsString, Record.class);
    ParsedRecord parsedRecord = sourceRecord.getParsedRecord();
    long invoiceLinesAmount = determineInvoiceLinesQuantity(parsedRecord);

    Optional<String> poLineNoExpressionOptional = getPoLineNoMappingExpression(eventPayload);
    Map<Integer, String> invoiceLineNoToPoLineNo = poLineNoExpressionOptional
      .map(expression -> EdifactRecordReader.getInvoiceLinesSegmentsValues(parsedRecord, expression))
      .orElse(Collections.emptyMap());

    List<String> referenceNumberExpressions = getPoLineRefNumberMappingExpressions(eventPayload);
    Map<Integer, List<String>> invoiceLineNoToRefNo2 = referenceNumberExpressions.isEmpty()
      ? Collections.emptyMap() : retrieveInvoiceLinesReferenceNumbers(parsedRecord, referenceNumberExpressions);

    return getAssociatedPoLinesByPoLineNumber(invoiceLineNoToPoLineNo, okapiHeaders)
      .compose(associatedPoLineMap -> {
        if (associatedPoLineMap.size() < invoiceLinesAmount) {
          associatedPoLineMap.keySet().forEach(invoiceLineNoToRefNo2::remove);
          return getAssociatedPoLinesByRefNumbers(invoiceLineNoToRefNo2, new RequestContext(Vertx.currentContext(), okapiHeaders)).map(poLinesMap -> {
              associatedPoLineMap.putAll(poLinesMap);
              return associatedPoLineMap;
          });
        }
        return succeededFuture(associatedPoLineMap);
      });
  }

  private long determineInvoiceLinesQuantity(ParsedRecord parsedRecord) {
    EdifactParsedContent parsedContent = Json.decodeValue(parsedRecord.getContent().toString(), EdifactParsedContent.class);
    return parsedContent.getSegments().stream()
      .filter(segment -> segment.getTag().equals("LIN"))
      .count();
  }

  private Optional<String> getPoLineNoMappingExpression(DataImportEventPayload dataImportEventPayload) {
    MappingProfile mappingProfile = JsonObject.mapFrom(dataImportEventPayload.getCurrentNode().getChildSnapshotWrappers().get(0).getContent()).mapTo(MappingProfile.class);
    return mappingProfile.getMappingDetails().getMappingFields().stream()
      .filter(mappingRule -> INVOICE_LINES_RULE_NAME.equals(mappingRule.getName()) && !mappingRule.getSubfields().isEmpty())
      .flatMap(mappingRule -> mappingRule.getSubfields().get(0).getFields().stream())
      .filter(mappingRule -> PO_LINE_NUMBER_RULE_NAME.equals(mappingRule.getName()))
      .map(mappingRule -> SEGMENT_QUERY_PATTERN.matcher(mappingRule.getValue()))
      .filter(mappingExpressionMatcher -> mappingExpressionMatcher.find())
      .map(matcher -> matcher.group(1))
      .findFirst();
  }

  private List<String> getPoLineRefNumberMappingExpressions(DataImportEventPayload dataImportEventPayload) {
    MappingProfile mappingProfile = JsonObject.mapFrom(dataImportEventPayload.getCurrentNode().getChildSnapshotWrappers().get(0).getContent()).mapTo(MappingProfile.class);
    return mappingProfile.getMappingDetails().getMappingFields().stream()
      .filter(mappingRule -> INVOICE_LINES_RULE_NAME.equals(mappingRule.getName()) && !mappingRule.getSubfields().isEmpty())
      .flatMap(mappingRule -> mappingRule.getSubfields().get(0).getFields().stream())
      .filter(mappingRule -> REFERENCE_NUMBERS_RULE_NAME.equals(mappingRule.getName()) && !mappingRule.getSubfields().isEmpty())
      .flatMap(mappingRule -> mappingRule.getSubfields().get(0).getFields().stream())
      .filter(mappingRule -> REF_NUMBER_RULE_NAME.equals(mappingRule.getName()))
      .map(mappingRule -> SEGMENT_QUERY_PATTERN.matcher(mappingRule.getValue()))
      .filter(mappingExpressionMatcher -> mappingExpressionMatcher.find())
      .map(matcher -> matcher.group(1))
      .collect(Collectors.toList());
  }

  private Map<Integer, List<String>> retrieveInvoiceLinesReferenceNumbers(ParsedRecord parsedRecord, List<String> referenceNumberExpressions) {
    Map<Integer, List<String>> invoiceLinesToRefNumbers = new HashMap<>();

    for (String expression : referenceNumberExpressions) {
      Map<Integer, String> segmentsValues = EdifactRecordReader.getInvoiceLinesSegmentsValues(parsedRecord, expression);

      segmentsValues.forEach((invLineNumber, segmentData) -> {
        if (invoiceLinesToRefNumbers.get(invLineNumber) == null) {
          List<String> referenceNumberList = new ArrayList<>();
          referenceNumberList.add(segmentData);
          invoiceLinesToRefNumbers.put(invLineNumber, referenceNumberList);
        } else {
          invoiceLinesToRefNumbers.get(invLineNumber).add(segmentData);
        }
      });
    }
    return invoiceLinesToRefNumbers;
  }

  private Future<Map<Integer, PoLine>> getAssociatedPoLinesByPoLineNumber(Map<Integer, String> invoiceLineNoToPoLineNo, Map<String, String> okapiHeaders) {
    Map<Integer, PoLine> invoiceLineNoToPoLine = new HashMap<>();
    if (invoiceLineNoToPoLineNo.isEmpty()) {
      return succeededFuture(invoiceLineNoToPoLine);
    }

    List<Future<PoLineCollection>> polineCollectionsFuture = ofSubLists(List.copyOf(invoiceLineNoToPoLineNo.values()), MAX_CHUNK_SIZE)
      .map(this::prepareQueryGetPoLinesByNumber)
      .map(cqlQuery -> new RequestEntry(resourcesPath(ORDER_LINES)).withQuery(cqlQuery).withOffset(0).withLimit(Integer.MAX_VALUE))
      .map(requestEntry -> restClient.get(requestEntry, PoLineCollection.class, new RequestContext(Vertx.currentContext(), okapiHeaders)))
      .collect(Collectors.toList());

    return collectResultsOnSuccess(polineCollectionsFuture)
      .map(lists -> lists.stream().flatMap(polCollection -> polCollection.getPoLines().stream()).distinct().collect(Collectors.toList()))
      .map(poLines -> poLines.stream().collect(Collectors.toMap(PoLine::getPoLineNumber, poLine -> poLine)))
      .map(poLineNumberToPoLine -> {
        invoiceLineNoToPoLineNo.forEach((key, value) ->
          poLineNumberToPoLine.computeIfPresent(value, (polNo, poLine) -> invoiceLineNoToPoLine.put(key, poLine)));
        return null;
      })
      .map(v -> invoiceLineNoToPoLine);
  }


  private Future<Map<Integer, PoLine>> getAssociatedPoLinesByRefNumbers(Map<Integer, List<String>> refNumberList, RequestContext requestContext) {
    if (MapUtils.isEmpty(refNumberList)) {
      return Future.succeededFuture(new HashMap<>());
    }
    return requestContext.getContext()
      .<List<Future<Pair<Integer, PoLine>>>>executeBlocking(promise -> {
        List<Future<Pair<Integer, PoLine>>> futures = new ArrayList<>();
        Semaphore semaphore = new Semaphore(maxActiveThreads, Vertx.currentContext().owner());
        for (Map.Entry<Integer, List<String>> entry : refNumberList.entrySet()) {
          semaphore.acquire(() -> {
            var future = getLinePair(entry, requestContext)
              .onComplete(asyncResult -> semaphore.release());
            futures.add(future);
            // complete executeBlocking promise when all operations started
            if (futures.size() == refNumberList.size()) {
              promise.complete(futures);
            }
          });
        }
      })
      .compose(HelperUtils::collectResultsOnSuccess)
      .map(poLinePairs -> poLinePairs.stream()
        .filter(Objects::nonNull)
        .collect(Collectors.toMap(Pair::getKey, Pair::getValue)));
  }

  private Future<Pair<Integer, PoLine>> getLinePair(Map.Entry<Integer, List<String>> refNumbers, RequestContext requestContext) {
    String cqlGetPoLinesByRefNo = prepareQueryGetPoLinesByRefNumber(refNumbers.getValue());

    RequestEntry requestEntry = new RequestEntry(resourcesPath(ORDER_LINES)).withQuery(cqlGetPoLinesByRefNo)
      .withQuery(cqlGetPoLinesByRefNo)
      .withOffset(0)
      .withLimit(Integer.MAX_VALUE);

    return restClient.get(requestEntry, PoLineCollection.class, requestContext)
      .map(polines -> {
        if (polines.getPoLines().size() == 1) {
          return Pair.of(refNumbers.getKey(), polines.getPoLines().get(0));
        }
        return null;
      })
      .otherwise(t -> null);
  }

  private String prepareQueryGetPoLinesByNumber(List<String> poLineNumbers) {
    String valueString = poLineNumbers.stream()
      .map(number -> format("\"%s\"", number))
      .collect(Collectors.joining(" OR "));

    return format(PO_LINES_BY_POL_NUMBER_CQL, valueString);
  }

  private String prepareQueryGetPoLinesByRefNumber(List<String> referenceNumbers) {
    String valueString = referenceNumbers.stream()
      .map(refNumber -> format(REF_NUMBER_CRITERIA_PATTERN, refNumber))
      .collect(Collectors.joining(" OR "));

    return format(PO_LINES_BY_REF_NUMBER_CQL, valueString);
  }

  private Future<Invoice> saveInvoice(DataImportEventPayload dataImportEventPayload,
                                      Map<String, String> okapiHeaders, String invoiceId) {
    Invoice invoiceToSave = Json.decodeValue(dataImportEventPayload.getContext().get(INVOICE.value()), Invoice.class);
    invoiceToSave.setSource(Invoice.Source.EDI);
    invoiceToSave.setId(invoiceId);
    InvoiceHelper invoiceHelper = new InvoiceHelper(okapiHeaders, Vertx.currentContext());
    RequestContext requestContext = new RequestContext(Vertx.currentContext(), okapiHeaders);
    return invoiceHelper.createInvoice(invoiceToSave, requestContext)
      .compose(result -> {
        dataImportEventPayload.getContext().put(INVOICE.value(), Json.encode(result));
        return Future.succeededFuture(result);
      })
      .recover(throwable -> {
        if (throwable instanceof HttpException httpException && httpException.getMessage().contains(UNIQUE_KEY_CONSTRAINT_ERROR)) {
          String message = String.format("Duplicated event by Invoice id: %s", invoiceToSave.getId());
          logger.info("Duplicated event received by InvoiceId: {}. Ignoring...", invoiceToSave.getId());
          return Future.failedFuture(new DuplicateEventException(message));
        }
        return Future.failedFuture(throwable);
      });
  }

  private List<InvoiceLine> prepareInvoiceLinesToSave(String invoiceId, DataImportEventPayload dataImportEventPayload, Map<Integer, PoLine> associatedPoLines) {
    List<InvoiceLine> invoiceLines = new JsonArray(dataImportEventPayload.getContext().get(INVOICE_LINES_KEY))
      .stream()
      .map(JsonObject.class::cast)
      .map(json -> json.mapTo(InvoiceLine.class))
      .map(invoiceLine -> invoiceLine.withInvoiceId(invoiceId).withInvoiceLineStatus(OPEN))
      .collect(Collectors.toList());

    linkInvoiceLinesToPoLines(invoiceLines, associatedPoLines);
    ensureFundCode(invoiceLines, dataImportEventPayload);
    return invoiceLines;
  }

  private void ensureFundCode(List<InvoiceLine> invoiceLines, DataImportEventPayload dataImportEventPayload) {
    if (invoiceLines.stream().allMatch(line -> isEmpty(line.getFundDistributions()))) {
      return;
    }

    Map<String, String> idToFundName = extractFundsData(dataImportEventPayload);
    if (!idToFundName.isEmpty()) {
      invoiceLines.stream()
        .filter(invoiceLine -> isNotEmpty(invoiceLine.getFundDistributions()))
        .forEach(invoiceLine -> invoiceLine.getFundDistributions().stream()
          .filter(fundDistribution -> isNotEmpty(fundDistribution.getFundId()) && isEmpty(fundDistribution.getCode()))
          .forEach(fundDistribution -> populateFundCode(fundDistribution, idToFundName)));
    }
  }

  private Map<String, String> extractFundsData(DataImportEventPayload dataImportEventPayload) {
    ProfileSnapshotWrapper mappingProfileWrapper = dataImportEventPayload.getCurrentNode();
    MappingProfile mappingProfile = ObjectMapperTool.getMapper().convertValue(mappingProfileWrapper.getContent(), MappingProfile.class);

    return mappingProfile.getMappingDetails().getMappingFields().stream()
      .filter(mappingRule -> INVOICE_LINES_RULE_NAME.equals(mappingRule.getName()) && !mappingRule.getSubfields().isEmpty())
      .flatMap(mappingRule -> mappingRule.getSubfields().get(0).getFields().stream())
      .filter(mappingRule -> FUND_DISTRIBUTIONS_RULE_NAME.equals(mappingRule.getName()) && !mappingRule.getSubfields().isEmpty())
      .flatMap(mappingRule -> mappingRule.getSubfields().get(0).getFields().stream())
      .filter(mappingRule -> FUND_ID_RULE_NAME.equals(mappingRule.getName()))
      .map(mappingRule -> ((Map<String, String>) mappingRule.getAcceptedValues()))
      .findAny()
      .orElse(Collections.emptyMap());
  }

  private void populateFundCode(org.folio.rest.jaxrs.model.FundDistribution fundDistribution, Map<String, String> idToFundName) {
    String fundName = idToFundName.get(fundDistribution.getFundId());
    fundDistribution.setCode(fundName.substring(fundName.lastIndexOf(LEFT_BRACKET) + 1, fundName.lastIndexOf(RIGHT_BRACKET)));
  }

  private void linkInvoiceLinesToPoLines(List<InvoiceLine> invoiceLines, Map<Integer, PoLine> associatedPoLines) {
    for (int i = 0; i < invoiceLines.size(); i++) {
      if (associatedPoLines.get(i + 1) != null) {
        invoiceLines.get(i).setPoLineId(associatedPoLines.get(i + 1).getId());
      } else {
        invoiceLines.get(i).setPoLineId(null);
      }
    }
  }

  private Future<List<Pair<InvoiceLine, String>>> saveInvoiceLines(List<InvoiceLine> invoiceLines, Map<String, String> okapiHeaders) {
    List<Future<Pair<InvoiceLine, String>>> futures = new ArrayList<>();

    if (CollectionUtils.isEmpty(invoiceLines)) {
      return Future.succeededFuture(new ArrayList<>());
    }

    InvoiceLineHelper helper = new InvoiceLineHelper(okapiHeaders, Vertx.currentContext());
    return Vertx.currentContext()
      .<List<Future<Pair<InvoiceLine, String>>>>executeBlocking(promise -> {
        Semaphore semaphore = new Semaphore(maxActiveThreads, true, Vertx.currentContext().owner());
        for (InvoiceLine invoiceLine : invoiceLines) {
          semaphore.acquire(() -> {
            var future = helper.createInvoiceLine(invoiceLine)
              .compose(createdInvoiceLine -> {
                Pair<InvoiceLine, String> invoiceLineToMsg = Pair.of(createdInvoiceLine, null);
                return Future.succeededFuture(invoiceLineToMsg);
              }, err -> {
                logger.error("Failed to create invoice line {}, {}", invoiceLine, err);
                Pair<InvoiceLine, String> invoiceLineToMsg = Pair.of(invoiceLine, err.getMessage());
                return Future.succeededFuture(invoiceLineToMsg);
              })
              .onComplete(asyncResult -> semaphore.release());
            futures.add(future);
            // complete executeBlocking promise when all operations processed
            if (futures.size() == invoiceLines.size()) {
              promise.complete(futures);
            }
          });
        }
      })
      .compose(HelperUtils::collectResultsOnSuccess);
  }

  private List<InvoiceLine> mapInvoiceLinesArrayToList(JsonArray invoiceLinesArray) {
    return invoiceLinesArray.stream()
      .map(JsonObject.class::cast)
      .map(json -> json.mapTo(InvoiceLine.class))
      .collect(Collectors.toList());
  }

  private void prepareEventPayloadForMapping(DataImportEventPayload dataImportEventPayload) {
    dataImportEventPayload.getEventsChain().add(dataImportEventPayload.getEventType());
    dataImportEventPayload.setCurrentNode(dataImportEventPayload.getCurrentNode().getChildSnapshotWrappers().get(0));
    dataImportEventPayload.getContext().put(INVOICE.value(), new JsonObject().encode());
  }

  private void prepareMappingResult(DataImportEventPayload dataImportEventPayload) {
    JsonObject mappingResult = new JsonObject(dataImportEventPayload.getContext().get(INVOICE.value()));
    JsonObject invoiceJson = mappingResult.getJsonObject(INVOICE_FIELD);
    dataImportEventPayload.getContext().put(INVOICE_LINES_KEY, invoiceJson.remove(INVOICE_LINES_FIELD).toString());
    dataImportEventPayload.getContext().put(INVOICE.value(), invoiceJson.encode());
  }

  private void ensureAdditionalData(DataImportEventPayload dataImportEventPayload, Map<Integer, PoLine> invoiceLineNoToPoLine) {
    for (Map.Entry<Integer, PoLine> pair : invoiceLineNoToPoLine.entrySet()) {
      PoLine poLine = pair.getValue();
      // put poLine id because invoice line schema expects "poLineId" instead of poLine number
      // https://github.com/folio-org/acq-models/blob/master/mod-invoice-storage/schemas/invoice_line.json
      dataImportEventPayload.getContext().put(format(POL_NUMBER_KEY, pair.getKey() - 1), poLine.getId());
      dataImportEventPayload.getContext().put(format(POL_TITLE_KEY, pair.getKey() - 1), poLine.getTitleOrPackage());

      if (poLine.getFundDistribution() != null) {
        dataImportEventPayload.getContext().put(format(POL_FUND_DISTRIBUTIONS_KEY, pair.getKey() - 1), Json.encode(poLine.getFundDistribution()));
      }
      if (isNotEmpty(poLine.getFundDistribution()) && verifyAllFundsHaveSameExpenseClass(poLine.getFundDistribution())) {
        dataImportEventPayload.getContext().put(format(POL_EXPENSE_CLASS_KEY, pair.getKey() - 1), poLine.getFundDistribution().get(0).getExpenseClassId());
      }
    }
  }

  private boolean verifyAllFundsHaveSameExpenseClass(List<FundDistribution> fundDistributionList) {
    return fundDistributionList.stream()
      .allMatch(fundDistribution -> Objects.equals(fundDistributionList.get(0).getExpenseClassId(), fundDistribution.getExpenseClassId()));
  }

  private void preparePayloadWithMappedInvoiceLines(DataImportEventPayload dataImportEventPayload) {
    if (dataImportEventPayload.getContext().get(INVOICE_LINES_KEY) != null) {
      List<InvoiceLine> invoiceLines = mapInvoiceLinesArrayToList(new JsonArray(dataImportEventPayload.getContext().get(INVOICE_LINES_KEY)));
      InvoiceLineCollection invoiceLineCollection = new InvoiceLineCollection().withInvoiceLines(invoiceLines).withTotalRecords(invoiceLines.size());
      dataImportEventPayload.getContext().put(INVOICE_LINES_KEY, Json.encode(invoiceLineCollection));
    }
  }

  private void makeLightweightReturnPayload(DataImportEventPayload eventPayload) {
    Record sourceRecord = Json.decodeValue(eventPayload.getContext().get(EDIFACT_INVOICE.value()), Record.class);
    sourceRecord.setParsedRecord(null);
    sourceRecord.setRawRecord(null);
    eventPayload.getContext().put(EDIFACT_INVOICE.value(), Json.encode(sourceRecord));
  }

  @Override
  public boolean isEligible(DataImportEventPayload dataImportEventPayload) {
    if (dataImportEventPayload.getCurrentNode() != null && ACTION_PROFILE == dataImportEventPayload.getCurrentNode().getContentType()) {
      ActionProfile actionProfile = JsonObject.mapFrom(dataImportEventPayload.getCurrentNode().getContent()).mapTo(ActionProfile.class);
      return actionProfile.getAction() == CREATE && actionProfile.getFolioRecord() == INVOICE;
    }
    return false;
  }
}
