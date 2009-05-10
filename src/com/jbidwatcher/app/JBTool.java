package com.jbidwatcher.app;

import com.jbidwatcher.auction.AuctionEntry;
import com.jbidwatcher.auction.Resolver;
import com.jbidwatcher.auction.AuctionServerInterface;
import com.jbidwatcher.auction.AuctionInfo;
import com.jbidwatcher.auction.server.ebay.ebayServer;
import com.jbidwatcher.auction.server.AuctionServerManager;
import com.jbidwatcher.util.config.JConfig;
import com.jbidwatcher.util.config.ErrorManagement;
import com.jbidwatcher.util.db.ActiveRecord;
import com.jbidwatcher.util.xml.XMLElement;
import com.jbidwatcher.util.queue.AuctionQObject;
import com.jbidwatcher.util.queue.MQFactory;
import com.jbidwatcher.util.Constants;
import com.jbidwatcher.util.ToolInterface;
import com.jbidwatcher.util.StringTools;
import com.jbidwatcher.util.html.JHTML;
import com.jbidwatcher.util.http.ClientHttpRequest;
import com.jbidwatcher.webserver.SimpleProxy;
import com.jbidwatcher.my.MyJBidwatcher;

import java.util.*;
import java.text.SimpleDateFormat;
import java.text.ParseException;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

/**
 * This provides a command-line interface to JBidwatcher, loading an individual auction
 * and returning the XML for it.
 *
 * User: mrs
 * Date: Oct 4, 2008
 * Time: 6:42:11 PM
 */
@SuppressWarnings({"UtilityClass", "UtilityClassWithoutPrivateConstructor"})
public class JBTool implements ToolInterface {
  private boolean mLogin = false;
  private boolean mTestQuantity = false;
  private String mUsername = null;
  private String mPassword = null;
  private SimpleProxy mServer = null;
  private boolean mJustMyeBay = false;
  private boolean mRunServer = false;
//  private int mSiteNumber = -1;
  private String mPortNumber = null;
  private List<String> mParams;
  private ebayServer mEbay;
  private String mCountry = "ebay.com";
  private ebayServer mEbayUK;
  private String mParseFile = null;

  private void testDateFormatting() {
    try {
      String siteDateFormat = "dd.MM.yy HH:mm:ss z";
      String testTime = "10.11.08 13:54:28 MET";
      SimpleDateFormat sdf = new SimpleDateFormat(siteDateFormat, Locale.US);
      Date endingDate = sdf.parse(testTime);
      TimeZone tz = sdf.getCalendar().getTimeZone();
      JConfig.log().logMessage("EndingDate: " + endingDate + "\nTZ: " + tz);
    } catch (ParseException e) {
      e.printStackTrace();
    }
  }

  public void execute() {
    setupAuctionResolver();

    if(mRunServer) {
      spawnServer();
    } else if(mJustMyeBay) {
      MQFactory.getConcrete(mEbay).enqueue(new AuctionQObject(AuctionQObject.LOAD_MYITEMS, null, null)); // NONSTRING Queue Object
      try { Thread.sleep(120000); } catch(Exception ignored) { }
    } else if(mParseFile != null) {
      buildAuctionEntryFromFile(mParseFile);
    } else {
      retrieveAndVerifyAuctions(mParams);
    }
  }

  public JBTool(String[] args) {
    mParams = parseOptions(args);
  }

  public static void main(String[] args) {
    JConfig.setLogger(new ErrorManagement());
    ActiveRecord.disableDatabase();
    JBTool tool = new JBTool(args);

    tool.execute();
    System.exit(0);
  }

  private void buildAuctionEntryFromFile(String fname) {
    StringBuffer sb = new StringBuffer(StringTools.cat(fname));
    try {
      AuctionInfo ai = mEbay.doParse(sb);
      AuctionEntry ae = new AuctionEntry();
      ae.setAuctionInfo(ai);
      JConfig.log().logMessage(ae.toXML().toString());
    } catch (Exception e) {
      JConfig.log().handleException("Failed to load auction from file: " + fname, e);
    }
  }

