package com.jbidwatcher.auction;

/**
 * Created by IntelliJ IDEA.
 * User: Morgan
 * Date: Jun 29, 2008
 * Time: 11:59:12 PM
 *
 * The resolver really exists to abstract the AuctionServerManager's key function: getting the default server.
 */
public interface Resolver {
  AuctionServerInterface getServer();
}
