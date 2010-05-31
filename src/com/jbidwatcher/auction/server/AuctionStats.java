package com.jbidwatcher.auction.server;

import com.jbidwatcher.auction.AuctionEntry;
import com.jbidwatcher.auction.EntryInterface;

/**
 * Created by IntelliJ IDEA.
* User: Morgan
* Date: Feb 20, 2007
* Time: 8:56:21 PM
* To change this template use File | Settings | File Templates.
*/
public class AuctionStats {
  int _snipes;
  int _count;
  int _completed;
  AuctionEntry _nextSnipe;
  AuctionEntry _nextEnd;
  AuctionEntry _nextUpdate;

  public AuctionStats() {
    _snipes = _count = _completed = 0;
    _nextSnipe = _nextEnd = _nextUpdate = null;
  }

  public int getCompleted() {
    return _completed;
  }

  public int getSnipes() {
    return _snipes;
  }

  public int getCount() {
    return _count;
  }

  public AuctionEntry getNextSnipe() {
    return _nextSnipe;
  }

  public EntryInterface getNextEnd() {
    return _nextEnd;
  }
}
