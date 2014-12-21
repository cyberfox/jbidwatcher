package com.jbidwatcher.auction.server;

import com.google.inject.assistedinject.Assisted;

/**
 * Created by mrs on 12/20/14.
 */
public interface AuctionServerFactory {
  AuctionServer create(@Assisted("site") String site, @Assisted("username") String username, @Assisted("password") String password);
}
