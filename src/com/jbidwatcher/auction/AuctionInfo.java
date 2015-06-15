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

import com.jbidwatcher.util.Currency;
import com.jbidwatcher.util.config.*;
import com.jbidwatcher.util.*;
import com.jbidwatcher.util.db.*;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class AuctionInfo extends AuctionCore {
  private String potentialThumbnail = null;

  protected Seller mSeller;
  protected GZip mLoadedPage = null;

  /**
   * @brief Empty constructor, for ActiveRecord.
   *
   */
  public AuctionInfo() { }

  static AuctionInfo findByIdOrIdentifier(String id, String identifier) {
    AuctionInfo ai = null;
    if(id != null) {
      ai = find(id);
    }

    if (ai == null && identifier != null) {
      ai = findByIdentifier(identifier);
    }
    return ai;
  }

  public void setThumbnail(String thumbPath) {
    if(thumbPath == null) setNoThumbnail(true);
    mThumbnailPath = thumbPath;
  }

  public void save() {
    String outPath = JConfig.queryConfiguration("auctions.savepath");
    if (outPath != null && outPath.length() != 0) {
      if (JConfig.queryConfiguration("store.auctionHTML", "true").equals("true")) {
        String filePath = outPath + System.getProperty("file.separator") + getIdentifier() + ".html.gz";

        if (mLoadedPage != null) {
          mLoadedPage.save(filePath);
        }
      }
    }
    mLoadedPage = null;
  }

  private GZip loadFile(File fp) {
    GZip localZip = new GZip();

    if(fp.exists()) {
      //  Okay, I don't allow loading auction data that's over 512K.  Duh.
      if(fp.length() < 512 * 1024) {
        try {
          JConfig.log().logDebug("Loading from backing page (file is " + fp.length() + " bytes)!");
          localZip.load(fp);
        } catch(IOException ioe) {
          JConfig.log().handleException("Couldn't read " + fp.getName(), ioe);
          return null;
        }

        return localZip;
      } else {
        JConfig.log().logDebug("Can't load " + fp.getName() + ", file is too large.");
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
      mLoadedPage = new GZip();
      mLoadedPage.setData(changedContent);

      if(outPath != null && outPath.length() != 0) {
        if(final_data) {
          String filePath = outPath + System.getProperty("file.separator") + getIdentifier() + ".html.gz";
          mLoadedPage.save(filePath);
          mLoadedPage = null;
        }
      }
    }
  }

  GZip getRealContent() {
    File fp = JConfig.getContentFile(getIdentifier());
    if(fp != null) return loadFile(fp);
    return mLoadedPage;
  }

  public void setContent(StringBuffer inContent, boolean final_data) {
    setRealContent(inContent, final_data);
  }

  protected StringBuffer getContent() {
    StringBuffer sb;

    if(mLoadedPage != null) {
      StringBuffer outSB = mLoadedPage.getUncompressedData(false);
      if(outSB == null) outSB = new StringBuffer("mLoadedPage.getUncompressedData is null");
      sb = outSB;
    } else {
      JConfig.log().logDebug("mLoadedPage is null, returning the 'real' cached copy!");
      GZip gz = getRealContent();
      if(gz != null) {
        sb = gz.getUncompressedData();
        JConfig.log().logDebug("Turned the uncompressed data into a StringBuffer!");
        if(sb == null) JConfig.log().logDebug(" Failed to uncompress for id " + getIdentifier());
      } else {
        sb = new StringBuffer("Error getting real content.");
      }
    }
    return(sb);
  }

  public String getSellerName() {
    refreshSeller();
    return mSeller != null ? (mSeller.getSeller()) : "(unknown)";
  }

  private void refreshSeller() {
    if (mSeller == null) {
      String seller_id = get("seller_id");
      if (seller_id != null) mSeller = Seller.findFirstBy("id", seller_id);
    }
  }

  public Seller getSeller() {
    refreshSeller();
    return mSeller;
  }

  public String getPositiveFeedbackPercentage() {
    refreshSeller();
    if (mSeller != null) return mSeller.getPositivePercentage();
    return "n/a";
  }

  public int getFeedbackScore() {
    refreshSeller();
    if (mSeller != null) return mSeller.getFeedback();
    return 0;
  }

  protected void setSellerName(String sellerName) {
    if(sellerName == null || sellerName.length() == 0) return;

    if(mSeller == null) {
      mSeller = Seller.makeSeller(sellerName.trim());
    } else {
      mSeller = mSeller.makeSeller(sellerName, mSeller);
    }
    Integer seller_id = mSeller.getId();
    if(seller_id == null || seller_id == 0) {
      String raw_id = mSeller.saveDB();
      if (raw_id != null && raw_id.length() != 0) seller_id = Integer.parseInt(raw_id);
    }
    setInteger("seller_id", seller_id);
  }

  public void setIdentifier(String id) { setString("identifier", id); }
  public void setHighBidder(String highBidder) { setString("highBidder", highBidder); }
  protected void setTitle(String title) { setString("title", title); }
//  protected void setHighBidderEmail(String highBidderEmail) { setString("highBidderEmail", highBidderEmail); }
  protected void setItemLocation(String itemLocation) { setString("itemLocation", itemLocation); }

  protected void setInsuranceOptional(boolean insuranceOptional) { setBoolean("insuranceOptional", insuranceOptional); }
  protected void setFixedPrice(boolean fixedPrice) { setBoolean("fixedPrice", fixedPrice); }
  protected void setNoThumbnail(boolean noThumbnail) { setBoolean("noThumbnail", noThumbnail); }

  protected void setCurBid(Currency curBid) {       setMonetary("curBid", curBid); }
  protected void setMinBid(Currency minBid) {       setMonetary("minBid", minBid); }
  protected void setShipping(Currency shipping) {   setMonetary("shipping", shipping); }
  protected void setInsurance(Currency insurance) { setMonetary("insurance", insurance); }
  protected void setUSCur(Currency USCur) {         setMonetary("us_cur", USCur, false); }
  protected void setBuyNowUS(Currency buyNowUS) {   setMonetary("buy_now_us", buyNowUS, false); }
  protected void setBuyNow(Currency buyNow) {       setMonetary("buy_now", buyNow); }

  protected void setEnd(Date end) {
    if(end == null || end.equals(Constants.FAR_FUTURE)) {
      end = null;
    }
    setDate("end", end);
  }

  public void setNumBids(int numBids) { setInteger("numBids", numBids); }
  public void setPrivate(boolean isPrivate) { setBoolean("private", isPrivate); }

  protected void setPaypal(boolean paypal) { setBoolean("paypal", paypal); }
  protected void setEnded(boolean ended) { setBoolean("ended", ended); }

  public void setThumbnailURL(String url) {
    setNoThumbnail(false);
    potentialThumbnail = url;
  }

  public String getThumbnailURL() {
    if (potentialThumbnail != null) return potentialThumbnail;
    return getThumbnailById(getIdentifier());
  }

  public String getAlternateSiteThumbnail() {
    return getThumbnailById(getIdentifier() + "6464");
  }

  private static String getThumbnailById(String id) {
    return "http://thumbs.ebaystatic.com/pict/" + id + ".jpg";
  }

  private static Table sDB = null;
  protected static String getTableName() { return "auctions"; }
  protected Table getDatabase() { return getRealDatabase(); }
  private static Table getRealDatabase() {
    if (sDB == null) {
      sDB = openDB(getTableName());
    }
    return sDB;
  }

  public String saveDB() {
    //  Look for columns of type: {foo}_id
    //  For each of those, introspect for 'm{Foo}'.
    //  For each non-null of those, call 'saveDB' on it.
    //  Store the result of that call as '{foo}_id'.
    if(mSeller != null) {
      String seller_id = mSeller.saveDB();
      if(seller_id != null) set("seller_id", seller_id);
    }

    return super.saveDB();
  }

  public static AuctionInfo find(String id) {
    return findFirstBy("id", id);
  }

  public static AuctionInfo findFirstBy(String key, String value) {
    return (AuctionInfo) ActiveRecord.findFirstBy(AuctionInfo.class, key, value);
  }

  public static AuctionInfo findByIdentifier(String identifier) {
    return (AuctionInfo) ActiveRecord.findFirstBySQL(AuctionInfo.class, "SELECT * FROM auctions WHERE id IN (SELECT Max(id) FROM auctions WHERE identifier = '" + identifier + "')");
  }

  public static int count() {
    return ActiveRecord.count(AuctionInfo.class);
  }

  public static int uniqueCount() {
    return getRealDatabase().countBySQL("SELECT COUNT(DISTINCT(identifier)) FROM auctions WHERE identifier IS NOT NULL");
  }

  public static boolean deleteAll(List<Integer> toDelete) {
    if(toDelete.isEmpty()) return true;
    //  TODO - Replace with Guava?
    String auctions = buildCSL(toDelete);

    return getRealDatabase().deleteBy("id IN (" + auctions + ")");
  }

  public static List<AuctionInfo> findAllByIds(List<? extends Object> toFind) {
    if(toFind.isEmpty()) return new ArrayList<AuctionInfo>(0);

    String auctions = buildCSL(toFind);

    return (List<AuctionInfo>) ActiveRecord.findAllBySQL(AuctionInfo.class, "SELECT * FROM auctions WHERE id IN (" + auctions + ")");
  }

  private static String buildCSL(List<? extends Object> toDelete) {
    StringBuilder ids = new StringBuilder("");
    boolean first = true;
    for (Object id : toDelete) {
      if (!first) {
        ids.append(", ");
      }
      ids.append(id);
      first = false;
    }

    return ids.toString();
  }

  @SuppressWarnings({"unchecked"})
  public static List<AuctionInfo> findLostAuctions() {
    List<AuctionInfo> resultSet;
    try {
      resultSet = (List<AuctionInfo>) findAllBySQL(AuctionInfo.class, "SELECT * FROM auctions WHERE identifier NOT IN (SELECT DISTINCT(identifier) FROM entries)");
    } catch(Exception e) {
      JConfig.log().handleDebugException("Failed to find lost auctions.", e);
      resultSet = null;
    }
    return resultSet;
  }
}
