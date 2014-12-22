package com.jbidwatcher.auction.server;

import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

import javax.annotation.Nullable;

/**
 * Created by mrs on 12/20/14.
 */
public interface AuctionServerFactory {
  @Inject
  AuctionServer create(@Nullable @Assisted("site") String site, @Nullable @Assisted("username") String username, @Nullable @Assisted("password") String password);
}
