package com.jbidwatcher.auction;

import java.util.Date;

/**
 * Created by IntelliJ IDEA.
 * User: Morgan
 * Date: Jan 14, 2009
 * Time: 3:31:11 AM
 *
 * An interface to disconnect the AuctionEntry behaviors from its exposed API.
 */
public interface Snipeable {
  /**
   * @brief Force this auction to snipe at a non-default time prior to
   * the auction end.
   *
   * @param newSnipeTime The new amount of time prior to the end of
   * this auction to fire a snipe.  Value is in milliseconds.  If the
   * value is set to -1, it will reinstate the default time.
   */
  void setSnipeTime(long newSnipeTime);

  /**
   * @brief Stop any snipe prepared on this auction.  If the auction is
   * already completed, then the snipe information is transferred to the
   * the 'cancelled' status.
   *
   * @param after_end - Is this auction already completed?
   */
  void cancelSnipe(boolean after_end);

  Date getEndDate();

  /**
   * @brief How close prior to the end is this particular auction
   * going to snipe?
   *
   * @return Number of milliseconds prior to auction close that a
   * snipe will be fired.
   */
  long getSnipeTime();

  /**
   * @brief Is this auction using the standard/default snipe time?
   *
   * @return False if the snipe time for this auction has been specially set.
   */
  boolean hasDefaultSnipeTime();

  String getIdentifier();

  boolean isComplete();
}
