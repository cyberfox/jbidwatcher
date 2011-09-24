package com.jbidwatcher.auction;

/**
 * Created by IntelliJ IDEA.
 * User: Morgan
 * Date: Jan 14, 2009
 * Time: 4:10:53 AM
 * All 'entry' objects have 'getIdentifier' as a method.
 */
public interface EntryInterface extends Snipeable {
  /**
   * @brief What is the auction's unique identifier on that server?
   *
   * @return The unique identifier for this auction.
   */
  String getIdentifier();
}
