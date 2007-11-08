package com.jbidwatcher.auction;

import com.jbidwatcher.util.ErrorManagement;
import com.jbidwatcher.util.db.AuctionDB;
import com.jbidwatcher.util.db.DBRecord;
import com.jbidwatcher.xml.XMLElement;

import java.util.HashMap;
import java.util.Map;

/**
 * User: Morgan
 * Date: Sep 29, 2007
 * Time: 7:27:30 PM
 * To change this template use File | Settings | File Templates.
 */
public class Seller {
  static Map<String, Seller> mSellerMap = new HashMap<String,Seller>();
  String mSeller;
  String mPositivePercentage;
  int mFeedback;
  private Integer mId;
  private static AuctionDB sDB = null;

  public Seller() {
    establishSellerDatabase();
  }

  private Seller(DBRecord record) {
    mSeller = record.get("seller");
    try { mFeedback = Integer.parseInt(record.get("feedback")); } catch(Exception e) { mFeedback = 0; }
    mPositivePercentage = record.get("feedback_percentage");
  }

  private static void establishSellerDatabase() {
    if (sDB == null) {
      try {
        sDB = new AuctionDB("sellers");
      } catch (Exception e) {
        ErrorManagement.handleException("Can't access the sellers table.", e);
      }
    }
  }

  public static Seller makeSeller(String sellerName) {
    establishSellerDatabase();
    Seller existing_seller = mSellerMap.get(sellerName);
    if(existing_seller == null) {
      DBRecord existing = sDB.findByColumn("seller", sellerName);
      Seller rval;

      if(existing != null) {
        rval = new Seller(existing);
      } else {
        //  This should look up the seller in the database, so there's only one instance of any given seller.
        rval = new Seller();
        rval.setSeller(sellerName);
      }

      mSellerMap.put(sellerName, rval);
      return rval;
    }

    return existing_seller;
  }

  public Integer getId() {
    if(mId == null) {
      create();
    }
    return mId;
  }

  private void create() {
    String id = sDB.storeMap(getMap());
    if(id != null && id.length() != 0) {
      mId = Integer.parseInt(id);
    } else {
      mId = null;
    }
  }

  public DBRecord getMap() {
    DBRecord values = new DBRecord();

    values.put("seller", mSeller);
    values.put("feedback", Integer.toString(mFeedback));
    values.put("feedback_percentage", mPositivePercentage);

    return values;
  }

  public String getSeller() {
    return mSeller;
  }

  public void setSeller(String seller) {
    mSeller = seller;
  }

  public String getPositivePercentage() {
    return mPositivePercentage;
  }

  public void setPositivePercentage(String positivePercentage) {
    mPositivePercentage = positivePercentage;
  }

  public int getFeedback() {
    return mFeedback;
  }

  public void setFeedback(int feedback) {
    mFeedback = feedback;
  }

  public XMLElement toXML() {
    XMLElement xmlResult = new XMLElement("seller");
    XMLElement xseller = new XMLElement("name");
    XMLElement xfeedback = new XMLElement("feedback");
    XMLElement xpercentage = new XMLElement("feedback_percent");
    xseller.setContents(mSeller);
    xmlResult.addChild(xseller);

    xfeedback.setContents(Integer.toString(mFeedback));
    xmlResult.addChild(xfeedback);

    xpercentage.setContents(mPositivePercentage);
    xmlResult.addChild(xpercentage);

    return xmlResult;
  }

  public static Seller fromXML(XMLElement curElement) {
    establishSellerDatabase();
    try {
      Seller rval = new Seller();
      rval.setSeller(curElement.getChild("seller").getContents());
      rval.setFeedback(Integer.parseInt(curElement.getChild("feedback").getContents()));
      rval.setPositivePercentage(curElement.getChild("feedback_percent").getContents());

      return rval;
    } catch(Exception e) {
      return null;
    }
  }

  public Seller makeSeller(String sellerName, Seller oldSeller) {
    Seller rval = makeSeller(sellerName);
    rval.setFeedback(oldSeller.mFeedback);
    rval.setPositivePercentage(oldSeller.mPositivePercentage);

    return rval;
  }
}
