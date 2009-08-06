package com.jbidwatcher.auction;

import com.jbidwatcher.util.db.ActiveRecord;
import com.jbidwatcher.util.db.Table;
import com.jbidwatcher.util.Currency;

import java.util.List;

/**
 * User: mrs
 * Date: Mar 31, 2008
 * Time: 5:02:53 PM
 * 
 * A class to manage snipes.  Snipes are specific to an auction entry.
 */
public class AuctionSnipe extends ActiveRecord
{
  /**
   * @brief What is the amount that will be sniped when the snipe
   * timer goes off?
   *
   * How much is the snipe set for, if anything.  This is also used to
   * determine if a snipe is set at all for this auction.
   *
   * @return The amount that will be submitted as a bid when it is
   * time to snipe.
   */
  public Currency getAmount() { return getMonetary("amount"); }

  /**
   * @return The count of items to bid on in the snipe.
   * @brief What number of items will be sniped for when the snipe is
   * fired?
   */
//  public int getSnipeQuantity()
//  {
//    if (mSnipe != null) return mSnipe.getQuantity();
//    else return 0;
//  }
//
  /**
   * How many items are to be sniped on, when the snipe fires?
   * @return - The number of items to snipe for (only valid for multi-item auctions).
   */
  public int getQuantity() { return getInteger("quantity", 1); }
  public long getDelta() { return getInteger("delta"); }
  public int getUserId() { return getInteger("user_id"); }
  public String getStatus() { return getString("status"); }

  public boolean isNegative() { return getAmount().getValue() < 0.0; }

  public static AuctionSnipe create(Currency amount, int quantity, long delta) {
    AuctionSnipe snipe = new AuctionSnipe();

    snipe.setMonetary("amount", amount);
    snipe.setInteger("quantity", quantity);
    snipe.setInteger("delta", (int)delta);
    snipe.saveDB();

    return snipe;
  }

  private static Table sDB = null;
  protected static String getTableName() { return "snipes"; }

  protected Table getDatabase() {
    if (sDB == null) {
      sDB = openDB(getTableName());
    }
    return sDB;
  }

  public static AuctionSnipe find(String id) {
    return findFirstBy("id", id);
  }

  public static AuctionSnipe findFirstBy(String key, String value) {
    return (AuctionSnipe) ActiveRecord.findFirstBy(AuctionSnipe.class, key, value);
  }

  public static boolean deleteAll(List<AuctionSnipe> toDelete) {
    if(toDelete.isEmpty()) return true;
    String snipes = makeCommaList(toDelete);

    return toDelete.get(0).getDatabase().deleteBy("id IN (" + snipes + ")");
  }
}
