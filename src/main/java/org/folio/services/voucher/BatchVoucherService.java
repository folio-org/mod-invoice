package org.folio.services.voucher;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.MediaType.APPLICATION_XML;
import static org.folio.invoices.utils.ResourcePathResolver.BATCH_VOUCHER_STORAGE;
import static org.folio.invoices.utils.ResourcePathResolver.resourceByIdPath;
import static org.folio.invoices.utils.ResourcePathResolver.resourcesPath;

import javax.xml.stream.XMLStreamException;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.converters.BatchVoucherModelConverter;
import org.folio.invoices.rest.exceptions.HttpException;
import org.folio.jaxb.XMLConverter;
import org.folio.rest.core.RestClient;
import org.folio.rest.core.models.RequestContext;
import org.folio.rest.jaxrs.model.BatchVoucher;
import org.folio.rest.jaxrs.model.jaxb.BatchVoucherType;
import org.springframework.stereotype.Service;

@Service
public class BatchVoucherService {

  private static final Logger log = LogManager.getLogger();
  private static final String HEADER_ERROR_MSG = "Accept header must be [\"application/xml\",\"application/json\"]";
  private static final String MARSHAL_ERROR_MSG = "Internal server error. Can't marshal response to XML. Error message: ";
  private final XMLConverter xmlConverter;
  private final BatchVoucherModelConverter batchVoucherModelConverter;
  private final RestClient restClient;

  public BatchVoucherService(RestClient restClient) {
    this.restClient = restClient;
    this.xmlConverter = XMLConverter.getInstance();
    this.batchVoucherModelConverter = BatchVoucherModelConverter.getInstance();
  }

  public Future<BatchVoucher> getBatchVoucherById(String id, RequestContext requestContext) {
    String endpoint = resourceByIdPath(BATCH_VOUCHER_STORAGE, id);
    return restClient.get(endpoint, BatchVoucher.class, requestContext);
  }

  public String convertBatchVoucher(BatchVoucher batchVoucher, String contentType) {
    if (contentType.equalsIgnoreCase(APPLICATION_JSON)){
      return JsonObject.mapFrom(batchVoucher).encodePrettily();
    }
    if (contentType.equalsIgnoreCase(APPLICATION_XML)) {
      BatchVoucherType xmlBatchVoucher = batchVoucherModelConverter.convert(batchVoucher);
      try {
        return xmlConverter.marshal(BatchVoucherType.class, xmlBatchVoucher, null,true);
      } catch (XMLStreamException e) {
        log.error("Internal server error. Can't marshal response to XML. batchVoucherId: {}, contentType: {}, Error message: {}", batchVoucher.getId(), contentType, e.getMessage());
        throw new HttpException(400, MARSHAL_ERROR_MSG + e.getMessage());
      }
    }
    log.error("Accept header must be [\"application/xml\",\"application/json\"]. batchVoucherId: {}, contentType: {}", batchVoucher.getId(), contentType);
    throw new HttpException(400, HEADER_ERROR_MSG);
  }

  public Future<BatchVoucher> createBatchVoucher(BatchVoucher batchVoucher, RequestContext requestContext) {
    return restClient.post(resourcesPath(BATCH_VOUCHER_STORAGE), batchVoucher, BatchVoucher.class, requestContext);
  }
}
