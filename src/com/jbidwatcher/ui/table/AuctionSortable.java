package com.jbidwatcher.ui.table;

import com.jbidwatcher.auction.AuctionEntry;
import com.jbidwatcher.auction.Seller;
import com.jbidwatcher.util.Currency;
import com.jbidwatcher.util.config.JConfig;

import java.util.HashMap;
import java.util.Map;

/**
* Created by mschweers on 8/1/14.
*/
class AuctionSortable {
  private AuctionEntry entry;

  private static Map<String, Seller> sellers = new HashMap<String, Seller>();

  private Integer Zero = 0;

  public AuctionSortable(AuctionEntry ae) { entry = ae; }


  /** Utility methods **/
  private Seller getSeller(String sellerId) {
    Seller seller;
    if(sellers.containsKey(sellerId)) {
      seller = sellers.get(sellerId);
    } else {
      seller = Seller.findFirstBy("id", sellerId);
    }
    return seller;
  }

  private int safeConvert(String feedbackPercent)
  {
    int rval;
    try {
      rval = (int) (Double.parseDouble(feedbackPercent) * 10.0);
    } catch (NumberFormatException e) {
      rval = 0;
    }
    return rval;
  }

  private Currency getMaxOrSnipe(AuctionEntry aEntry) {
    if(aEntry.isSniped()) {
      return aEntry.getSnipeAmount();
    }
    if(aEntry.isBidOn()) {
      return aEntry.getBid();
    }
    if(aEntry.snipeCancelled() && aEntry.isComplete()) {
      return aEntry.getCancelledSnipe();
    }
    return Currency.NoValue();
  }


  /** Key methods **/
  public String getId() { return entry.getIdentifier(); }
  public Currency getCurrentBid() {
    Currency rval = entry.getUSCurBid();
    if(rval.getValue() == 0.0 && rval.getCurrencyType() == Currency.US_DOLLAR) {
      return entry.getCurrentUSPrice();
    }
    return rval;
  }

  public Currency getSnipeOrMax() { return Currency.convertToUSD(entry.getCurrentUSPrice(), entry.getCurrentPrice(), getMaxOrSnipe(entry)); }
  public Currency getFixedPrice() { return Currency.convertToUSD(entry.getCurrentUSPrice(), entry.getCurrentPrice(), entry.getBuyNow()); }

  public Currency getShippingInsurance() {
    Currency si = (!entry.getShipping().isNull())?entry.getShippingWithInsurance(): Currency.NoValue();
    //  This is crack.  I'm insane to even think about doing this, but it works...
    return Currency.convertToUSD(entry.getCurrentUSPrice(), entry.getCurrentPrice(), si);
  }

  public Currency getMax() {
    Currency bid = entry.isBidOn()?entry.getBid(): Currency.NoValue();
    return Currency.convertToUSD(entry.getCurrentUSPrice(), entry.getCurrentPrice(), bid);
  }

  public Currency getSnipe() {
    Currency snipe = entry.getSnipeAmount();
    return Currency.convertToUSD(entry.getCurrentUSPrice(), entry.getCurrentPrice(), snipe);
  }

  public int getSellerPositiveFeedback() {
    Seller seller = getSeller(entry.getSellerId());

    try {
      String feedbackPercent = seller.getPositivePercentage();
      if(feedbackPercent != null) feedbackPercent = feedbackPercent.replace("%", "");
      return safeConvert(feedbackPercent);
    } catch(Exception e) {
      return Zero;
    }
  }

  public String getComment() {
    String s = entry.getComment();
    return (s==null?"":s);
  }

  public int getSellerFeedback() {
    Seller seller = getSeller(entry.getSellerId());

    return seller.getFeedback();
  }

  public Currency getCurrentTotal() {
    Currency shipping = entry.getShippingWithInsurance();
    if (shipping.getCurrencyType() == Currency.NONE) {
      return shipping; // shipping not set so cannot add up values
    }

    Currency shippingUSD = Currency.convertToUSD(entry.getCurrentUSPrice(), entry.getCurrentPrice(), entry.getShippingWithInsurance());
    try {
      return entry.getUSCurBid().add(shippingUSD);
    } catch (Currency.CurrencyTypeException e) {
      JConfig.log().handleException("Threw a bad currency exception, which should be unlikely.", e); //$NON-NLS-1$
      return Currency.NoValue();
    }
  }

  public Currency getSnipeTotal() {
    Currency shipping2 = entry.getShippingWithInsurance();
    if (shipping2.getCurrencyType() == Currency.NONE) {
      return shipping2; // shipping not set so cannot add up values
    }

    Currency shippingUSD2 = Currency.convertToUSD(entry.getCurrentUSPrice(), entry.getCurrentPrice(), entry.getShippingWithInsurance());
    try {
      return Currency.convertToUSD(entry.getCurrentUSPrice(), entry.getCurrentPrice(), entry.getSnipeAmount()).add(shippingUSD2);
    } catch (Currency.CurrencyTypeException e) {
      JConfig.log().handleException("Currency addition or conversion threw a bad currency exception, which should be unlikely.", e); //$NON-NLS-1$
      return Currency.NoValue();
    }
  }
}
