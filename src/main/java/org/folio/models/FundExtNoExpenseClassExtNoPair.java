package org.folio.models;

import static org.apache.commons.lang3.StringUtils.EMPTY;

import java.util.Objects;

public class FundExtNoExpenseClassExtNoPair {
  public static final String DASH = "-";

  private final String fundExtNo;
  private final String expenseClassExtNo;

  public FundExtNoExpenseClassExtNoPair(String fundExtNo, String expenseClassExtNo) {
    this.fundExtNo = fundExtNo;
    this.expenseClassExtNo = expenseClassExtNo;
  }

  public String getFundExtNo() {
    return fundExtNo;
  }

  public String getExpenseClassExtNo() {
    return expenseClassExtNo;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    FundExtNoExpenseClassExtNoPair that = (FundExtNoExpenseClassExtNoPair) o;
    return fundExtNo.equals(that.fundExtNo) &&
      expenseClassExtNo.equals(that.expenseClassExtNo);
  }

  @Override
  public int hashCode() {
    return Objects.hash(fundExtNo, expenseClassExtNo);
  }

  @Override
  public String toString(){
    return EMPTY.equals(expenseClassExtNo) ? fundExtNo : fundExtNo + DASH + expenseClassExtNo;
  }
}
