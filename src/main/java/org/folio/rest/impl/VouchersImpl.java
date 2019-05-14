package org.folio.rest.impl;

import static io.vertx.core.Future.succeededFuture;

import java.util.Map;

import javax.ws.rs.core.Response;
import org.apache.commons.lang3.StringUtils;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.Invoice;
import org.folio.rest.jaxrs.model.InvoiceLine;
import org.folio.rest.jaxrs.model.VoucherLine;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

public class VouchersImpl implements org.folio.rest.jaxrs.resource.Voucher {

  private static final Logger logger = LoggerFactory.getLogger(VouchersImpl.class);
  private static final String NOT_SUPPORTED = "Not supported";  // To overcome sonarcloud warning
  private static final String INVOICE_LOCATION_PREFIX = "/invoice/invoices/%s";
  private static final String INVOICE_LINE_LOCATION_PREFIX = "/invoice/invoice-lines/%s";


//  private Void handleErrorResponse(Handler<AsyncResult<Response>> asyncResultHandler, AbstractHelper helper,
//                                   Throwable t) {
//    asyncResultHandler.handle(succeededFuture(helper.buildErrorResponse(t)));
//    return null;
//  }


  @Override
  public void getVoucherVoucherLines(int offset, int limit, String query, String lang, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    // TODO Auto-generated method stub
    
  }


  @Override
  public void postVoucherVoucherLines(String lang, VoucherLine entity, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    // TODO Auto-generated method stub
    
  }


  @Override
  public void getVoucherVoucherLinesById(String id, String lang, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    // TODO Auto-generated method stub
    
  }


  @Override
  public void deleteVoucherVoucherLinesById(String id, String lang, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    // TODO Auto-generated method stub
    
  }


  @Override
  public void putVoucherVoucherLinesById(String id, String lang, VoucherLine entity, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    // TODO Auto-generated method stub
    
  }
}
