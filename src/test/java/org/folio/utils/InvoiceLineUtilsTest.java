package org.folio.utils;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.Date;
import java.util.List;

import org.folio.CopilotGenerated;
import org.folio.rest.jaxrs.model.InvoiceLine;
import org.folio.rest.jaxrs.model.Tags;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@CopilotGenerated(model = "Claude Haiku 4.5")
public class InvoiceLineUtilsTest {

  // ============ TAGS TESTS ============

  @ParameterizedTest(name = "Tags: {0}")
  @CsvSource({
    "added with non-empty,,tag1|tag2,true",
    "added but empty,,EMPTY,false",
    "removed with non-empty,tag1|tag2,,true",
    "removed but storage empty,EMPTY,,false",
    "changed different,tag1|tag2,tag1|tag3,true",
    "replaced with empty,tag1|tag2,EMPTY,true",
    "empty replaced with non-empty,EMPTY,tag1|tag2,true",
    "same,tag1|tag2,tag1|tag2,false",
    "both null,null,null,false"
  })
  @DisplayName("Should handle various tag scenarios")
  void shouldHandleTagScenarios(String scenario, String storageTagsStr, String requestTagsStr, boolean expected) {
    var storageTags = (Tags) null;
    var requestTags = (Tags) null;

    if ("EMPTY".equals(storageTagsStr)) {
      storageTags = new Tags().withTagList(Collections.emptyList());
    } else if (storageTagsStr != null) {
      storageTags = new Tags().withTagList(List.of(storageTagsStr.split("\\|")));
    }
    if ("EMPTY".equals(requestTagsStr)) {
      requestTags = new Tags().withTagList(Collections.emptyList());
    } else if (requestTagsStr != null) {
      requestTags = new Tags().withTagList(List.of(requestTagsStr.split("\\|")));
    }

    var storage = new InvoiceLine().withTags(storageTags);
    var request = new InvoiceLine().withTags(requestTags);
    if (expected) {
      assertTrue(InvoiceLineUtils.isIgnoreMissingBudgets(storage, request), "Failed for: " + scenario);
    } else {
      assertFalse(InvoiceLineUtils.isIgnoreMissingBudgets(storage, request), "Failed for: " + scenario);
    }
  }

  @Test
  @DisplayName("Should return false when both tags are null")
  void shouldReturnFalseWhenBothTagsAreNull() {
    var storage = new InvoiceLine().withTags(null);
    var request = new InvoiceLine().withTags(null);

    assertFalse(InvoiceLineUtils.isIgnoreMissingBudgets(storage, request));
  }

  // ============ SUBSCRIPTION INFO TESTS ============

  @ParameterizedTest(name = "Subscription Info: {0}")
  @CsvSource({
    "changed,info1,info2,true",
    "added,null,info1,true",
    "removed,info1,null,true",
    "same,info1,info1,false",
    "both null,null,null,false"
  })
  @DisplayName("Should handle various subscription info scenarios")
  void shouldHandleSubscriptionInfoScenarios(String scenario, String storageInfo, String requestInfo, boolean expected) {
    var storage = new InvoiceLine().withSubscriptionInfo("null".equals(storageInfo) ? null : storageInfo);
    var request = new InvoiceLine().withSubscriptionInfo("null".equals(requestInfo) ? null : requestInfo);

    if (expected) {
      assertTrue(InvoiceLineUtils.isIgnoreMissingBudgets(storage, request), "Failed for: " + scenario);
    } else {
      assertFalse(InvoiceLineUtils.isIgnoreMissingBudgets(storage, request), "Failed for: " + scenario);
    }
  }

  // ============ SUBSCRIPTION START DATE TESTS ============

  @Test
  @DisplayName("Should return true when subscription start date added (storage=null, request=non-null)")
  void shouldReturnTrueWhenSubscriptionStartDateAdded() {
    var now = new Date();
    var storage = new InvoiceLine().withSubscriptionStart(null);
    var request = new InvoiceLine().withSubscriptionStart(now);

    assertTrue(InvoiceLineUtils.isIgnoreMissingBudgets(storage, request));
  }

  @Test
  @DisplayName("Should return true when subscription start date changed")
  void shouldReturnTrueWhenSubscriptionStartDateChanged() {
    var date1 = new Date();
    var date2 = new Date(date1.getTime() + 86400000); // Add 1 day in milliseconds
    var storage = new InvoiceLine().withSubscriptionStart(date1);
    var request = new InvoiceLine().withSubscriptionStart(date2);

    assertTrue(InvoiceLineUtils.isIgnoreMissingBudgets(storage, request));
  }

  @Test
  @DisplayName("Should return false when subscription start date is same")
  void shouldReturnFalseWhenSubscriptionStartDateSame() {
    var now = new Date();
    var storage = new InvoiceLine().withSubscriptionStart(now);
    var request = new InvoiceLine().withSubscriptionStart(now);

    assertFalse(InvoiceLineUtils.isIgnoreMissingBudgets(storage, request));
  }

  @Test
  @DisplayName("Should return false when both subscription start dates are null")
  void shouldReturnFalseWhenBothSubscriptionStartDatesNull() {
    var storage = new InvoiceLine().withSubscriptionStart(null);
    var request = new InvoiceLine().withSubscriptionStart(null);

    assertFalse(InvoiceLineUtils.isIgnoreMissingBudgets(storage, request));
  }

