package com.jbidwatcher.app;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.assistedinject.FactoryModuleBuilder;
import com.jbidwatcher.auction.*;
import com.jbidwatcher.auction.server.AuctionServer;
import com.jbidwatcher.auction.server.AuctionServerFactory;
import com.jbidwatcher.auction.server.ebay.ebayServer;
import com.jbidwatcher.auction.server.AuctionServerManager;
import com.jbidwatcher.scripting.JRubyPreloader;
import com.jbidwatcher.ui.AuctionsManager;
import com.jbidwatcher.util.*;
import com.jbidwatcher.util.Observer;
import com.jbidwatcher.util.config.JConfig;
import com.cyberfox.util.config.Base64;
import com.jbidwatcher.util.db.ActiveRecord;
import com.jbidwatcher.scripting.Scripting;
import com.jbidwatcher.util.queue.AuctionQObject;
import com.jbidwatcher.util.queue.MQFactory;
import com.jbidwatcher.util.html.JHTML;
import com.jbidwatcher.my.MyJBidwatcher;
import com.jbidwatcher.search.SearchManager;
import com.jbidwatcher.search.Searcher;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import java.util.*;
import java.text.SimpleDateFormat;
import java.text.ParseException;
import java.io.File;
import java.net.URL;
import java.net.HttpURLConnection;

/**
 * This provides a command-line interface to JBidwatcher, loading an individual auction
 * and returning the JSON for it.
 *
 * User: mrs
 * Date: Oct 4, 2008
 * Time: 6:42:11 PM
 */
@SuppressWarnings({"UtilityClass", "UtilityClassWithoutPrivateConstructor"})
public class JBTool {
  @Inject
  private AuctionServerFactory serverFactory;

  private final EntryFactory entryFactory;
  private final SearchManager searchManager;
  private final AuctionServerManager auctionServerManager;
  private final MyJBidwatcher myJBidwatcher;
  private boolean mLogin = false;
  private String mUsername = null;
  private String mPassword = null;
  private boolean mJustMyeBay = false;
  //  private int mSiteNumber = -1;
  private List<String> mParams;
  private ebayServer mEbay;
  private String mCountry = "ebay.com";
  private String mParseFile = null;
  private boolean mCompare = false;
  private boolean mMultiFiles = false;

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
    Searcher sm = searchManager.addSearch("Title", "zarf", "zarf", "ebay", -1, 12345678);
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
    if(mLogin) mEbay.forceLogin();

