package org.folio.services.voucher;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import javax.ws.rs.core.Response;

import org.folio.invoices.rest.exceptions.HttpException;
import org.folio.rest.impl.ApiTestBase;
import org.folio.rest.impl.BatchVoucherExportConfigHelper;
import org.folio.rest.impl.BatchVoucherExportsHelper;
import org.folio.rest.impl.BatchVoucherHelper;
import org.folio.rest.jaxrs.model.BatchVoucher;
import org.folio.rest.jaxrs.model.BatchVoucherExport;
import org.folio.rest.jaxrs.model.Credentials;
import org.folio.rest.jaxrs.model.ExportConfigCollection;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.vertx.core.impl.EventLoopContext;

public class UploadBatchVoucherExportServiceTest extends ApiTestBase {
  private static final String BV_ID = "35657479-83b9-4760-9c39-b58dcd02ee14";
  private static final String BV_EXPORT_ID = "566c9156-e52f-4597-9fee-5ddac91d14f2";
  private static final String BV_EXPORT_CONF_ID = "configs";
  private static final String CRED_ID = "574f0791-beca-4470-8037-050660cfb73a";
  private static final String BATCH_VOUCHERS_PATH = BASE_MOCK_DATA_PATH + "batchVouchers/" + BV_ID + ".json";
  private static final String BATCH_VOUCHERS_EXPORT_PATH = BASE_MOCK_DATA_PATH + "batchVoucherExports/" + BV_EXPORT_ID  + ".json";
  private static final String BATCH_VOUCHERS_EXPORT_CONF_PATH = BASE_MOCK_DATA_PATH + "batchVoucherExportConfigs/" + BV_EXPORT_CONF_ID  + ".json";
  private static final String CRED_PATH = BASE_MOCK_DATA_PATH + "credentials/" + CRED_ID  + ".json";

  @InjectMocks
  private UploadBatchVoucherExportService service;
  @Mock
  private BatchVoucherHelper bvHelper;
  @Mock
  private BatchVoucherExportConfigHelper bvExportConfigHelper;
  @Mock
  private BatchVoucherExportsHelper bvExportsHelper;
  @Mock
  private Map<String, String> okapiHeadersMock;
  @Mock
  private EventLoopContext ctxMock;

  @BeforeEach
  public void initMocks(){
    MockitoAnnotations.initMocks(this);
  }


  @Test
  public void testServiceConstructor() {
    //given
    UploadBatchVoucherExportService service =  new UploadBatchVoucherExportService(ctxMock, bvHelper, bvExportConfigHelper, bvExportsHelper);
    //Then
    assertNotNull(service);
  }

  @Test
  public void testShouldSuccessUploadBatchVoucherExport() throws ExecutionException, InterruptedException {
    //given
    UploadBatchVoucherExportService serviceSpy = spy(new UploadBatchVoucherExportService(ctxMock, bvHelper, bvExportConfigHelper, bvExportsHelper));
    BatchVoucher bv = getMockAsJson(BATCH_VOUCHERS_PATH).mapTo(BatchVoucher.class);
    Response.ResponseBuilder responseBuilder = Response.status(200).header("Content-Type", "application/json");
    Response responseBV = responseBuilder.entity(bv).build();

    BatchVoucherExport bvExport = getMockAsJson(BATCH_VOUCHERS_EXPORT_PATH).mapTo(BatchVoucherExport.class);
    ExportConfigCollection bvExportConf = getMockAsJson(BATCH_VOUCHERS_EXPORT_CONF_PATH).mapTo(ExportConfigCollection.class);

    Credentials credentials = getMockAsJson(CRED_PATH).mapTo(Credentials.class);
    doReturn(completedFuture(bvExport)).when(bvExportsHelper).getBatchVoucherExportById(BV_EXPORT_ID);

    doReturn(completedFuture(credentials))
      .when(bvExportConfigHelper).getExportConfigCredentials(bvExportConf.getExportConfigs().get(0).getId());
    doReturn(completedFuture(bvExportConf))
      .when(bvExportConfigHelper).getExportConfigs(1, 0, "batchGroupId==" + bvExport.getBatchGroupId());

    doReturn(completedFuture(null)).when(bvExportsHelper).updateBatchVoucherExportRecord(eq(bvExport));
    doReturn(completedFuture(responseBV)).when(bvHelper).getBatchVoucherById(BV_ID, "application/xml");
    doReturn(completedFuture(null)).when(serviceSpy).uploadBatchVoucher(any());
    //When
    serviceSpy.uploadBatchVoucherExport(BV_EXPORT_ID).get();
    //Then
    verify(bvExportsHelper).getBatchVoucherExportById(BV_EXPORT_ID);
    verify(bvExportConfigHelper).getExportConfigCredentials(bvExportConf.getExportConfigs().get(0).getId());
    verify(bvHelper).getBatchVoucherById(BV_ID, "application/xml");
    verify(bvExportsHelper).updateBatchVoucherExportRecord(eq(bvExport));
    verify(bvExportConfigHelper).getExportConfigs(eq(1), eq(0), anyString());
    assertEquals(BatchVoucherExport.Status.UPLOADED, bvExport.getStatus());
  }

  @Test
  public void testShouldFailIfBatchVoucherExportNotFound() {
    //given
    CompletableFuture<BatchVoucherExport> future = new CompletableFuture<>();
    future.completeExceptionally(new HttpException(404, "Not found"));
    when(bvExportsHelper.getBatchVoucherExportById(BV_EXPORT_ID))
      .thenReturn(future);
    //When
    CompletableFuture<Void> actFuture = service.uploadBatchVoucherExport(BV_EXPORT_ID);
    //Then
    actFuture.join();
    verify(bvExportsHelper).getBatchVoucherExportById(BV_EXPORT_ID);
  }
}
