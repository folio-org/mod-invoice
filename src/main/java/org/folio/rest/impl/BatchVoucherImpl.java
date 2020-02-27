package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import me.escoffier.vertx.completablefuture.VertxCompletableFuture;


import org.folio.jaxb.XMLConverter;
import org.folio.rest.jaxrs.model.BatchVoucher;
import org.folio.rest.jaxrs.model.jaxb.BatchVoucherType;
import org.folio.rest.jaxrs.resource.BatchVoucherBatchVouchers;
import org.folio.spring.SpringContextUtil;
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
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import static io.vertx.core.Future.succeededFuture;


public class BatchVoucherImpl implements BatchVoucherBatchVouchers {
  private static final Logger LOG = LoggerFactory.getLogger(BatchGroupsImpl.class);



  @Override
  public void getBatchVoucherBatchVouchersById(String id, String lang, String contentType, Map<String, String> okapiHeaders
    , Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    BatchVoucherHelper helper = new BatchVoucherHelper(okapiHeaders, vertxContext, lang);
     helper.getBatchVoucherById(id, contentType)
       .thenAccept(response -> asyncResultHandler.handle(succeededFuture(response)))
       .exceptionally(t -> handleErrorResponse(asyncResultHandler, helper, t));
  }

  private Void handleErrorResponse(Handler<AsyncResult<Response>> asyncResultHandler, AbstractHelper helper, Throwable t) {
    asyncResultHandler.handle(succeededFuture(helper.buildErrorResponse(t)));
    return null;
  }

}
