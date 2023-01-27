package org.folio.services.voucher;

import static io.vertx.core.Future.succeededFuture;
import static org.folio.ApiTestSuite.mockPort;
import static org.folio.ApiTestSuite.vertx;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.openMocks;

import java.util.HashMap;
import java.util.Map;

import javax.ws.rs.core.Response;

import org.folio.invoices.rest.exceptions.HttpException;
import org.folio.rest.RestConstants;
import org.folio.rest.core.RestClient;
import org.folio.rest.core.models.RequestContext;
import org.folio.rest.impl.ApiTestBase;
import org.folio.rest.impl.BatchVoucherExportConfigHelper;
import org.folio.rest.impl.BatchVoucherExportsHelper;
import org.folio.rest.impl.UploadBatchVoucherExportHelper;
import org.folio.rest.jaxrs.model.BatchVoucher;
import org.folio.rest.jaxrs.model.BatchVoucherExport;
import org.folio.rest.jaxrs.model.Credentials;
import org.folio.rest.jaxrs.model.ExportConfigCollection;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

@ExtendWith(VertxExtension.class)
public class UploadBatchVoucherExportServiceTest extends ApiTestBase {
  private static final String BV_ID = "35657479-83b9-4760-9c39-b58dcd02ee14";
  private static final String BV_EXPORT_ID = "566c9156-e52f-4597-9fee-5ddac91d14f2";
  private static final String BV_EXPORT_CONF_ID = "configs";
  private static final String CRED_ID = "574f0791-beca-4470-8037-050660cfb73a";
  private static final String BATCH_VOUCHERS_PATH = BASE_MOCK_DATA_PATH + "batchVouchers/" + BV_ID + ".json";
  private static final String BATCH_VOUCHERS_EXPORT_PATH = BASE_MOCK_DATA_PATH + "batchVoucherExports/" + BV_EXPORT_ID  + ".json";
  private static final String BATCH_VOUCHERS_EXPORT_CONF_PATH = BASE_MOCK_DATA_PATH + "batchVoucherExportConfigs/" + BV_EXPORT_CONF_ID  + ".json";
  private static final String CRED_PATH = BASE_MOCK_DATA_PATH + "credentials/" + CRED_ID  + ".json";


  @Mock
  private BatchVoucherService batchVoucherService;
  @Mock
  private BatchVoucherExportConfigHelper bvExportConfigHelper;
  @Mock
  private BatchVoucherExportsHelper bvExportsHelper;
  private Context context;
  @Mock
  private RestClient restClient;
  private Map<String, String> okapiHeaders;
  private RequestContext requestContext;

  @BeforeEach
  public void setUp()  {
    super.setUp();
    context = vertx.getOrCreateContext();
    okapiHeaders = new HashMap<>();
    okapiHeaders.put(RestConstants.OKAPI_URL, "http://localhost:" + mockPort);
    okapiHeaders.put(X_OKAPI_TOKEN.getName(), X_OKAPI_TOKEN.getValue());
    okapiHeaders.put(X_OKAPI_TENANT.getName(), X_OKAPI_TENANT.getValue());
    okapiHeaders.put(X_OKAPI_USER_ID.getName(), X_OKAPI_USER_ID.getValue());
    requestContext = new RequestContext(context, okapiHeaders);
    openMocks(this);
  }


  @Test
  public void testServiceConstructor() {
    //given
    UploadBatchVoucherExportHelper service = new UploadBatchVoucherExportHelper(okapiHeaders, context);
    //Then
    Assertions.assertNotNull(service);
  }

