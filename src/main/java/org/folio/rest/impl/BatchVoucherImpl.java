package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import me.escoffier.vertx.completablefuture.VertxCompletableFuture;


import org.folio.helpers.jaxb.JAXBHelper;
import org.folio.rest.jaxrs.model.BatchVoucherType;
import org.folio.rest.jaxrs.resource.BatchVoucherBatchVouchers;
import org.springframework.beans.factory.annotation.Autowired;
import javax.ws.rs.core.Response;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.File;
import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static io.vertx.core.Future.succeededFuture;
import static org.folio.rest.impl.BatchVoucherHelper.RequestHolder;


public class BatchVoucherImpl implements BatchVoucherBatchVouchers {
  private static final Logger LOG = LoggerFactory.getLogger(BatchGroupsImpl.class);

  @Autowired
  private JAXBHelper jaxbHelper;

  @Autowired
  private  BatchVoucherHelper service;

  @Override
  public void getBatchVoucherBatchVouchersById(String id, String lang, String contentType, Map<String, String> okapiHeaders
    , Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    RequestHolder requestHolder = new  RequestHolder(okapiHeaders, vertxContext, lang);
    service.getBatchVoucherByIdAs(id, requestHolder, LOG)
            .thenApply(jsonBatchVoucher -> buildSuccessResponse(vertxContext, contentType))
            .thenAccept(response ->  asyncResultHandler.handle(succeededFuture(service.buildOkResponse(response))))
            .exceptionally(t -> handleErrorResponse(asyncResultHandler, service, t));

  }

  private Void handleErrorResponse(Handler<AsyncResult<Response>> asyncResultHandler, AbstractHelper helper, Throwable t) {
    asyncResultHandler.handle(succeededFuture(helper.buildErrorResponse(t)));
    return null;
  }

  protected CompletableFuture<Response> buildSuccessResponse(Context vertxContext, String contentType) {
    ClassLoader classLoader = this.getClass().getClassLoader();
    InputStream inputStream = classLoader.getResourceAsStream("ramls"  + File.separator + "examples" + File.separator + "batch_voucher_xml.sample");
    JAXBElement<BatchVoucherType> batchVoucher = null;
    try {
      JAXBContext jc = JAXBContext.newInstance(BatchVoucherType.class);
      XMLStreamReader reader = XMLInputFactory.newInstance().createXMLStreamReader(inputStream);
      Unmarshaller unMarshaller = jc.createUnmarshaller();
      batchVoucher = unMarshaller.unmarshal(reader, BatchVoucherType.class);
    } catch (XMLStreamException | JAXBException e) {
        throw  new IllegalArgumentException("Exception ", e);
    }

    CompletableFuture<Response> future = new VertxCompletableFuture<>(vertxContext);
    future.complete(GetBatchVoucherBatchVouchersByIdResponse.respond200WithApplicationXml(jaxbHelper.marshal(batchVoucher.getValue(), true)));
    return future;
  }

}