  private void retrieveAndVerifyAuctions(List<String> params) {
    if(params.size() == 0) return;
    try {
      if(params.size() > 1) {
        XMLElement auctionList = new XMLElement("auctions");
        for(String id : params) {
          XMLElement xmlized = AuctionEntry.retrieveAuctionXML(id);
          if(xmlized != null) auctionList.addChild(xmlized);
        }
        JConfig.log().logMessage(auctionList.toString());
      } else {
        StringBuffer auctionXML = AuctionEntry.retrieveAuctionXMLString(params.get(0));
        if (auctionXML != null) {
          JConfig.log().logMessage(auctionXML.toString());
          XMLElement xmlized = new XMLElement();
          xmlized.parseString(auctionXML.toString());

          if (JConfig.debugging() && mTestQuantity) {
            AuctionEntry ae2 = new AuctionEntry();
            ae2.fromXML(xmlized);
            JConfig.log().logDebug("ae2.quantity == " + ae2.getQuantity());
          }
        }
      }
    } catch(Exception dumpMe) {
      JConfig.log().handleException("Failure during serialization or deserialization of an auction", dumpMe);
    }
  }

  private void spawnServer() {
    int listenPort = 9099;
    if(mPortNumber != null) listenPort = Integer.parseInt(mPortNumber);
    mServer = new SimpleProxy(listenPort, MiniServer.class, this);
    mServer.go();
    try { mServer.join(); } catch(Exception ignored) { /* Time to die... */ }
  }

  private void setupAuctionResolver() {
    mEbay = new ebayServer(mCountry, mUsername, mPassword);
    mEbayUK = new ebayServer("ebay.co.uk", mUsername, mPassword);

    Resolver r = new Resolver() {
      public AuctionServerInterface getServer() { return mEbay; }
    };
    AuctionServerManager.getInstance().setServer(mEbay);
    mEbay.setBackupServer(mEbayUK);
    AuctionEntry.setResolver(r);
  }

  private List<String> parseOptions(String[] args) {
    List<String> options = new LinkedList<String>();
    List<String> params = new LinkedList<String>();

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
      if(option.equals("server")) mRunServer = true;
      if(option.startsWith("port=")) mPortNumber = option.substring(5);
      if(option.equals("logging")) JConfig.setConfiguration("logging", "true");
      if(option.equals("debug")) JConfig.setConfiguration("debugging", "true");
      if(option.equals("timetest")) testDateFormatting();
      if(option.equals("logconfig")) JConfig.setConfiguration("config.logging", "true");
      if(option.equals("logurls")) JConfig.setConfiguration("debug.urls", "true");
      if(option.equals("myebay")) mJustMyeBay = true;
      if(option.equals("sandbox")) JConfig.setConfiguration("override.ebayServer.viewHost", "cgi.sandbox.ebay.com");
      if(option.startsWith("country=")) {
        mCountry = option.substring(8);
        if(getSiteNumber(mCountry) == -1) JConfig.log().logMessage("That country is not recognized by JBidwatcher's eBay Server.");
      }
      if(option.equals("login")) mLogin = true;
      if(option.startsWith("username=")) mUsername = option.substring(9);
      if(option.startsWith("password=")) mPassword = option.substring(9);
      if(option.startsWith("file=")) mParseFile = option.substring(5);
      if(option.startsWith("upload=")) MyJBidwatcher.getInstance().sendLogFile(option.substring(7));
    }

    if(!mLogin) {
      mPassword = mUsername = "default";
    }
    return params;
  }

  public static int getSiteNumber(String site) {
    for(int i=0; i< Constants.SITE_CHOICES.length; i++) {
      if(site.equals(Constants.SITE_CHOICES[i])) return i;
    }
    return -1;
  }

  public void done() {
    mServer.halt();
    mServer.interrupt();
  }

  public void forceLogin() {
    mEbay.forceLogin();
    if(mEbayUK != null) mEbayUK.forceLogin();
  }
}