  @Test
  @Disabled
  public void testShouldSuccessUploadBatchVoucherExport(VertxTestContext vertxTestContext) {
    //given
    UploadBatchVoucherExportHelper uploadBatchVoucherExportHelper = spy(new UploadBatchVoucherExportHelper(okapiHeaders, context));
    BatchVoucher bv = getMockAsJson(BATCH_VOUCHERS_PATH).mapTo(BatchVoucher.class);
    Response.ResponseBuilder responseBuilder = Response.status(200).header("Content-Type", "application/json");

    BatchVoucherExport bvExport = getMockAsJson(BATCH_VOUCHERS_EXPORT_PATH).mapTo(BatchVoucherExport.class);
    ExportConfigCollection bvExportConf = getMockAsJson(BATCH_VOUCHERS_EXPORT_CONF_PATH).mapTo(ExportConfigCollection.class);

    Credentials credentials = getMockAsJson(CRED_PATH).mapTo(Credentials.class);
    doReturn(succeededFuture(bvExport)).when(restClient).get(any(String.class), eq(BatchVoucherExport.class), any(RequestContext.class));

    doReturn(succeededFuture(credentials))
      .when(bvExportConfigHelper).getExportConfigCredentials(bvExportConf.getExportConfigs().get(0).getId());
    doReturn(succeededFuture(bvExportConf))
      .when(bvExportConfigHelper).getExportConfigs(1, 0, "batchGroupId==" + bvExport.getBatchGroupId());

    doReturn(succeededFuture(null)).when(bvExportsHelper).updateBatchVoucherExportRecord(eq(bvExport));
    doReturn(succeededFuture(bv)).when(batchVoucherService).getBatchVoucherById(BV_ID, requestContext);
    //When
    var future = uploadBatchVoucherExportHelper.uploadBatchVoucherExport(BV_EXPORT_ID);
    //Then
    vertxTestContext.assertComplete(future)
      .onComplete(result -> {
        verify(bvExportsHelper).getBatchVoucherExportById(BV_EXPORT_ID);
        verify(bvExportConfigHelper).getExportConfigCredentials(bvExportConf.getExportConfigs().get(0).getId());
        verify(batchVoucherService).getBatchVoucherById(BV_ID, requestContext);
        verify(bvExportsHelper).updateBatchVoucherExportRecord(eq(bvExport));
        verify(bvExportConfigHelper).getExportConfigs(eq(1), eq(0), anyString());
        Assertions.assertEquals(BatchVoucherExport.Status.UPLOADED, bvExport.getStatus());
        vertxTestContext.completeNow();
      });

  }

  @Test
  @Disabled
  public void testShouldFailIfBatchVoucherExportNotFound(VertxTestContext vertxTestContext) {
    //given
    UploadBatchVoucherExportHelper uploadBatchVoucherExportHelper = spy(new UploadBatchVoucherExportHelper(okapiHeaders, context));

    when(bvExportsHelper.getBatchVoucherExportById(BV_EXPORT_ID))
      .thenReturn(Future.failedFuture(new HttpException(404, "Not found")));
    //When
    Future<Void> future = uploadBatchVoucherExportHelper.uploadBatchVoucherExport(BV_EXPORT_ID);
    //Then
    vertxTestContext.assertFailure(future)
      .onComplete(result -> {
        verify(bvExportsHelper).getBatchVoucherExportById(BV_EXPORT_ID);
        vertxTestContext.completeNow();
      });
  }

  @Test
  public void testFineNameGenerateLogicIdThereIsSeparatorInUUID() {
    //given
    UploadBatchVoucherExportHelper serviceSpy = spy(new UploadBatchVoucherExportHelper(okapiHeaders, context));
    BatchVoucher bv = getMockAsJson(BATCH_VOUCHERS_PATH).mapTo(BatchVoucher.class);
    String expId = "b58dcd02ee14";
    bv.setId("xxx-yyy-zzz-" + expId);
    //When
    String actFileName = serviceSpy.generateFileName(bv, "json");
    //Then
    Assertions.assertEquals("bv_"+expId+"_Amherst College (AC)_2019-12-06_2019-12-07.json", actFileName);
  }

  @Test
  public void testFineNameGenerateLogicIdThereNoSeparatorInUUID() {
    //given
    UploadBatchVoucherExportHelper serviceSpy = spy(new UploadBatchVoucherExportHelper(okapiHeaders, context));
    BatchVoucher bv = getMockAsJson(BATCH_VOUCHERS_PATH).mapTo(BatchVoucher.class);
    bv.setId("xxxyyyzzb58dcd02ee14");
    //When
    String actFileName = serviceSpy.generateFileName(bv, "json");
    //Then
    Assertions.assertEquals("bv_"+bv.getId()+"_Amherst College (AC)_2019-12-06_2019-12-07.json", actFileName);
  }
}
