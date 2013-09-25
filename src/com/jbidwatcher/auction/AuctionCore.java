package com.jbidwatcher.auction;

import com.jbidwatcher.util.Constants;
import com.jbidwatcher.util.Currency;
import com.jbidwatcher.util.db.ActiveRecord;

import java.io.File;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * User: mrs
 * Date: 3/9/13
 * Time: 6:32 PM
 *
 * This contains the core auction information.
 */
public abstract class AuctionCore extends ActiveRecord {
  private static Map<String, String> mKeys;
  protected String mThumbnailPath;

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

  public AuctionCore() {
    setTranslationTable(mKeys);
  }

  public String getIdentifier() { return getString("identifier"); }

  public String getTitle() { return getString("title"); }

  public String getHighBidder() { return getString("highBidder"); }

  //  public String getHighBidderEmail() { return getString("highBidderEmail"); }
  public String getItemLocation() { return getString("itemLocation", ""); }

  public boolean isComplete() { return getBoolean("ended"); }

  public Currency getBestPrice() {
    Currency currentPrice = getCurBid();
    if (currentPrice == null || currentPrice.isNull()) {
      currentPrice = getBuyNow();
    }
    return currentPrice;
  }

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

  public int getQuantity() { return getInteger("quantity", 1); }

  public int getNumBidders() { return getInteger("numBids", 0); }

  public int getNumBids() { return getNumBidders(); }

  public Date getStartDate() { return getDate("start"); }

  public Date getEndDate() {
    Date end = getEnd();
    if(end == null) end = Constants.FAR_FUTURE;
    return end;
  }

  public boolean isReserve() { return getBoolean("isReserve"); }

  public boolean isPrivate() { return getBoolean("isPrivate"); }

  public boolean isFixed() { return getBoolean("fixed_price"); }

  public boolean isReserveMet() { return getBoolean("reserve_met"); }

  public boolean hasPaypal() { return getBoolean("paypal"); }

  boolean isInsuranceOptional() { return getBoolean("insurance_optional", true); }

  protected boolean hasNoThumbnail() { return getBoolean("noThumbnail"); }

  public Currency getUSCur() { return getMonetary("us_cur", Currency.US_DOLLAR); }

  public Currency getBuyNowUS() { return getMonetary("buy_now_us", Currency.US_DOLLAR); }

  public Date getStart() { return getDate("start"); }

  public Date getEnd() { return getDate("end"); }

  public String getSellerId() { return get("seller_id"); }

  private boolean hasThumb() { return getBoolean("has_thumbnail"); }

  private void setHasThumb(boolean hasThumb) { setBoolean("has_thumbnail", hasThumb); }

  protected boolean hasThumbnail() {
    String imgPath = mThumbnailPath;

    if(imgPath == null) {
      imgPath = Thumbnail.getValidImagePath(getIdentifier());
      if(imgPath == null) return false;
    }

    File tester = new File(imgPath);
    boolean rval= tester.exists();

    if(rval && mThumbnailPath == null) mThumbnailPath = imgPath;

    return rval;
  }

  public String getThumbnail() {
    //  Bad optimization -- BUGBUG -- mrs: 21-March-2004 18:28
    //  If it doesn't have a thumbnail, we check.
    if(!hasThumb() || mThumbnailPath == null) {
      if(!hasThumbnail()) return null;
    }

    setHasThumb(true);

    return "file:" + mThumbnailPath;
  }
}