  @Test
  @DisplayName("Should return true when subscription start date removed (storage=non-null, request=null)")
  void shouldReturnTrueWhenSubscriptionStartDateRemoved() {
    var now = new Date();
    var storage = new InvoiceLine().withSubscriptionStart(now);
    var request = new InvoiceLine().withSubscriptionStart(null);

    assertTrue(InvoiceLineUtils.isIgnoreMissingBudgets(storage, request));
  }

  // ============ SUBSCRIPTION END DATE TESTS ============

  @Test
  @DisplayName("Should return true when subscription end date added (storage=null, request=non-null)")
  void shouldReturnTrueWhenSubscriptionEndDateAdded() {
    var now = new Date();
    var storage = new InvoiceLine().withSubscriptionEnd(null);
    var request = new InvoiceLine().withSubscriptionEnd(now);

    assertTrue(InvoiceLineUtils.isIgnoreMissingBudgets(storage, request));
  }

  @Test
  @DisplayName("Should return true when subscription end date changed")
  void shouldReturnTrueWhenSubscriptionEndDateChanged() {
    var date1 = new Date();
    var date2 = new Date(date1.getTime() + 86400000); // Add 1 day in milliseconds
    var storage = new InvoiceLine().withSubscriptionEnd(date1);
    var request = new InvoiceLine().withSubscriptionEnd(date2);

    assertTrue(InvoiceLineUtils.isIgnoreMissingBudgets(storage, request));
  }

  @Test
  @DisplayName("Should return false when subscription end date is same")
  void shouldReturnFalseWhenSubscriptionEndDateSame() {
    var now = new Date();
    var storage = new InvoiceLine().withSubscriptionEnd(now);
    var request = new InvoiceLine().withSubscriptionEnd(now);

    assertFalse(InvoiceLineUtils.isIgnoreMissingBudgets(storage, request));
  }

  @Test
  @DisplayName("Should return false when both subscription end dates are null")
  void shouldReturnFalseWhenBothSubscriptionEndDatesNull() {
    var storage = new InvoiceLine().withSubscriptionEnd(null);
    var request = new InvoiceLine().withSubscriptionEnd(null);

    assertFalse(InvoiceLineUtils.isIgnoreMissingBudgets(storage, request));
  }

  @Test
  @DisplayName("Should return true when subscription end date removed (storage=non-null, request=null)")
  void shouldReturnTrueWhenSubscriptionEndDateRemoved() {
    var now = new Date();
    var storage = new InvoiceLine().withSubscriptionEnd(now);
    var request = new InvoiceLine().withSubscriptionEnd(null);

    assertTrue(InvoiceLineUtils.isIgnoreMissingBudgets(storage, request));
  }

  // ============ COMMENT TESTS ============

  @ParameterizedTest(name = "Comment: {0}")
  @CsvSource({
    "changed,comment1,comment2,true",
    "added,null,comment1,true",
    "removed,comment1,null,true",
    "same,comment1,comment1,false",
    "both null,null,null,false"
  })
  @DisplayName("Should handle various comment scenarios")
  void shouldHandleCommentScenarios(String scenario, String storageComment, String requestComment, boolean expected) {
    var storage = new InvoiceLine().withComment("null".equals(storageComment) ? null : storageComment);
    var request = new InvoiceLine().withComment("null".equals(requestComment) ? null : requestComment);

    if (expected) {
      assertTrue(InvoiceLineUtils.isIgnoreMissingBudgets(storage, request), "Failed for: " + scenario);
    } else {
      assertFalse(InvoiceLineUtils.isIgnoreMissingBudgets(storage, request), "Failed for: " + scenario);
    }
  }

  // ============ COMPREHENSIVE TESTS ============

  @Test
  @DisplayName("Should return false when no fields changed")
  void shouldReturnFalseWhenNoFieldsChanged() {
    var now = new Date();
    var storage = new InvoiceLine()
      .withTags(new Tags().withTagList(List.of("tag1")))
      .withSubscriptionInfo("info")
      .withSubscriptionStart(now)
      .withSubscriptionEnd(now)
      .withComment("comment");
    var request = new InvoiceLine()
      .withTags(new Tags().withTagList(List.of("tag1")))
      .withSubscriptionInfo("info")
      .withSubscriptionStart(now)
      .withSubscriptionEnd(now)
      .withComment("comment");

    assertFalse(InvoiceLineUtils.isIgnoreMissingBudgets(storage, request));
  }

  @Test
  @DisplayName("Should return true when multiple fields changed")
  void shouldReturnTrueWhenMultipleFieldsChanged() {
    var date1 = new Date();
    var date2 = new Date(date1.getTime() + 86400000); // Add 1 day in milliseconds

    var storage = new InvoiceLine()
      .withTags(new Tags().withTagList(List.of("tag1")))
      .withSubscriptionInfo("info1")
      .withSubscriptionStart(date1)
      .withSubscriptionEnd(date1)
      .withComment("comment1");
    var request = new InvoiceLine()
      .withTags(new Tags().withTagList(List.of("tag2")))
      .withSubscriptionInfo("info2")
      .withSubscriptionStart(date2)
      .withSubscriptionEnd(date2)
      .withComment("comment2");

    assertTrue(InvoiceLineUtils.isIgnoreMissingBudgets(storage, request));
  }
}