    if (mJustMyeBay) {
      MQFactory.getConcrete(mEbay.getFriendlyName()).enqueueBean(new AuctionQObject(AuctionQObject.LOAD_MYITEMS, null, null));
      try { Thread.sleep(120000); } catch(Exception ignored) { }
    } else if(mParseFile != null) {
      JConfig.setHomeDirectory("./");
      if (mCompare) {
        comparative(mParseFile);
      } else {
        buildAuctionEntryFromFile(mParseFile);
      }
    } else if(mMultiFiles) {
      for (String file : mParams) {
        comparative(file);
      }
    } else {
      retrieveAndVerifyAuctions(mParams);
    }
  }

  public JBTool(EntryFactory eFactory, final EntryCorral corral, SearchManager searchManager, AuctionServerManager serverManager,
                MyJBidwatcher myJBidwatcher) {
    this.entryFactory = eFactory;
    this.searchManager = searchManager;
    this.auctionServerManager = serverManager;
    this.myJBidwatcher = myJBidwatcher;

    ActiveRecord.disableDatabase();
    AuctionEntry.addObserver(entryFactory);
    AuctionEntry.addObserver(new Observer<AuctionEntry>() {
      public void afterCreate(AuctionEntry o) {
        corral.putWeakly(o);
      }
    });
  }

  public static void main(String[] args) {
//    JConfig.setLogger(new ErrorManagement());

    AbstractModule guiceModule = new AbstractModule() {
      @Override
      protected void configure() {
        bind(EntryManager.class).to(AuctionsManager.class);
        install(new FactoryModuleBuilder()
            .implement(AuctionServer.class, ebayServer.class)
            .build(AuctionServerFactory.class));
      }
    };

    Injector inject = Guice.createInjector(guiceModule);
    JBTool tool = inject.getInstance(JBTool.class);

    tool.mParams = tool.parseOptions(args);

    tool.execute();
    System.exit(0);
  }

  private void buildAuctionEntryFromFile(String fname) {
    StringBuffer sb = new StringBuffer(StringTools.cat(fname));
    try {
      long start = System.currentTimeMillis();
      AuctionInfo ai = mEbay.doParse(sb);
      AuctionEntry ae = entryFactory.constructEntry();
      ae.setAuctionInfo(ai);
      System.out.println("Took: " + (System.currentTimeMillis() - start));
      System.out.println(JSONObject.toJSONString(ae.getBacking()));
    } catch (Exception e) {
      JConfig.log().handleException("Failed to load auction from file: " + fname, e);
    }
  }

  private void comparative(String fname) {
    JRubyPreloader preloader = new JRubyPreloader(new Object());
    preloader.run();
    StringBuffer sb = new StringBuffer(StringTools.cat(fname));
    try {
      Record jResults = mEbay.doParse(sb).getBacking();
      Record rResults = mEbay.tryRuby(sb);

      System.out.println("Java:");
      dumpMap(jResults);

      System.out.println("\n\nRuby:");
      dumpMap(rResults);
      System.out.println();
    } catch (Exception e) {
      JConfig.log().handleException("Failed to load auction from file: " + fname, e);
    }
  }

  private void retrieveAndVerifyAuctions(List<String> params) {
    if(params.size() == 0) return;
    try {
      JSONArray ary = new JSONArray();
      for (String id : params) {
        JSONObject element = new JSONObject();
        element.putAll(mEbay.create(id).getBacking());
        ary.add(element);
      }
      System.out.println(ary.toJSONString());
    } catch(Exception dumpMe) {
      JConfig.log().handleException("Failure during serialization or deserialization of an auction", dumpMe);
    }
  }

  private void setupAuctionResolver() {
    mEbay = (ebayServer)serverFactory.create(mCountry, mUsername, mPassword);

    Resolver r = new Resolver() {
      public AuctionServerInterface getServer() { return mEbay; }
    };
    auctionServerManager.setServer(mEbay);
    entryFactory.setResolver(r);
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
      if(option.equals("uk")) { option="country=ebay.co.uk"; }
      if(option.equals("accountinfo")) { testAccountInfo(); return params; }
      if(option.equals("searching")) { testSearching(); return params; }
      if(option.equals("logging")) JConfig.setConfiguration("logging", "true");
      if(option.equals("debug")) JConfig.setConfiguration("debugging", "true");
      if(option.equals("timetest")) testDateFormatting();
      if(option.equals("logconfig")) JConfig.setConfiguration("config.logging", "true");
      if(option.equals("logurls")) JConfig.setConfiguration("debug.urls", "true");
      if(option.equals("myebay")) mJustMyeBay = true;
      if(option.equals("sandbox")) JConfig.setConfiguration("replace." + Constants.PROGRAM_VERS + ".ebayServer.viewHost", "cgi.sandbox.ebay.com");
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
      if(option.startsWith("compare=")) { mParseFile = option.substring(8); mCompare = true; }
      if(option.startsWith("bulk")) { mCompare = true; mMultiFiles = true; }
      if(option.startsWith("bidfile=")) testBidHistory(option.substring(8));
      if(option.startsWith("adult")) JConfig.setConfiguration("ebay.mature", "true");
      if(option.startsWith("upload=")) myJBidwatcher.sendFile(new File(option.substring(7)), "http://my.jbidwatcher.com/upload/log", "cyberfox@jbidwatcher.com", "This is a <test> of descriptions & stuff.");
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

  private void dumpMap(Map m) {
    Scripting.rubyMethod("dump_hash", m);
  }
}
