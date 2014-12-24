package com.jbidwatcher.app;

import com.google.inject.AbstractModule;
import com.google.inject.assistedinject.FactoryModuleBuilder;
import com.jbidwatcher.auction.EntryManager;
import com.jbidwatcher.auction.server.AuctionServer;
import com.jbidwatcher.auction.server.AuctionServerFactory;
import com.jbidwatcher.auction.server.ebay.ebayServer;
import com.jbidwatcher.ui.*;

/**
* Created by mrs on 12/21/14.
*/
public class JBidwatcherModule extends AbstractModule {
  @Override
  protected void configure() {
    bind(EntryManager.class).to(AuctionsManager.class);
    install(new FactoryModuleBuilder()
        .implement(AuctionServer.class, ebayServer.class)
        .build(AuctionServerFactory.class));
    install(new FactoryModuleBuilder()
        .implement(JTabPopupMenu.class, JTabPopupMenu.class)
        .build(PopupMenuFactory.class));
    install(new FactoryModuleBuilder().implement(AuctionListHolder.class, AuctionListHolder.class).build(AuctionListHolderFactory.class));
  }
}
