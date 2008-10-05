package com.jbidwatcher.app;

import com.jbidwatcher.auction.AuctionEntry;
import com.jbidwatcher.auction.Resolver;
import com.jbidwatcher.auction.AuctionServerInterface;
import com.jbidwatcher.auction.server.ebay.ebayServer;
import com.jbidwatcher.auction.server.AuctionServer;
import com.jbidwatcher.util.config.JConfig;
import com.jbidwatcher.util.db.ActiveRecord;
import com.jbidwatcher.util.xml.XMLElement;

import java.util.List;
import java.util.LinkedList;

/**
 * This provides a command-line interface to JBidwatcher, loading an individual auction
 * and returning the XML for it.
 *
 * User: mrs
 * Date: Oct 4, 2008
 * Time: 6:42:11 PM
 */
public class JBTool {
  private static boolean mLogin = false;
  private static String mUsername;
  private static String mPassword;

  public static void main(String[] args) {
    List<String> options = new LinkedList<String>();
    List<String> params = new LinkedList<String>();
    ActiveRecord.disableDatabase();

    for(String arg : args) {
      if(arg.charAt(0) == '-') {
        int skip = 1;
        if(arg.charAt(1) == '-') skip = 2;
        options.add(arg.substring(skip));
      } else {
        params.add(arg);
      }
    }

    for(String option: options) {
      if(option.equals("logging")) JConfig.setConfiguration("logging", "true");
      if(option.equals("debug")) JConfig.setConfiguration("debugging", "true");
      if(option.equals("logconfig")) JConfig.setConfiguration("config.logging", "true");
      if(option.equals("login")) mLogin = true;
      if(option.startsWith("username=")) mUsername = option.substring(9);
      if(option.startsWith("password=")) mPassword = option.substring(9);
    }

    if(mLogin) {
      JConfig.setConfiguration("ebay.user", mUsername);
      JConfig.setConfiguration("ebay.password", mPassword);
    } else {
      JConfig.setConfiguration("ebay.user", "default");
      JConfig.setConfiguration("ebay.password", "default");
    }

    Resolver r = new Resolver() {
      AuctionServer ebay = new ebayServer();
      public AuctionServerInterface getServerByName(String name) {
        return ebay;
      }

      public AuctionServerInterface getServerForIdentifier(String auctionId) {
        return ebay;
      }

      public AuctionServerInterface getServerForUrlString(String strURL) {
        return ebay;
      }
    };
    AuctionEntry.setResolver(r);
    AuctionEntry ae = AuctionEntry.construct(params.get(0));
    XMLElement auctionXML = ae.toXML();
    System.out.println(auctionXML.toString());
    if(JConfig.debugging()) {
      AuctionEntry ae2 = new AuctionEntry();
      ae2.fromXML(auctionXML);
      System.out.println("ae2.quantity == " + ae2.getQuantity());
    }
    System.exit(0);
  }
}
