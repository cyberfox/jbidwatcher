package com.jbidwatcher.ui;

import com.jbidwatcher.auction.AuctionEntry;

/**
 * Created by IntelliJ IDEA.
 * User: mrs
 * Date: 12/16/11
 * Time: 11:40 PM
 * To change this template use File | Settings | File Templates.
 */
public interface FilterInterface {
  void deleteAuction(AuctionEntry ae);

  void addAuction(AuctionEntry ae);
}
