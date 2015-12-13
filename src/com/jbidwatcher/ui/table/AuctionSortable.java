package com.jbidwatcher.ui.table;

import com.jbidwatcher.auction.AuctionEntry;
import com.jbidwatcher.auction.Seller;
import com.jbidwatcher.util.Currency;
import com.jbidwatcher.util.config.JConfig;

import java.util.HashMap;
import java.util.Map;

/**
 * Holds the sortable methods for an AuctionEntry.  This is a featherweight; containing the current auction entry to be processed
 * rather than creating a new one every time we need to do a lookup.  This also caches seller information, which may not be a great
 * choice on long-running instances.
 *
 * Created by mschweers on 8/1/14.
 */
class AuctionSortable {
  private AuctionEntry entry;

  private static Map<String, Seller> sellers = new HashMap<String, Seller>();

  public AuctionSortable() { }

  public void setEntry(AuctionEntry ae) {
    entry = ae;
  }

  /** Utility methods **/
  private Seller getSeller(String sellerId) {
    Seller seller;
    if(sellers.containsKey(sellerId)) {
      seller = sellers.get(sellerId);
    } else {
      seller = Seller.findFirstBy("id", sellerId);
      sellers.put(sellerId, seller);
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

  /**
   * Get the auction's identifier.
   *
   * @return The auction's identifier.
   */
  public String getId() { return entry.getIdentifier(); }

  /**
   * Get a sortable currency (USD) equivalent to the current high bid on a listing.
   *
   * @return The current high bid in USD, falling back to the 'buy now' if it's not an auction. It could, but shouldn't, return Currency.NoValue().
   */
  public Currency getCurrentBid() {
    if(entry.getDefaultCurrency().getCurrencyType() == Currency.US_DOLLAR) {
      return entry.getCurrentPrice();
    }

    Currency rval = entry.getUSCurBid();
    if(rval.getValue() == 0.0 && rval.getCurrencyType() == Currency.US_DOLLAR) {
      return entry.getCurrentUSPrice();
    }
    return rval;
  }

  /**
   * Get a sortable currency (USD) equivalent to either the current snipe (if there is one) or the current user's max bid (if they have one).
   *
   * @return The current snipe or the user's maximum bid (in that order) converted to USD for sorting.  Currency.NoValue() if neither is present.
   */
  public Currency getSnipeOrMax() { return Currency.convertToUSD(entry.getCurrentUSPrice(), entry.getCurrentPrice(), getMaxOrSnipe(entry)); }

  /**
   * If the listing has a fixed price component, return a sortable currency (USD) equivalent of that fixed price amount.
   *
   * @return The buy-now amount of a listing, converted to USD for sorting purposes, or null if no fixed price amount is found.
   */
  public Currency getFixedPrice() { return Currency.convertToUSD(entry.getCurrentUSPrice(), entry.getCurrentPrice(), entry.getBuyNow()); }

  /**
   * Get the sortable currency (USD) equivalent of the shipping amount (including optional insurance), or Currency.NoValue() if none found.
   *
   * @return The shipping amount (with optional insurance), converted to USD or Currency.NoValue() if the shipping amount couldn't be determined.
   */
  public Currency getShippingInsurance() {
    Currency si = (!entry.getShipping().isNull())?entry.getShippingWithInsurance(): Currency.NoValue();
    //  This is crack.  I'm insane to even think about doing this, but it works...
    return Currency.convertToUSD(entry.getCurrentUSPrice(), entry.getCurrentPrice(), si);
  }

  /**
   * Get the sortable currency (USD) equivalent of the current high bid by the current user.
   *
   * @return The current high bid by the user, in a canonicalized currency (USD) for sorting purposes, or Currency.NoValue() if none found.
   */
  public Currency getMax() {
    Currency bid = entry.isBidOn()?entry.getBid(): Currency.NoValue();
    return Currency.convertToUSD(entry.getCurrentUSPrice(), entry.getCurrentPrice(), bid);
  }

  /**
   * Get the snipe amount set (if any) and convert it to a sortable currency, USD.
   *
   * @return The USD equivalent of the snipe amount, or null if no snipe amount has been provided.
   */
  public Currency getSnipe() {
    Currency snipe = entry.getSnipeAmount();
    return Currency.convertToUSD(entry.getCurrentUSPrice(), entry.getCurrentPrice(), snipe);
  }

  /**
   * Get the seller's feedback as an integer representing a percentage.
   *
   * @return The percentage of positive feedbacks the seller of this auction entry has received, or 0 if that number is not available.
   */
  public int getSellerPositiveFeedback() {
    Seller seller = getSeller(entry.getSellerId());

    try {
      String feedbackPercent = seller.getPositivePercentage();
      if(feedbackPercent != null) feedbackPercent = feedbackPercent.replace("%", "");
      return safeConvert(feedbackPercent);
    } catch(Exception e) {
      return 0;
    }
  }

  /**
   * Get any comment that has been assigned to this entry.
   *
   * @return The comment for the auction entry, or the empty string if no comment has been assigned.
   */
  public String getComment() {
    String s = entry.getComment();
    return (s==null?"":s);
  }

  /**
   * Get the seller's feedback as an integer.
   *
   * @return The current entry's seller's total (non-percentage) feedback score.
   */
  public int getSellerFeedback() {
    Seller seller = getSeller(entry.getSellerId());

    return seller.getFeedback();
  }

  /**
   * Get a sortable equivalent of the current price plus shipping.  This converts to USD if possible, as a common converter, so all
   * currency values can be sorted against each other.
   *
   * @return Currency.NoValue() if the shipping is not set, or if the USD conversion of the shipping can't be added to the USD
   * conversion of the current price, otherwise the USD equivalent of the sum of shipping + current price.
   */
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

  /**
   * Get a sortable version of the snipe+shipping. This converts to USD if possible, as a common converter, so all currency values
   * can be sorted against each other.
   *
   * @return Currency.NoValue() if the shipping is not set, or there's a problem adding the USD shipping to the USD-converted snipe,
   * otherwise returns the sum of the USD equivalents of any snipe set and the shipping (with insurance).
   */
  public Currency getSnipeTotal() {
    Currency shipping = entry.getShippingWithInsurance();
    if (shipping.getCurrencyType() == Currency.NONE) {
      return shipping; // shipping not set so cannot add up values
    }

    Currency shippingUSD = Currency.convertToUSD(entry.getCurrentUSPrice(), entry.getCurrentPrice(), entry.getShippingWithInsurance());
    try {
      return Currency.convertToUSD(entry.getCurrentUSPrice(), entry.getCurrentPrice(), entry.getSnipeAmount()).add(shippingUSD);
    } catch (Currency.CurrencyTypeException e) {
      JConfig.log().handleException("Currency addition or conversion threw a bad currency exception, which should be unlikely.", e); //$NON-NLS-1$
      return Currency.NoValue();
    }
  }
}
