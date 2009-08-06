package com.jbidwatcher.auction;

import com.jbidwatcher.util.html.JHTML;
import com.jbidwatcher.util.http.CookieJar;
import com.jbidwatcher.util.Currency;

/**
 * Created by IntelliJ IDEA.
 * User: Morgan
 * Date: Feb 26, 2007
 * Time: 8:55:10 AM
 *
 * Abstraction of the bidding interface, so it can be replaced with other bidding code, if desired.
 */
public interface Bidder {
  JHTML.Form getBidForm(CookieJar cj, AuctionEntry inEntry, Currency inCurr) throws com.jbidwatcher.auction.BadBidException;

  int buy(AuctionEntry ae, int quantity);

  /**
   * @brief Perform the entire bidding process on an item.
   *
   * @param inEntry - The item to bid on.
   * @param inBid - The amount to bid.
   * @param inQuantity - The number of items to bid on.
   *
   * @return - A bid response code, or BID_ERROR_UNKNOWN if we can't
   * figure out what happened.
   */
  int bid(AuctionEntry inEntry, Currency inBid, int inQuantity);

  int placeFinalBid(CookieJar cj, JHTML.Form bidForm, AuctionEntry inEntry, Currency inBid, int inQuantity);
}
