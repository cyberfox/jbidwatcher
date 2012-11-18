package com.jbidwatcher.app;

import com.jbidwatcher.auction.*;
import com.jbidwatcher.auction.server.ebay.ebayServer;
import com.jbidwatcher.auction.server.AuctionServerManager;
import com.jbidwatcher.util.Observer;
import com.jbidwatcher.util.config.JConfig;
import com.cyberfox.util.config.Base64;
import com.jbidwatcher.util.db.ActiveRecord;
import com.jbidwatcher.util.xml.XMLElement;
import com.jbidwatcher.util.queue.AuctionQObject;
import com.jbidwatcher.util.queue.MQFactory;
import com.jbidwatcher.util.Constants;
import com.jbidwatcher.util.ToolInterface;
import com.jbidwatcher.util.StringTools;
import com.jbidwatcher.util.html.JHTML;
import com.jbidwatcher.util.webserver.SimpleProxy;
import com.jbidwatcher.my.MyJBidwatcher;
import com.jbidwatcher.search.SearchManager;
import com.jbidwatcher.search.Searcher;

import java.util.*;
import java.text.SimpleDateFormat;
import java.text.ParseException;
import java.io.File;
import java.net.URL;
import java.net.HttpURLConnection;

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

  private void testBasicAuthentication(final String user, final String key) throws Exception {
    URL retrievalURL = JConfig.getURL("http://localhost:9909/services/sqsurl");
    HttpURLConnection huc = (HttpURLConnection) retrievalURL.openConnection();

    huc.setRequestProperty("Authorization", "Basic " + Base64.encodeString(user + ":" + key));
    String url = StringTools.cat(huc.getInputStream());
    System.out.println("URL == " + url);
  }

  private void testBidHistory(String file) {
    StringBuffer sb = new StringBuffer(StringTools.cat(file));
    JHTML hDoc = new JHTML(sb);
    List<JHTML.Table> tableList = hDoc.extractTables();

    System.err.println("There were " + tableList.size() + " tables.");

    for(JHTML.Table t : tableList) {
      if(t.rowCellMatches(0, "Bidder")) {
        for(int i=1; i<t.getRowCount()-1; i++) {
          System.err.println("Bidder #" + i + ": " + t.getCell(0, i));
        }
      }
    }
  }

  private Map testMicroformats(String file) {
    StringBuffer sb = new StringBuffer(StringTools.cat(file));
    JHTML hDoc = new JHTML(sb);

    return hDoc.extractMicroformat();
  }

  private void testSearching() {
    Searcher sm = SearchManager.getInstance().addSearch("Title", "zarf", "zarf", "ebay", -1, 12345678);
    sm.execute();
  }

  private void testDateFormatting() {
    try {
      String siteDateFormat = "dd.MM.yy HH:mm:ss z";
      String testTime = "10.11.08 13:54:28 MET";
      SimpleDateFormat sdf = new SimpleDateFormat(siteDateFormat, Locale.US);
      Date endingDate = sdf.parse(testTime);
      TimeZone tz = sdf.getCalendar().getTimeZone();
      System.out.println("EndingDate: " + endingDate + "\nTZ: " + tz);
    } catch (ParseException e) {
      e.printStackTrace();
    }
  }

  public void execute() {
    setupAuctionResolver();

    if(mRunServer) {
      spawnServer();
    } else if(mJustMyeBay) {
      MQFactory.getConcrete(mEbay.getFriendlyName()).enqueueBean(new AuctionQObject(AuctionQObject.LOAD_MYITEMS, null, null));
      try { Thread.sleep(120000); } catch(Exception ignored) { }
    } else if(mParseFile != null) {
      JConfig.setHomeDirectory("./");
      buildAuctionEntryFromFile(mParseFile);
    } else {
      retrieveAndVerifyAuctions(mParams);
    }
  }

  public JBTool(String[] args) {
    mParams = parseOptions(args);
  }

  public static void main(String[] args) {
//    JConfig.setLogger(new ErrorManagement());
    ActiveRecord.disableDatabase();
    AuctionEntry.addObserver(EntryFactory.getInstance());
    AuctionEntry.addObserver(new Observer<AuctionEntry>() {
      public void afterCreate(AuctionEntry o) {
        EntryCorral.getInstance().putWeakly(o);
      }
    });
    JBTool tool = new JBTool(args);

    tool.execute();
    System.exit(0);
  }

  private void buildAuctionEntryFromFile(String fname) {
    StringBuffer sb = new StringBuffer(StringTools.cat(fname));
    try {
      long start = System.currentTimeMillis();
      AuctionInfo ai = mEbay.doParse(sb);
      AuctionEntry ae = EntryFactory.getInstance().constructEntry();
      ae.setAuctionInfo(ai);
      System.out.println("Took: " + (System.currentTimeMillis() - start));
      System.out.println(ae.toXML().toString());
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
          XMLElement xmlized = EntryFactory.getInstance().retrieveAuctionXML(id);
          if(xmlized != null) auctionList.addChild(xmlized);
        }
        System.out.println(auctionList.toString());
      } else {
        StringBuffer auctionXML = EntryFactory.getInstance().retrieveAuctionXMLString(params.get(0));
        if (auctionXML != null) {
          System.out.println(auctionXML.toString());
          XMLElement xmlized = new XMLElement();
          xmlized.parseString(auctionXML.toString());

          if (JConfig.debugging() && mTestQuantity) {
            AuctionEntry ae2 = EntryFactory.getInstance().constructEntry();
            ae2.fromXML(xmlized);
            System.out.println("ae2.quantity == " + ae2.getQuantity());
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

    Resolver r = new Resolver() {
      public AuctionServerInterface getServer() { return mEbay; }
    };
    AuctionServerManager.getInstance().setServer(mEbay);
    EntryFactory.setResolver(r);
  }

  private List<String> parseOptions(String[] args) {
    List<String> options = new LinkedList<String>();
    List<String> params = new LinkedList<String>();
    boolean append=false;

    for(String arg : args) {
      if(append) {
        String last = options.get(options.size()-1);
        last = last.substring(0, last.length()-1) + ' ' + arg;
        options.set(options.size()-1, last);
      } else {
        if (arg.charAt(0) == '-') {
          int skip = 1;
          if (arg.charAt(1) == '-') skip = 2;
          options.add(arg.substring(skip));
          if (arg.charAt(arg.length() - 1) == '\\') append = true;
        } else {
          params.add(arg);
        }
      }
    }

    for(String option: options) {
      if(option.equals("basicauth")) {
        try {
          testBasicAuthentication("morgan", "schweers");
        } catch (Exception e) {
          System.err.println(e.getMessage());
        }
        return params;
      }
      if(option.equals("uk")) { mEbay = mEbayUK; }
      if(option.equals("accountinfo")) { testAccountInfo(); return params; }
      if(option.equals("searching")) { testSearching(); return params; }
      if(option.equals("server")) mRunServer = true;
      if(option.startsWith("port=")) mPortNumber = option.substring(5);
      if(option.equals("logging")) JConfig.setConfiguration("logging", "true");
      if(option.equals("debug")) JConfig.setConfiguration("debugging", "true");
      if(option.equals("timetest")) testDateFormatting();
      if(option.equals("logconfig")) JConfig.setConfiguration("config.logging", "true");
      if(option.equals("logurls")) JConfig.setConfiguration("debug.urls", "true");
      if(option.equals("myebay")) mJustMyeBay = true;
      if(option.equals("sandbox")) JConfig.setConfiguration("replace." + JConfig.getVersion() + ".ebayServer.viewHost", "cgi.sandbox.ebay.com");
      if(option.startsWith("country=")) {
        mCountry = option.substring(8);
        if(getSiteNumber(mCountry) == -1) System.out.println("That country is not recognized by JBidwatcher's eBay Server.");
      }
      if(option.equals("login")) mLogin = true;
      if(option.startsWith("username=")) mUsername = option.substring(9);
      if(option.startsWith("password=")) mPassword = option.substring(9);
      if(option.startsWith("mfparse=")) {
        long start = System.currentTimeMillis();
        dumpMap(testMicroformats(option.substring(8)));
        System.out.println("Took: " + (System.currentTimeMillis() - start));
      }
      if(option.startsWith("file=")) mParseFile = option.substring(5);
      if(option.startsWith("bidfile=")) testBidHistory(option.substring(8));
      if(option.startsWith("adult")) JConfig.setConfiguration("ebay.mature", "true");
      if(option.startsWith("upload=")) MyJBidwatcher.getInstance().sendFile(new File(option.substring(7)), "http://my.jbidwatcher.com/upload/log", "cyberfox@jbidwatcher.com", "This is a <test> of descriptions & stuff.");
    }

    if(!mLogin) {
      mPassword = mUsername = "default";
    }
    return params;
  }

  private void testAccountInfo() {
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

  private void dumpMap(Map m) {
    dumpMap(m, 0);
    System.out.println();
  }

  private void dumpMap(Map m, int offset) {
    System.out.println("{");
    for (Object o : m.keySet()) {
      Object value = m.get(o);
      if (value instanceof String) {
        System.out.println("\"" + o.toString() + "\" => \"" + value.toString().replace("\"", "\\\"") + "\"");
      } else if (value instanceof Map) {
        System.out.print("\"" + o.toString() + "\" => ");
        dumpMap((Map) value, offset + 2);
      }
    }
    System.out.print("}");
  }
}
