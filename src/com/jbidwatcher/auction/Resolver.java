package com.jbidwatcher.auction;

import com.jbidwatcher.auction.server.AuctionServer;

/**
 * Created by IntelliJ IDEA.
 * User: Morgan
 * Date: Jun 29, 2008
 * Time: 11:59:12 PM
 * To change this template use File | Settings | File Templates.
 */
public interface Resolver {
  AuctionServerInterface getServerByName(String name);//  Handle the case of '198332643'.  (For 'paste auction').

  AuctionServerInterface getServerForIdentifier(String auctionId);

  AuctionServerInterface getServerForUrlString(String strURL);
}
