package org.folio.dataimport.handlers.actions;

import static java.lang.String.format;
import static org.apache.commons.collections4.CollectionUtils.isNotEmpty;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.folio.ActionProfile.Action.CREATE;
import static org.folio.ActionProfile.FolioRecord.INVOICE;
import static org.folio.DataImportEventTypes.DI_INVOICE_CREATED;
import static org.folio.invoices.utils.ResourcePathResolver.ORDER_LINES;
import static org.folio.invoices.utils.ResourcePathResolver.resourcesPath;
import static org.folio.rest.jaxrs.model.EntityType.EDIFACT_INVOICE;
import static org.folio.rest.jaxrs.model.InvoiceLine.InvoiceLineStatus.OPEN;
import static org.folio.rest.jaxrs.model.ProfileSnapshotWrapper.ContentType.ACTION_PROFILE;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

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

import io.vertx.core.Vertx;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

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
  private static final String POL_NUMBER_KEY = "POL_NUMBER_%s";
  private static final String POL_EXPENSE_CLASS_KEY = "POL_EXPENSE_CLASS_%s";
  private static final String POL_FUND_DISTRIBUTIONS_KEY = "POL_FUND_DISTRIBUTIONS_%s";
  private static final Pattern SEGMENT_QUERY_PATTERN = Pattern.compile("([A-Z]{3}((\\+|<)\\w*)(\\2*\\w*)*(\\?\\w+)?\\[[1-9](-[1-9])?\\])");
  private static final String DEFAULT_LANG = "en";

  private final RestClient restClient;

  public CreateInvoiceEventHandler(RestClient restClient) {
    this.restClient = restClient;
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
      CompletableFuture<Map<Integer, PoLine>> poLinesFuture = getAssociatedPoLines(dataImportEventPayload, okapiHeaders);

      poLinesFuture
        .thenAccept(invLineNoToPoLine -> ensureAdditionalData(dataImportEventPayload, invLineNoToPoLine))
        .thenAccept(v -> prepareEventPayloadForMapping(dataImportEventPayload))
        .thenAccept(v -> MappingManager.map(dataImportEventPayload, new MappingContext()))
        .thenAccept(v -> prepareMappingResult(dataImportEventPayload))
        .thenCompose(v -> saveInvoice(dataImportEventPayload, okapiHeaders))
        .thenApply(savedInvoice -> prepareInvoiceLinesToSave(savedInvoice.getId(), dataImportEventPayload, poLinesFuture.join()))
        .thenCompose(preparedInvoiceLines -> saveInvoiceLines(preparedInvoiceLines, okapiHeaders))
        .whenComplete((savedInvoiceLines, throwable) -> {
          if (throwable == null) {
            List<InvoiceLine> invoiceLines = savedInvoiceLines.stream().map(Pair::getLeft).collect(Collectors.toList());
            InvoiceLineCollection invoiceLineCollection = new InvoiceLineCollection().withInvoiceLines(invoiceLines).withTotalRecords(invoiceLines.size());
            dataImportEventPayload.getContext().put(INVOICE_LINES_KEY, Json.encode(invoiceLineCollection));
            Map<Integer, String> invoiceLinesErrors = prepareInvoiceLinesErrors(savedInvoiceLines);
            if (!invoiceLinesErrors.isEmpty()) {
              dataImportEventPayload.getContext().put(INVOICE_LINES_ERRORS_KEY, Json.encode(invoiceLinesErrors));
              future.completeExceptionally(new EventProcessingException("Error during invoice lines creation"));
              return;
            }
            future.complete(dataImportEventPayload);
          } else {
            preparePayloadWithMappedInvoiceLines(dataImportEventPayload);
            logger.error("Error during invoice creation", throwable);
            future.completeExceptionally(throwable);
          }
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

  private CompletableFuture<Map<Integer, PoLine>> getAssociatedPoLines(DataImportEventPayload eventPayload, Map<String, String> okapiHeaders) {
    String recordAsString = eventPayload.getContext().get(EDIFACT_INVOICE.value());
    Record sourceRecord = Json.decodeValue(recordAsString, Record.class);
    ParsedRecord parsedRecord = sourceRecord.getParsedRecord();
    long invoiceLinesAmount = determineInvoiceLinesQuantity(parsedRecord);

    Optional<String> poLineNoExpressionOptional = getPoLineNoMappingExpression(eventPayload);
    Map<Integer, String> invoiceLineNoToPoLineNo = poLineNoExpressionOptional
      .map(expression -> EdifactRecordReader.getInvoiceLinesSegmentsValues(parsedRecord, expression))
      .orElse(Collections.emptyMap());

    List<String> ReferenceNumberExpressions = getPoLineRefNumberMappingExpressions(eventPayload);
    Map<Integer, List<String>> invoiceLineNoToRefNo2 = ReferenceNumberExpressions.isEmpty()
      ? Collections.emptyMap() : retrieveInvoiceLinesReferenceNumbers(parsedRecord, ReferenceNumberExpressions);

    return getAssociatedPoLinesByPoLineNumber(invoiceLineNoToPoLineNo, okapiHeaders)
      .thenCompose(associatedPoLineMap -> {
        if (associatedPoLineMap.size() < invoiceLinesAmount) {
          associatedPoLineMap.keySet().forEach(invoiceLineNoToRefNo2::remove);
          return getAssociatedPoLinesByRefNumbers(invoiceLineNoToRefNo2, okapiHeaders).thenApply(poLinesMap -> {
            associatedPoLineMap.putAll(poLinesMap);
            return associatedPoLineMap;
          });
        }
        return CompletableFuture.completedFuture(associatedPoLineMap);
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

  private CompletableFuture<Map<Integer, PoLine>> getAssociatedPoLinesByPoLineNumber(Map<Integer, String> invoiceLineNoToPoLineNo, Map<String, String> okapiHeaders) {
    Map<Integer, PoLine> invoiceLineNoToPoLine = new HashMap<>();
    if (invoiceLineNoToPoLineNo.isEmpty()) {
      return CompletableFuture.completedFuture(invoiceLineNoToPoLine);
    }

    String preparedCql = prepareQueryGetPoLinesByNumber(List.copyOf(invoiceLineNoToPoLineNo.values()));
    RequestEntry requestEntry = new RequestEntry(resourcesPath(ORDER_LINES))
        .withQuery(preparedCql)
        .withOffset(0)
        .withLimit(Integer.MAX_VALUE);
    return restClient.get(requestEntry, new RequestContext(Vertx.currentContext(), okapiHeaders), PoLineCollection.class)
      .thenApply(poLineCollection -> poLineCollection.getPoLines().stream().collect(Collectors.toMap(PoLine::getPoLineNumber, poLine -> poLine)))
      .thenAccept(poLineNumberToPoLine -> invoiceLineNoToPoLineNo
        .forEach((key, value) -> poLineNumberToPoLine.computeIfPresent(value, (polNo, poLine) -> invoiceLineNoToPoLine.put(key, poLine))))
      .thenApply(v -> invoiceLineNoToPoLine);
  }

  private CompletableFuture<Map<Integer, PoLine>> getAssociatedPoLinesByRefNumbers(
      Map<Integer, List<String>> invoiceLineNoToRefNumbers, Map<String, String> okapiHeaders) {
    if (invoiceLineNoToRefNumbers.isEmpty()) {
      return CompletableFuture.completedFuture(new HashMap<>());
    }
    RequestContext requestContext = new RequestContext(Vertx.currentContext(), okapiHeaders);

    return CompletableFuture.completedFuture(getAssociatedPoLinesByRefNumbers(invoiceLineNoToRefNumbers, requestContext));

  }

  private Map<Integer, PoLine> getAssociatedPoLinesByRefNumbers(Map<Integer, List<String>> invoiceLineNoToRefNumbers, RequestContext requestContext) {
    Map<Integer, PoLine> poLinesByRefNumbers = new HashMap<>();

    invoiceLineNoToRefNumbers.entrySet()
      .stream()
      .sequential()
      .forEach(entry -> {
        var invoiceLineNumber = entry.getKey();
        var refNumberList = entry.getValue();
        String cqlGetPoLinesByRefNo = prepareQueryGetPoLinesByRefNumber(refNumberList);
        RequestEntry requestEntry = new RequestEntry(resourcesPath(ORDER_LINES)).withQuery(cqlGetPoLinesByRefNo)
          .withOffset(0)
          .withLimit(Integer.MAX_VALUE);
        try {
          var polines = restClient.get(requestEntry, requestContext, PoLineCollection.class)
            .join();
          if (polines.getPoLines().size() == 1) {
            poLinesByRefNumbers.put(invoiceLineNumber, polines.getPoLines().get(0));
          }
        } catch (Exception e) {
          logger.error(requestEntry.buildEndpoint(), e);
        }
      });

    return poLinesByRefNumbers;
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

  private CompletableFuture<Invoice> saveInvoice(DataImportEventPayload dataImportEventPayload, Map<String, String> okapiHeaders) {
    Invoice invoiceToSave = Json.decodeValue(dataImportEventPayload.getContext().get(INVOICE.value()), Invoice.class);
    invoiceToSave.setSource(Invoice.Source.EDI);
    InvoiceHelper invoiceHelper = new InvoiceHelper(okapiHeaders, Vertx.currentContext(), DEFAULT_LANG);

    return invoiceHelper.createInvoice(invoiceToSave).whenComplete((invoice, throwable) -> {
      if (throwable == null) {
        dataImportEventPayload.getContext().put(INVOICE.value(), Json.encode(invoice));
        return;
      }
      logger.error("Error during creation invoice in the storage", throwable);
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
    return invoiceLines;
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

  private CompletableFuture<List<Pair<InvoiceLine, String>>> saveInvoiceLines(List<InvoiceLine> invoiceLines, Map<String, String> okapiHeaders) {
    ArrayList<CompletableFuture<InvoiceLine>> futures = new ArrayList<>();

    CompletableFuture<InvoiceLine> future = CompletableFuture.completedFuture(null);
    InvoiceLineHelper helper = new InvoiceLineHelper(okapiHeaders, Vertx.currentContext(), DEFAULT_LANG);
    for (InvoiceLine invoiceLine : invoiceLines) {
      future = future.thenCompose(v -> helper.createInvoiceLine(invoiceLine));
      futures.add(future);
    }

    List<Pair<InvoiceLine, String>> savingResults = new ArrayList<>();
    return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).handle((v, throwable) -> {
      for (int i = 0; i < futures.size(); i++) {
        InvoiceLine mappedInvoiceLine = invoiceLines.get(i);
        int invLineNumber = i + 1;
        futures.get(i).whenComplete((savedInvLine, e) -> {
          if (e != null) {
            logger.error("Error to create invoice line with number {}", invLineNumber, e);
          }
          Pair<InvoiceLine, String> invoiceLineToMsg = e == null ? Pair.of(savedInvLine, null) : Pair.of(mappedInvoiceLine, e.getMessage());
          savingResults.add(invoiceLineToMsg);
        });
      }
      return savingResults;
    });
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

  @Override
  public boolean isEligible(DataImportEventPayload dataImportEventPayload) {
    if (dataImportEventPayload.getCurrentNode() != null && ACTION_PROFILE == dataImportEventPayload.getCurrentNode().getContentType()) {
      ActionProfile actionProfile = JsonObject.mapFrom(dataImportEventPayload.getCurrentNode().getContent()).mapTo(ActionProfile.class);
      return actionProfile.getAction() == CREATE && actionProfile.getFolioRecord() == INVOICE;
    }
    return false;
  }
}
