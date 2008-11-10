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
import com.jbidwatcher.util.StringTools;
import com.jbidwatcher.webserver.HTTPProxyClient;
import com.jbidwatcher.webserver.SimpleProxy;
import com.jbidwatcher.webserver.JBidProxy;

import java.util.*;
import java.text.SimpleDateFormat;
import java.text.ParseException;
import java.io.FileNotFoundException;
import java.net.Socket;

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
  private static SimpleProxy mServer = null;

  private static void testDateFormatting() {
    String[] zones = TimeZone.getAvailableIDs();
    for(String zone : zones) {
      if(zone.contains("M")) {
        System.err.println("MEZ - " + zone);
      }
    }

    try {
      String siteDateFormat = "dd.MM.yy HH:mm:ss z";
      String testTime = "10.11.08 13:54:28 MET";
      SimpleDateFormat sdf = new SimpleDateFormat(siteDateFormat, Locale.US);
      Date endingDate = sdf.parse(testTime);
      TimeZone tz = sdf.getCalendar().getTimeZone();
      System.err.println("EndingDate: " + endingDate + "\nTZ: " + tz);
    } catch (ParseException e) {
      e.printStackTrace();
    }
  }

  public static class MiniServer extends HTTPProxyClient {
    public MiniServer(Socket talkSock) {
      super(talkSock);
    }

    protected boolean handleAuthorization(String inAuth) {
      return true;
    }

    protected boolean needsAuthorization(String reqFile) {
      return false;
    }

    protected StringBuffer buildHeaders(String whatDocument, byte[][] buf) throws FileNotFoundException {
      return null;
    }

    protected StringBuffer buildHTML(String whatDocument) {
      if(whatDocument.indexOf("/") != -1) {
        whatDocument = whatDocument.substring(whatDocument.indexOf("/") +1);
      }
      if(StringTools.isNumberOnly(whatDocument)) {
        StringBuffer sb = retrieveAuctionXML(whatDocument);
        System.err.println("Returning: " + sb);
        return sb;
      } else if(whatDocument.equals("SHUTDOWN")) {
        mServer.halt();
        mServer.interrupt();
      }
      return new StringBuffer();
    }
  }

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
    boolean runServer = false;
    int site = -1;
    String portNumber = null;
    for(String option: options) {
      if(option.equals("server")) runServer = true;
      if(option.startsWith("port=")) portNumber = option.substring(5);
      if(option.equals("logging")) JConfig.setConfiguration("logging", "true");
      if(option.equals("debug")) JConfig.setConfiguration("debugging", "true");
      if(option.equals("timetest")) testDateFormatting();
      if(option.equals("logconfig")) JConfig.setConfiguration("config.logging", "true");
      if(option.equals("logurls")) JConfig.setConfiguration("debug.urls", "true");
      if(option.equals("myebay")) justMyeBay = true;
      if(option.equals("sandbox")) JConfig.setConfiguration("override.ebayServer.viewHost", "cgi.sandbox.ebay.com");
      if(option.startsWith("country=")) {
        String country = option.substring(8);
        if(!T.setCountrySite(country)) {
          System.err.println("Can't find properties bundle to load for country " + country + ".");
          T.setBundle("ebay_com");
        }
        site = ebayServer.getSiteNumber(country);
        if(site == -1) System.err.println("That country is not recognized by JBidwatcher's eBay Server.");
      }
      if(option.equals("login")) mLogin = true;
      if(option.startsWith("username=")) mUsername = option.substring(9);
      if(option.startsWith("password=")) mPassword = option.substring(9);
    }

    if(!mLogin) {
      mPassword = mUsername = "default";
    }
    if(site == -1) site = 0;

    final AuctionServer ebay = new ebayServer(Integer.toString(site), mUsername, mPassword);

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

    if(runServer) {
      int listenPort = 9099;
      if(portNumber != null) listenPort = Integer.parseInt(portNumber);
      mServer = new SimpleProxy(listenPort, MiniServer.class, null);
      mServer.go();
      try { mServer.join(); } catch(Exception ignored) { /* Time to die... */ }
    } else if(justMyeBay) {
      MQFactory.getConcrete("ebay").enqueue(new AuctionQObject(AuctionQObject.LOAD_MYITEMS, null, null));
      try { Thread.sleep(120000); } catch(Exception ignored) { }
    } else {
      try {
        StringBuffer auctionXML = retrieveAuctionXML(params.get(0));
        if(auctionXML != null) {
          System.out.println(auctionXML);
          XMLElement xmlized = new XMLElement();
          xmlized.parseString(auctionXML.toString());

          if (JConfig.debugging()) {
            AuctionEntry ae2 = new AuctionEntry();
            ae2.fromXML(xmlized);
            System.out.println("ae2.quantity == " + ae2.getQuantity());
          }
        }
      } catch(Exception dumpMe) {
        System.out.println("Exception Thrown:\n" + dumpMe.toString() + "\n");
        dumpMe.printStackTrace(System.out);
      }
    }
    System.exit(0);
  }

  private static StringBuffer retrieveAuctionXML(String identifier) {
    AuctionEntry ae = AuctionEntry.construct(identifier);
    if(ae != null) {
      if (ae.isDutch()) ae.checkDutchHighBidder();
      XMLElement auctionXML = ae.toXML();
      return auctionXML.toStringBuffer();
    }

    return null;
  }

}
