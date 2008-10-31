package com.jbidwatcher.app;

import com.jbidwatcher.auction.AuctionEntry;
import com.jbidwatcher.auction.Resolver;
import com.jbidwatcher.auction.AuctionServerInterface;
import com.jbidwatcher.auction.server.ebay.ebayServer;
import com.jbidwatcher.auction.server.AuctionServer;
import com.jbidwatcher.auction.server.AuctionServerManager;
import com.jbidwatcher.util.config.JConfig;
import com.jbidwatcher.util.db.ActiveRecord;
import com.jbidwatcher.util.xml.XMLElement;
import com.jbidwatcher.util.queue.AuctionQObject;
import com.jbidwatcher.util.queue.MQFactory;
import com.jbidwatcher.util.T;

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
@SuppressWarnings({"UtilityClass", "UtilityClassWithoutPrivateConstructor"})
public class JBTool {
  private static boolean mLogin = false;
  private static String mUsername = null;
  private static String mPassword = null;

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

    boolean justMyeBay = false;
    for(String option: options) {
      if(option.equals("logging")) JConfig.setConfiguration("logging", "true");
      if(option.equals("debug")) JConfig.setConfiguration("debugging", "true");
      if(option.equals("logconfig")) JConfig.setConfiguration("config.logging", "true");
      if(option.equals("logurls")) JConfig.setConfiguration("debug.urls", "true");
      if(option.equals("myebay")) justMyeBay = true;
      if(option.equals("sandbox")) JConfig.setConfiguration("override.ebayServer.viewHost", "cgi.sandbox.ebay.com");
      if(option.startsWith("country=")) {
        String country = option.substring(8);
        JConfig.setConfiguration("override.ebayServer.viewHost", "cgi." + country);
        String bundle = country.replace('.', '_');
        try {
          T.setBundle(bundle);
        } catch(Exception e) {
          System.err.println("Can't find bundle " + bundle + ".properties to load.");
          T.setBundle("ebay_com");
        }
      }
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

    final AuctionServer ebay = new ebayServer();

    Resolver r = new Resolver() {
      public AuctionServerInterface getServerByName(String name) {
        return ebay;
      }

      public AuctionServerInterface getServerForIdentifier(String auctionId) {
        return ebay;
      }

      public AuctionServerInterface getServerForUrlString(String strURL) {
        return ebay;
      }
      public AuctionServerInterface getServer() {
        return ebay;
      }
    };
    AuctionServerManager.getInstance().addServer(ebay);
    AuctionEntry.setResolver(r);
    if(!justMyeBay) {
      try {
      AuctionEntry ae = AuctionEntry.construct(params.get(0));
      if(ae != null) {
        if (ae.isDutch()) ae.checkDutchHighBidder();
        XMLElement auctionXML = ae.toXML();
        System.out.println(auctionXML.toString());
        if (JConfig.debugging()) {
          AuctionEntry ae2 = new AuctionEntry();
          ae2.fromXML(auctionXML);
          System.out.println("ae2.quantity == " + ae2.getQuantity());
        }
      }
      } catch(Exception dumpMe) {
        System.out.println("Exception Thrown:\n" + dumpMe.toString() + "\n");
        dumpMe.printStackTrace(System.out);
      }
    } else {
      MQFactory.getConcrete("ebay").enqueue(new AuctionQObject(AuctionQObject.LOAD_MYITEMS, null, null));
      try { Thread.sleep(120000); } catch(Exception ignored) { }
    }
    System.exit(0);
  }
}
