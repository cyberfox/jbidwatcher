package com.jbidwatcher.auction;

import com.jbidwatcher.util.db.*;
import com.jbidwatcher.util.db.ActiveRecord;

import java.text.NumberFormat;

/**
 * User: Morgan
 * Date: Sep 29, 2007
 * Time: 7:27:30 PM
 * To change this template use File | Settings | File Templates.
 */
public class Seller extends ActiveRecord
{
  public String getSeller() { return getString("seller"); }
  private void setSeller(String name) { setString("seller", name); }

  private static NumberFormat decimalPercentage = null;
  public String getPositivePercentage() {
    if(decimalPercentage == null) {
      decimalPercentage = NumberFormat.getPercentInstance();
      decimalPercentage.setMinimumFractionDigits(1);
      decimalPercentage.setMaximumFractionDigits(1);
    }
    String feedbackPercent = getString("feedback_percentage");
    if(feedbackPercent != null) {
      Double x = Double.parseDouble(feedbackPercent);
      return decimalPercentage.format(x/100.0);
    } else {
      return "n/a";
    }
  }
  public void setPositivePercentage(String positivePercentage) {
    setString("feedback_percentage", positivePercentage.replaceAll(",", ".").replaceFirst("%", ""));
    saveDB();
  }
  public int getFeedback() { return getInteger("feedback", 0); }
  public void setFeedback(int feedback) { setInteger("feedback", feedback); saveDB(); }

  public static Seller makeSeller(String sellerName) {
    if(sellerName == null) return null;

    Seller existing_seller = findFirstBy("seller", sellerName);
    if (existing_seller == null) {
      existing_seller = new Seller();
      existing_seller.setSeller(sellerName);
      existing_seller.saveDB();
    }

    return existing_seller;
  }

  public Seller makeSeller(String sellerName, Seller oldSeller) {
    Seller rval = makeSeller(sellerName);
    rval.setFeedback(oldSeller.getFeedback());
    rval.setPositivePercentage(oldSeller.getString("feedback_percentage"));

    rval.saveDB();
    return rval;
  }

  /*************************/
  /* Database access stuff */
  /*************************/

  private static Table sDB = null;

  protected static String getTableName() { return "sellers"; }

  protected Table getDatabase() {
    if (sDB == null) {
      sDB = openDB(getTableName());
    }
    return sDB;
  }

  public static Seller findFirstBy(String key, String value) {
    return (Seller) ActiveRecord.findFirstBy(Seller.class, key, value);
  }
}
