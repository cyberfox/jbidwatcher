package com.jbidwatcher.auction.server;

import com.jbidwatcher.util.html.JHTML;
import com.jbidwatcher.util.http.CookieJar;
import com.jbidwatcher.auction.AuctionEntry;

/**
 * Created by IntelliJ IDEA.
 * User: Morgan
 * Date: Feb 26, 2007
 * Time: 8:55:10 AM
 * To change this template use File | Settings | File Templates.
 */
public interface Bidder {
  JHTML.Form getBidForm(CookieJar cj, AuctionEntry inEntry, com.jbidwatcher.util.Currency inCurr, int inQuant) throws BadBidException;

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
  int bid(AuctionEntry inEntry, com.jbidwatcher.util.Currency inBid, int inQuantity);

  int placeFinalBid(CookieJar cj, JHTML.Form bidForm, AuctionEntry inEntry, com.jbidwatcher.util.Currency inBid, int inQuantity);
}
