package com.jbidwatcher.auction;

import com.jbidwatcher.util.db.*;
import com.jbidwatcher.util.db.ActiveRecord;
import com.jbidwatcher.util.xml.XMLElement;

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

  @SuppressWarnings({"RefusedBequest"})
  public XMLElement toXML() {
    XMLElement xmlResult = new XMLElement("seller");
    XMLElement xseller = new XMLElement("name");
    XMLElement xfeedback = new XMLElement("feedback");
    XMLElement xpercentage = new XMLElement("feedback_percent");
    xseller.setContents(getSeller());
    xmlResult.addChild(xseller);

    xfeedback.setContents(Integer.toString(getFeedback()));
    xmlResult.addChild(xfeedback);

    String fp = getString("feedback_percentage");
    if(fp == null || fp.equals("100.00")) fp = "100";
    int decimal = fp.lastIndexOf('.');
    if(decimal != -1) {
      if(fp.substring(decimal+1).length() == 1) fp = fp + "0";
    }
    xpercentage.setContents(fp);
    xmlResult.addChild(xpercentage);

    return xmlResult;
  }

  public static Seller newFromXML(XMLElement curElement) {
    String seller = curElement.getChild("name").getContents();
    if(seller == null || seller.length() == 0) return null;

    try {
      Seller rval = new Seller();
      rval.setSeller(seller);
      rval.setFeedback(Integer.parseInt(curElement.getChild("feedback").getContents()));
      rval.setPositivePercentage(curElement.getChild("feedback_percent").getContents());

      rval.saveDB();
      return rval;
    } catch(Exception e) {
      return null;
    }
  }

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
