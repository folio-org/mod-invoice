package org.folio.utils;

import lombok.experimental.UtilityClass;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang.time.DateUtils;
import org.apache.commons.lang3.StringUtils;
import org.folio.rest.jaxrs.model.InvoiceLine;

import java.util.Date;
import java.util.Objects;

@Log4j2
@UtilityClass
public class InvoiceLineUtils {

  /**
   * Checks if any invoice line fields (tags, subscription info/dates, comment) have changed.
   * Returns true if changes detected, false if all fields are identical.
   */
  public boolean isIgnoreMissingBudgets(InvoiceLine storage, InvoiceLine request) {
    var hasTagsAdded = Objects.isNull(storage.getTags())
      && Objects.nonNull(request.getTags())
      && !CollectionUtils.sizeIsEmpty(request.getTags().getTagList());

    var hasTagsRemoved = Objects.isNull(request.getTags())
      && Objects.nonNull(storage.getTags())
      && !CollectionUtils.sizeIsEmpty(storage.getTags().getTagList());

    var hasTagsChanged = Objects.nonNull(storage.getTags())
      && Objects.nonNull(request.getTags())
      && !CollectionUtils.isEqualCollection(storage.getTags().getTagList(), request.getTags().getTagList());

    var hasSubInfoChanged = !StringUtils.equals(storage.getSubscriptionInfo(), request.getSubscriptionInfo());
    var hasSubStartChanged = areDatesNotEqual(storage.getSubscriptionStart(), request.getSubscriptionStart());
    var hasSubEndChanged = areDatesNotEqual(storage.getSubscriptionEnd(), request.getSubscriptionEnd());

    var hasCommentChanged = !StringUtils.equals(storage.getComment(), request.getComment());

    log.info("getIgnoreMissingBudgets:: has subInfoChanged={}, subStartChanged={}, subEndChanged={}, commentChanged={}, tagsAdded={}, tagsRemoved={}, tagsChanged={}",
      hasSubInfoChanged, hasSubStartChanged, hasSubEndChanged, hasCommentChanged, hasTagsAdded, hasTagsRemoved, hasTagsChanged);
    return hasSubInfoChanged
      || hasSubStartChanged
      || hasSubEndChanged
      || hasCommentChanged
      || hasTagsAdded
      || hasTagsRemoved
      || hasTagsChanged;
  }

  /**
   * Checks if two dates are different, considering null values.
   * Returns false if both dates are the same instant (or both null), true if they differ.
   *
   * @param date1 first date to compare
   * @param date2 second date to compare
   * @return true if dates are different (or one is null and other is not), false if both are equal or both null
   */
  private static boolean areDatesNotEqual(Date date1, Date date2) {
    if (Objects.isNull(date1) && Objects.isNull(date2)) {
      return false;
    }
    if (Objects.isNull(date1) || Objects.isNull(date2)) {
      return true;
    }
    return !DateUtils.isSameInstant(date1, date2);
  }
}
