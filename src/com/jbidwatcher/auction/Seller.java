package com.jbidwatcher.auction;

import com.jbidwatcher.util.db.AuctionDB;
import com.jbidwatcher.xml.XMLElement;

/**
 * User: Morgan
 * Date: Sep 29, 2007
 * Time: 7:27:30 PM
 * To change this template use File | Settings | File Templates.
 */
public class Seller extends ActiveRecord {
  public Seller() { }

  public String getSeller() { return getString("seller"); }
  public void setSeller(String name) { setString("seller", name); }
  public String getPositivePercentage() { return getString("feedback_percentage"); }
  public void setPositivePercentage(String positivePercentage) { setString("feedback_percentage", positivePercentage); }
  public int getFeedback() { return getInteger("feedback", 0); }
  public void setFeedback(int feedback) { setInteger("feedback", feedback); }

  public XMLElement toXML() {
    XMLElement xmlResult = new XMLElement("seller");
    XMLElement xseller = new XMLElement("name");
    XMLElement xfeedback = new XMLElement("feedback");
    XMLElement xpercentage = new XMLElement("feedback_percent");
    xseller.setContents(getSeller());
    xmlResult.addChild(xseller);

    xfeedback.setContents(Integer.toString(getFeedback()));
    xmlResult.addChild(xfeedback);

    xpercentage.setContents(getPositivePercentage());
    xmlResult.addChild(xpercentage);

    return xmlResult;
  }

  public static Seller newFromXML(XMLElement curElement) {
    String seller = curElement.getChild("seller").getContents();
    if(seller == null || seller.length() == 0) return null;

    try {
      Seller rval = new Seller();
      rval.setSeller(curElement.getChild("seller").getContents());
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
    rval.setPositivePercentage(oldSeller.getPositivePercentage());

    rval.saveDB();
    return rval;
  }

  /*************************/
  /* Database access stuff */
  /*************************/

  private static AuctionDB sDB;

  protected static String getTableName() { return "sellers"; }

  protected AuctionDB getDatabase() {
    if (sDB == null) {
      sDB = openDB(getTableName());
    }
    return sDB;
  }

  public static Seller findFirstBy(String key, String value) {
    return (Seller) ActiveRecord.findFirstBy(Seller.class, key, value);
  }
}
