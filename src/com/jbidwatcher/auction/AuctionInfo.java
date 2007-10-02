package com.jbidwatcher.auction;
/*
 * Copyright (c) 2000-2007, CyberFOX Software, Inc. All Rights Reserved.
 *
 * Developed by mrs (Morgan Schweers)
 */

/*
 * @brief Generic auction information, generally information that
 * needed to be retrieved.
 *
 * Program-specific information (next update time, what auction
 * server, etc.) is stored in AuctionEntry
 */

import com.jbidwatcher.config.JConfig;
import com.jbidwatcher.util.*;
import com.jbidwatcher.util.db.DBRecord;
import com.jbidwatcher.xml.XMLElement;

import java.io.File;
import java.io.IOException;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public abstract class AuctionInfo extends HashBacked {
  private static Map<String, String> mKeys;

  private static void setupKeys() {
    mKeys = new HashMap<String, String>();
    mKeys.put("startDate", "started_at");
    mKeys.put("start", "started_at");
    mKeys.put("endDate", "ending_at");
    mKeys.put("seller", "seller_id");
    mKeys.put("end", "ending_at");
    mKeys.put("highbidder", "high_bidder");
    mKeys.put("highBidder", "high_bidder");
    mKeys.put("highBidderEmail", "high_bidder_email");
    mKeys.put("itemLocation", "location");
    mKeys.put("numBids", "bid_count");
    mKeys.put("bidcount", "bid_count");
    mKeys.put("insurance_optional", "optional_insurance");
    mKeys.put("insuranceOptional", "optional_insurance");
    mKeys.put("noThumbnail", "no_thumbnail");
    mKeys.put("reserveMet", "reserve_met");
    mKeys.put("isReserve", "reserve");
    mKeys.put("fixed", "fixed_price");
    mKeys.put("fixedPrice", "fixed_price");
    mKeys.put("isDutch", "dutch");
    mKeys.put("reserveMet", "reserve_met");
    mKeys.put("hasThumb", "has_thumbnail");
    mKeys.put("currently", "current_bid");
    mKeys.put("curBid", "current_bid");
    mKeys.put("minimum", "minimum_bid");
    mKeys.put("minBid", "minimum_bid");
    mKeys.put("usprice", "usd_current");
    mKeys.put("us_cur", "usd_current");
    mKeys.put("buy_now_us", "usd_buy_now");
    mKeys.put("buynow", "buy_now");
  }

  static {
    setupKeys();
  }

  protected Seller _seller;

  protected static final sun.misc.BASE64Decoder b64dec = new sun.misc.BASE64Decoder();
  protected static final sun.misc.BASE64Encoder b64enc = new sun.misc.BASE64Encoder();
  protected GZip _loadedPage = null;

  /**
   * @brief Empty constructor, for XML parsing.
   *
   */
  AuctionInfo() {
    setTranslationTable(mKeys);
  }

  /** 
   * @brief Construct a somewhat complete AuctionInfo object with all
   * the important values set.
   * 
   * @param auctionTitle - The title of the auction.
   * @param auctionSeller - The seller's username for the auction.
   * @param auctionHighBidder - The current high bidder, if any.
   * @param auctionCurBid - The current/lowest/starting bid.
   * @param auctionStart - The start time for the auction (if available).
   * @param auctionEnd - The end time for the auction.
   * @param auctionBidCount - The number of bids that have been placed so far.
   */
  AuctionInfo(String auctionTitle, String auctionSeller, String auctionHighBidder,
                     Currency auctionCurBid, Date auctionStart, Date auctionEnd, int auctionBidCount) {
    setTranslationTable(mKeys);
    setTitle(auctionTitle.trim());
    setHighBidder(auctionHighBidder.trim());
    _seller = Seller.makeSeller(auctionSeller.trim());

    setStart(auctionStart);
    setEnd(auctionEnd);

    setCurBid(auctionCurBid);
    setNumBids(auctionBidCount);
  }

  public DBRecord getMap() {
    return getBacking();
  }

  protected String[] infoTags = { "title", "seller", "highbidder", "bidcount", "start", "end",
                                "currently", "dutch", "reserve", "private", "content",
                                "shipping", "insurance", "buynow", "usprice", "fixed", "minimum",
                                "paypal", "location", "feedback", "percentage", "sellerinfo"};
  protected String[] getTags() { return infoTags; }

  protected void handleTag(int i, XMLElement curElement) {
    switch(i) {
      case 0:  //  Title
        if(JConfig.queryConfiguration("savefile.format", "0100").compareTo("0101") >= 0) {
          setString(infoTags[i], curElement.decodeString(curElement.getContents(), 0));
        } else {
          setString(infoTags[i], curElement.getContents());
        }
        break;
      case 1:  //  Seller name
        if(_seller == null) _seller = Seller.makeSeller(curElement.getContents());
        else _seller = _seller.makeSeller(curElement.getContents(), _seller);
        break;
      case 2:  //  High bidder name
      case 18: //  Location of item
        setString(infoTags[i], curElement.getContents());
        break;
      case 3:  //  Bid count
        setInteger(infoTags[i], Integer.parseInt(curElement.getContents()));
        break;
      case 4:  //  Start date
      case 5:  //  End date
        setDate(infoTags[i], new Date(Long.parseLong(curElement.getContents())));
        break;
      case 6:  //  Current price
      case 11: //  Shipping cost
      case 12: //  Insurance cost
      case 13: //  Buy Now price
      case 14: //  Current US price
      case 16: //  Minimum price/bid
        Currency amount = Currency.getCurrency(curElement.getProperty("CURRENCY"), curElement.getProperty("PRICE"));
        setMonetary(infoTags[i], amount);
        if (i == 6) {
          if (amount.getCurrencyType() == Currency.US_DOLLAR) {
            setMonetary("us_cur", amount);
            setString("currency", amount.fullCurrencyName());
          }
          setDefaultCurrency(amount);
        } else if(i == 12) {
          String optional = curElement.getProperty("OPTIONAL");
          setBoolean("insurance_optional", optional == null || (optional.equals("true")));
        }
        break;
      case 7:  //  Is a dutch auction?
      case 8:  //  Is a reserve auction?
      case 9:  //  Is a private auction?
      case 15: //  Fixed price
      case 17: //  PayPal accepted
        setBoolean(infoTags[i], true);
        if(i==7) {
          setInteger("quantity", Integer.parseInt(curElement.getProperty("QUANTITY")));
        } else if(i==8) {
          setBoolean("reserveMet", "true".equals(curElement.getProperty("MET")));
        }
        break;
      case 19: //  Feedback score
        String feedback = curElement.getContents();
        if(_seller == null) _seller = new Seller();
        if(feedback != null) _seller.setFeedback(Integer.parseInt(feedback));
        break;
      case 20: //  Positive feedback percentage (w/o the % sign)
        String percentage = curElement.getContents();
        if (_seller == null) _seller = new Seller();
        _seller.setPositivePercentage(percentage);
        break;
      case 21: //  Seller info block
        _seller = Seller.fromXML(curElement);
        break;
      default:
        break;
        // commented out for FORWARDS compatibility.
        //        throw new RuntimeException("Unexpected value when handling AuctionInfo tags!");
    }
  }

  public XMLElement toXML() {
    XMLElement xmlResult = new XMLElement("info");
    XMLElement xseller, xbidcount, xstart, xend, xdutch, xinsurance;

    addStringChild(xmlResult, "title");

    xseller = _seller.toXML();
    xmlResult.addChild(xseller);

    xstart = new XMLElement("start");
    xstart.setContents(Long.toString(getStart().getTime()));
    xmlResult.addChild(xstart);

    xend = new XMLElement("end");
    xend.setContents(Long.toString(getEnd().getTime()));
    xmlResult.addChild(xend);

    xbidcount = new XMLElement("bidcount");
    xbidcount.setContents(Integer.toString(getNumBids()));
    xmlResult.addChild(xbidcount);

    xinsurance = addCurrencyChild(xmlResult, "insurance");
    if(xinsurance != null) xinsurance.setProperty("optional", isInsuranceOptional() ?"true":"false");

    if(getCurBid().getCurrencyType() != Currency.US_DOLLAR) {
      addCurrencyChild(xmlResult, "usprice");
    }

    addCurrencyChild(xmlResult, "currently");
    addCurrencyChild(xmlResult, "shipping");
    addCurrencyChild(xmlResult, "buynow");
    addCurrencyChild(xmlResult, "minimum");

    xdutch = addBooleanChild(xmlResult, "dutch");
    if(xdutch != null) xdutch.setProperty("quantity", Integer.toString(getQuantity()));

    XMLElement xreserve = addBooleanChild(xmlResult, "reserve");
    if(xreserve != null) xreserve.setProperty("met", isReserveMet() ?"true":"false");

    addBooleanChild(xmlResult, "paypal");
    addBooleanChild(xmlResult, "fixed");
    addBooleanChild(xmlResult, "private");

    addStringChild(xmlResult, "location");
    addStringChild(xmlResult, "highbidder");

    return xmlResult;
  }

  public void setThumbnail(String thumbPath) {
    if(thumbPath == null) setNoThumbnail(true);
    setString("thumbnail", thumbPath);
  }

  protected boolean hasThumbnail() {
    String imgPath = getString("thumbnail");
    boolean saveIfExists = false;

    if(imgPath == null) {
      imgPath = ThumbnailManager.getValidImagePath(this);
      if(imgPath == null) return false;
      saveIfExists = true;
    }

    File tester = new File(imgPath);
    boolean rval= tester.exists();

    if(rval && saveIfExists) setString("thumbnail", imgPath);

    return rval;
  }

  protected String getThumbnail() {
    //  Bad optimization -- BUGBUG -- mrs: 21-March-2004 18:28
    //  If it doesn't have a thumbnail, we check.
    if(!hasThumb()) {
      if(!hasThumbnail()) return null;
    }

    setHasThumb(true);

    return "file:" + getString("thumbnail");
  }

  public void save() {
    String outPath = JConfig.queryConfiguration("auctions.savepath");
    if (outPath != null) {
      if (JConfig.queryConfiguration("store.auctionHTML", "true").equals("true")) {
        String filePath = outPath + System.getProperty("file.separator") + getIdentifier() + ".html.gz";

        if (_loadedPage != null) {
          _loadedPage.save(filePath);
        }
      }
    }
    _loadedPage = null;
  }

  private GZip loadFile(String fileName) {
    File fp = new File(fileName);
    GZip localZip = new GZip();

    if(fp.exists()) {
      //  Okay, I don't allow loading auction data that's over 512K.  Duh.
      if(fp.length() < 512 * 1024) {
        try {
          ErrorManagement.logDebug("Loading from backing page (file is " + fp.length() + " bytes)!");
          localZip.load(fp);
        } catch(IOException ioe) {
          ErrorManagement.handleException("Couldn't read " + fileName, ioe);
          return null;
        }

        return localZip;
      } else {
        ErrorManagement.logDebug("Can't load " + fileName + ", file is too large.");
      }
    }
    return null;
  }

  void setRealContent(StringBuffer changedContent, boolean final_data) {
    if(changedContent != null) {
      byte[] localBytes = changedContent.toString().getBytes();
      setRealContent(localBytes, final_data);
    }
  }

  void setRealContent(byte[] changedContent, boolean final_data) {
    String outPath = JConfig.queryConfiguration("auctions.savepath");

    if(changedContent != null) {
      _loadedPage = new GZip();
      _loadedPage.setData(changedContent);

      if(outPath != null) {
        if(final_data) {
          String filePath = outPath + System.getProperty("file.separator") + getIdentifier() + ".html.gz";
          _loadedPage.save(filePath);
          _loadedPage = null;
        }
      }
    }
  }

  GZip getRealContent() {
    String outPath = JConfig.queryConfiguration("auctions.savepath");
    if(outPath != null) {
      String filePath = outPath + System.getProperty("file.separator") + getIdentifier() + ".html.gz";
      ErrorManagement.logDebug("filePath = " + filePath);
      return loadFile(filePath);
    }
    return _loadedPage;
  }

  public void setContent(StringBuffer inContent, boolean final_data) {
    setRealContent(inContent, final_data);
  }

  protected StringBuffer getContent() {
    StringBuffer sb;

    if(_loadedPage != null) {
      StringBuffer outSB = _loadedPage.getUncompressedData(false);
      if(outSB == null) outSB = new StringBuffer("_loadedPage.getUncompressedData is null");
      sb = outSB;
    } else {
      ErrorManagement.logDebug("_loadedPage is null, returning the 'real' cached copy!");
      GZip gz = getRealContent();
      if(gz != null) {
        sb = gz.getUncompressedData();
        ErrorManagement.logDebug("Turned the uncompressed data into a StringBuffer!");
        if(sb == null) ErrorManagement.logDebug(" Failed to uncompress for id " + getIdentifier());
      } else {
        sb = new StringBuffer("Error getting real content.");
      }
    }
    return(sb);
  }

  public String getIdentifier() { return getString("identifier"); }
  public String getTitle() { return getString("title"); }
  public String getHighBidder() { return getString("highBidder"); }
  public String getHighBidderEmail() { return getString("highBidderEmail"); }
  public String getItemLocation() { return getString("itemLocation", ""); }

  public Currency getCurBid() { return getMonetary("curBid"); }
  public Currency getUSCurBid() {
    if (getCurBid().getCurrencyType() == Currency.US_DOLLAR) {
      return getCurBid();
    }
    return getMonetary("us_cur", Currency.US_DOLLAR);
  }

  public Currency getMinBid() { return getMonetary("minBid", getCurBid()); }
  public Currency getShipping() { return getMonetary("shipping"); }
  public Currency getInsurance() { return getMonetary("insurance"); }
  public Currency getBuyNow() { return getMonetary("buy_now"); }

  public int getQuantity() { return getInteger("quantity"); }
  public int getNumBidders() { return getInteger("numBids"); }
  public int getNumBids() { return getInteger("numBids"); }

  Date getStartDate() { return getDate("start"); }  //  getDate
  Date getEndDate() { return getDate("end"); }      //  getDate

  boolean isDutch() { return getBoolean("isDutch"); }
  boolean isReserve() { return getBoolean("isReserve"); }
  boolean isPrivate() { return getBoolean("isPrivate"); }
  protected boolean isFixedPrice() { return getBoolean("fixed_price"); }
  boolean isReserveMet() { return getBoolean("reserveMet"); }
  boolean isOutbid() { return getBoolean("outbid"); }
  boolean hasPaypal() { return getBoolean("paypal"); }
  boolean hasThumb() { return getBoolean("has_thumbnail"); }
  boolean isInsuranceOptional() { return getBoolean("insurance_optional", true); }
  protected boolean hasNoThumbnail() { return getBoolean("noThumbnail"); }

  public String getSeller() { if (_seller != null) return (_seller.getSeller()); else return "(unknown)"; }
  public String getPositiveFeedbackPercentage() { if (_seller != null) return _seller.getPositivePercentage(); else return "n/a"; }
  int getFeedbackScore() { if(_seller != null) return _seller.getFeedback(); else return 0; }

  public Currency getUSCur() { return getMonetary("us_cur", Currency.US_DOLLAR); }
  public Currency getBuyNowUS() { return getMonetary("buy_now_us", Currency.US_DOLLAR); }

  public Date getStart() { return getDate("start"); }
  public Date getEnd() { return getDate("end"); }

  public void setIdentifier(String id) { setString("identifier", id); }
  protected void setHighBidder(String highBidder) { setString("highBidder", highBidder); }
  protected void setTitle(String title) { setString("title", title); }
  protected void setHighBidderEmail(String highBidderEmail) { setString("highBidderEmail", highBidderEmail); }
  protected void setItemLocation(String itemLocation) { setString("itemLocation", itemLocation); }

  protected void setInsuranceOptional(boolean insuranceOptional) { setBoolean("insuranceOptional", insuranceOptional); }
  protected void setFixedPrice(boolean fixedPrice) { setBoolean("fixedPrice", fixedPrice); }
  protected void setNoThumbnail(boolean noThumbnail) { setBoolean("noThumbnail", noThumbnail); }

  protected void setCurBid(Currency curBid) {       setMonetary("curBid", curBid); }
  protected void setMinBid(Currency minBid) {       setMonetary("minBid", minBid); }
  protected void setShipping(Currency shipping) {   setMonetary("shipping", shipping); }
  protected void setInsurance(Currency insurance) { setMonetary("insurance", insurance); }
  protected void setUSCur(Currency USCur) {         setMonetary("us_cur", USCur); }
  protected void setBuyNowUS(Currency buyNowUS) {   setMonetary("buy_now_us", buyNowUS); }
  protected void setBuyNow(Currency buyNow) {       setMonetary("buy_now", buyNow); }

  protected void setStart(Date start) { setDate("start", start); }
  protected void setEnd(Date end) { setDate("end", end); }

  protected void setQuantity(int quantity) { setInteger("quantity", quantity); }
  protected void setNumBids(int numBids) { setInteger("numBids", numBids); }

  protected void setDutch(boolean dutch) { setBoolean("isDutch", dutch); }
  protected void setReserve(boolean isReserve) { setBoolean("isReserve", isReserve); }
  protected void setPrivate(boolean isPrivate) { setBoolean("private", isPrivate); }
  protected void setReserveMet(boolean reserveMet) { setBoolean("reserveMet", reserveMet); }
  protected void setHasThumb(boolean hasThumb) { setBoolean("hasThumb", hasThumb); }
  protected void setOutbid(boolean outbid) { setBoolean("outbid", outbid); }
  protected void setPaypal(boolean paypal) { setBoolean("paypal", paypal); }

  public abstract ByteBuffer getSiteThumbnail();
  public abstract ByteBuffer getAlternateSiteThumbnail();
}
