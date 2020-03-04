package org.folio.schemas.xsd;

import java.io.FileInputStream;
import java.io.FileReader;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import javax.validation.constraints.NotNull;
import javax.xml.XMLConstants;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;

import org.apache.commons.lang3.StringUtils;
import org.folio.rest.jaxrs.model.BatchVoucher;
import org.folio.rest.jaxrs.model.BatchedVoucher;
import org.folio.rest.jaxrs.model.BatchedVoucherLine;
import org.folio.rest.jaxrs.model.jaxb.BatchVoucherType;
import org.folio.rest.jaxrs.model.jaxb.BatchedVoucherLineType;
import org.folio.rest.jaxrs.model.jaxb.BatchedVoucherType;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

public class BatchVoucherSchemaXSDTest {
  private static Path XSD_BATCH_VOUCHER_SCHEMA_PATH ;
  private static Path XML_BATCH_VOUCHER_EXAMPLES_PATH;

  private static AnnotationModel<Void> jsonRequiredAnn;
  private static AnnotationModel<Boolean> jaxbRequiredAnn;
  private static Map<String, String> batchVoucherRequiredFields;
  private static Map<String, String> batchedVoucherRequiredFields;
  private static Map<String, String> batchedVoucherLinesRequiredFields;

  @BeforeClass
  public static void beforeAll(){
    XSD_BATCH_VOUCHER_SCHEMA_PATH = Paths.get("", "ramls/schemas/batch_voucher.xsd").toAbsolutePath();
    XML_BATCH_VOUCHER_EXAMPLES_PATH = Paths.get("ramls/examples", "batch_voucher_xml.sample").toAbsolutePath();

    jsonRequiredAnn = new AnnotationModel<>(NotNull.class, null, null);
    jaxbRequiredAnn = new AnnotationModel<>(XmlElement.class, "required", Boolean.TRUE);

    batchVoucherRequiredFields = new HashMap<>();
    batchVoucherRequiredFields.put("batchGroup", "batchGroup");
    batchVoucherRequiredFields.put("created", "created");
    batchVoucherRequiredFields.put("start", "start");
    batchVoucherRequiredFields.put("end", "end");
    batchVoucherRequiredFields.put("batchedVouchers", "batchedVouchers");
    batchVoucherRequiredFields.put("totalRecords", "totalRecords");

    batchedVoucherRequiredFields = new HashMap<>();
    batchedVoucherRequiredFields.put("accountingCode", "accountingCode");
    batchedVoucherRequiredFields.put("amount", "amount");
    batchedVoucherRequiredFields.put("batchedVoucherLines", "batchedVoucherLines");
    batchedVoucherRequiredFields.put("invoiceCurrency", "invoiceCurrency");
    batchedVoucherRequiredFields.put("status", "status");
    batchedVoucherRequiredFields.put("systemCurrency", "systemCurrency");
    batchedVoucherRequiredFields.put("type", "type");
    batchedVoucherRequiredFields.put("vendorInvoiceNo", "vendorInvoiceNo");
    batchedVoucherRequiredFields.put("vendorName", "vendorName");
    batchedVoucherRequiredFields.put("voucherDate", "voucherDate");
    batchedVoucherRequiredFields.put("voucherNumber", "voucherNumber");

    batchedVoucherLinesRequiredFields = new HashMap<>();
    batchedVoucherLinesRequiredFields.put("amount", "amount");
    batchedVoucherLinesRequiredFields.put("fundCodes", "fundCodes");
    batchedVoucherLinesRequiredFields.put("externalAccountNumber", "externalAccountNumber");
  }

  @Test
  public void testValidateSampleDataBySchema() throws Exception {
    InputStream xsd = new FileInputStream(XSD_BATCH_VOUCHER_SCHEMA_PATH.toString());

    SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
    Schema schema = factory.newSchema(new StreamSource(xsd));

    Validator validator = schema.newValidator();
    validator.validate(new StreamSource(new FileReader(XML_BATCH_VOUCHER_EXAMPLES_PATH.toString())));
  }

  @Test
  public void checkThatAllRequiredFiledsfromJSONModelExistInXMModel() throws Exception {
    assertThatBatchVoucherIsRequired(batchVoucherRequiredFields);
    assertThatBatchedVoucherIsRequired(batchedVoucherRequiredFields);
    assertThatBatchedVoucherLinesIsRequired(batchedVoucherLinesRequiredFields);
  }

  private void assertThatBatchVoucherIsRequired(Map<String, String> fieldPairs) throws Exception {
    for (Map.Entry<String, String> fieldPair : fieldPairs.entrySet()) {
      assertThatAnnotationExist(jsonRequiredAnn, BatchVoucher.class, fieldPair.getKey());
      assertThatAnnotationExist(jaxbRequiredAnn, BatchVoucherType.class, fieldPair.getValue());
    }
  }

  private void assertThatBatchedVoucherIsRequired(Map<String, String> fieldPairs) throws Exception {
    for (Map.Entry<String, String> fieldPair : fieldPairs.entrySet()) {
      assertThatAnnotationExist(jsonRequiredAnn, BatchedVoucher.class, fieldPair.getKey());
      assertThatAnnotationExist(jaxbRequiredAnn, BatchedVoucherType.class, fieldPair.getValue());
    }
  }

  private void assertThatBatchedVoucherLinesIsRequired(Map<String, String> fieldPairs) throws Exception {
    for (Map.Entry<String, String> fieldPair : fieldPairs.entrySet()) {
      assertThatAnnotationExist(jsonRequiredAnn, BatchedVoucherLine.class, fieldPair.getKey());
      assertThatAnnotationExist(jaxbRequiredAnn, BatchedVoucherLineType.class, fieldPair.getValue());
    }
  }


  private <T> void assertThatAnnotationExist(AnnotationModel<T> annotationModel, Class<?> clazz, String fieldName)
    throws NoSuchFieldException, NoSuchMethodException, InvocationTargetException, IllegalAccessException {
      Field field = clazz.getDeclaredField(fieldName);
      Annotation annotation = field.getAnnotation(annotationModel.annotationType);
      Assert.assertNotNull("Field should have annotation : " + annotationModel.annotationType.getName(), annotation);
      if (!StringUtils.isEmpty(annotationModel.fieldName)) {
        T act = (T) annotation.getClass().getDeclaredMethod(annotationModel.fieldName).invoke(annotation, null);
        Assert.assertEquals(annotationModel.fieldValue, act);
      }
  }

  private static class AnnotationModel<T> {
    final Class<? extends Annotation> annotationType;
    final String fieldName;
    final T fieldValue;

    private AnnotationModel(Class<? extends Annotation> annotationType, String fieldName, T fieldValue) {
      this.annotationType = annotationType;
      this.fieldName = fieldName;
      this.fieldValue = fieldValue;
    }
  }

}
