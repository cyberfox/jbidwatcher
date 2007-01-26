package com.jbidwatcher.auction.server;
/*
 * Copyright (c) 2000-2007, CyberFOX Software, Inc. All Rights Reserved.
 *
 * Developed by mrs (Morgan Schweers)
 */

/*
 * @file   AuctionServer.java
 * @author Morgan Schweers <cyberfox@users.sourceforge.net>
 * @date   Wed Oct  9 13:49:02 2002
 * @note   Library GPL'ed.
 * @brief  This is an interface description for the general auction Servers
 *
 * It allows abstracting the auction setup to a factory creator, so
 * the factory can identify which site (ebay, yahoo, amazon, etc.) it
 * is, and do the appropriate parsing for that site.
 */
import com.jbidwatcher.config.JConfig;
import com.jbidwatcher.config.JConfigTab;
import com.jbidwatcher.queue.MQFactory;
import com.jbidwatcher.queue.AuctionQObject;
import com.jbidwatcher.xml.XMLElement;
import com.jbidwatcher.xml.XMLSerialize;
import com.jbidwatcher.util.html.JHTML;
import com.jbidwatcher.util.http.CookieJar;
import com.jbidwatcher.util.http.Http;
import com.jbidwatcher.util.*;
import com.jbidwatcher.util.Currency;
import com.jbidwatcher.search.SearchManagerInterface;
import com.jbidwatcher.*;
import com.jbidwatcher.auction.EntryManager;
import com.jbidwatcher.auction.AuctionEntry;
import com.jbidwatcher.auction.SpecificAuction;
import com.jbidwatcher.auction.AuctionInfo;

import java.util.*;
import java.net.*;
import java.io.*;
import java.text.SimpleDateFormat;


public abstract class AuctionServer implements XMLSerialize {
  public static final int BID_ERROR_UNKNOWN=-1;
  public static final int BID_ERROR_CANNOT=1;
  public static final int BID_ERROR_AMOUNT=2;
  public static final int BID_ERROR_OUTBID=3;
  public static final int BID_WINNING=4;
  public static final int BID_SELFWIN=5;
  public static final int BID_DUTCH_CONFIRMED=6;
  public static final int BID_ERROR_MULTI=7;
  public static final int BID_ERROR_TOO_LOW=8;
  public static final int BID_ERROR_ENDED=9;
  public static final int BID_ERROR_BANNED=10;
  public static final int BID_ERROR_RESERVE_NOT_MET=11;
  public static final int BID_ERROR_CONNECTION=12;
  public static final int BID_ERROR_TOO_LOW_SELF = 13; // You can't bid that low against yourself...
  public static final int BID_ERROR_AUCTION_GONE = 14; // Auction vanished between bid creation and submission.
  public static final int BID_ERROR_NOT_BIN = 15; // Trying to 'Buy' an item that isn't a BIN/Fixed Price listing.
  public static final int BID_BOUGHT_ITEM = 16; //  Successfully bought an item via BIN.
  public static final int BID_ERROR_ACCOUNT_SUSPENDED = 17; //  Your account has been (!) suspended, you can't bid.
  public static final int BID_ERROR_CANT_SIGN_IN = 18; //  We tried to get bid pages, but it kept asking for login.
  public static final int BID_ERROR_WONT_SHIP = 19; //  You are registered in a country to which the seller doesn't ship.
  public static final int BID_ERROR_REQUIREMENTS_NOT_MET = 20; //  This seller has set buyer requirements for this item and only sells to buyers who meet those requirements.

  protected String siteId = null; /**< The human-readable name of an auction server. */
  private String userCfgString=null;
  private String passCfgString=null;

  private EntryManager _em = null;

  private static final int YEAR_BASE = 1990;
  private static GregorianCalendar midpointDate = new GregorianCalendar(YEAR_BASE, Calendar.JANUARY, 1);
  private static final int HIGHBIT_ASCII = 0x80;
  public static final String UPDATE_LOGIN_COOKIE = "Update login cookie";

  public abstract StringBuffer getAuctionViaAffiliate(CookieJar cj, AuctionEntry ae, String id) throws CookieJar.CookieException;
  public abstract int buy(AuctionEntry ae, int quantity);
  public abstract long getSnipePadding();

  /*!@class BadBidException
  *
  * @brief Sometimes we need to be able to throw an exception when a
  * bid is bad, to simplify the error handling.
  */
  public class BadBidException extends Exception {
    String _associatedString;
    int _aucResult;

    public BadBidException(String inString, int auction_result) {
      _associatedString = inString;
      _aucResult = auction_result;
    }

    /** @noinspection RefusedBequest*/
    public String toString() {
      return _associatedString;
    }

    public int getResult() {
      return _aucResult;
    }
  }

  protected final Set<AuctionEntry> _aucList = new TreeSet<AuctionEntry>(new AuctionEntry.AuctionComparator()); /**< The list of auctions that this server is holding onto. */

  protected long _pageRequestTime=0; /**< The full amount of time it takes to request a single page from this site. */
  protected long _affRequestTime=0;  /**< The amount of time it takes to request an item via their affiliate program. */
  protected long _officialServerTimeDelta=0;
  protected TimeZone _officialServerTimeZone = null;
  public abstract String extractIdentifierFromURLString(String urlStyle);
  public abstract CookieJar getNecessaryCookie(boolean force);
  public abstract CookieJar getSignInCookie(CookieJar old_cj);
  public abstract void safeGetAffiliate(CookieJar cj, AuctionEntry inEntry) throws CookieJar.CookieException;
  public abstract JHTML.Form getBidForm(CookieJar cj, AuctionEntry inEntry, com.jbidwatcher.util.Currency inCurr, int inQuant) throws BadBidException;
  public abstract int bid(AuctionEntry inEntry, Currency inBid, int inQuantity);
  public abstract int placeFinalBid(CookieJar cj, JHTML.Form bidForm, AuctionEntry inEntry, Currency inBid, int inQuantity);
  public abstract boolean checkIfIdentifierIsHandled(String auctionId);
  public abstract void establishMenu();
  public abstract JConfigTab getConfigurationTab();
  public abstract void cancelSearches();
  public abstract void addSearches(SearchManagerInterface searchManager);
  public abstract Currency getMinimumBidIncrement(Currency currentBid, int bidCount);
  public abstract boolean isHighDutch(AuctionEntry inAE);
  public abstract void updateHighBid(AuctionEntry ae);

  /** 
   * @brief Get the official 'right now' time from the server.
   * 
   * @return - The current time as reported by the auction site's 'official time' mechanism.
   */
  protected abstract Date getOfficialTime();

  /** 
   * @brief Get the URL (in String form that a browser can view with) for a given item ID on this auction server.
   * 
   * @param itemID - The item to retrieve the URL for.
   * 
   * @return - A String with the full URL of the item description on the auction server.
   */
  public abstract String getBrowsableURLFromItem(String itemID);

  /** 
   * @brief Get the string-form URL for a given item ID on this
   * auction server, for when we aren't browsing.
   * 
   * @param itemID - The item to retrieve the URL for.
   * 
   * @return - A String with the full URL of the item description on the auction server.
   */
  public abstract String getStringURLFromItem(String itemID);

  /** 
   * @brief Get a java.net.URL that points to the item on the auction site's server.
   * 
   * @param itemID - The item to get the URL for.
   * 
   * @return - A URL that refers to the item at the auction site.
   */
  protected abstract URL getURLFromItem(String itemID);

  /** 
   * @brief Allocate a new auction that is of this auction server's specific subtype.
   *
   * In order for the auctions to be able to fill out relevant
   * information, each has to have a subclass of AuctionInfo dedicated
   * to it.  This abstract function is defined by the auction server
   * specific classes, and actually returns an object of their type of
   * 'auction'.
   * 
   * @return - A freshly allocated auction-server specific auction data object.
   */
  public abstract SpecificAuction getNewSpecificAuction();

  /** 
   * @brief Given a URL, determine if this auction server handles it.
   * 
   * @param checkURL - The URL to check.
   * 
   * @return - true if this auction server handles items at the provided URL.
   */
  public abstract boolean doHandleThisSite(URL checkURL);

  /** 
   * @brief Given a server name, determine if this auction server handles it.
   * 
   * @param serverName - The server name to query.
   * 
   * @return - true if this auction server recognizes the given server name.
   */
  public abstract boolean checkIfSiteNameHandled(String serverName);

  /** 
   * @brief Show an auction entry in the browser.
   * 
   * @param inEntry - The auction entry to load up and display in the users browser.
   */
  public void showBrowser(AuctionEntry inEntry) {
    final String entryId = inEntry.getIdentifier();
    String doLocalServer = JConfig.queryConfiguration("server.enabled", "false");
    String browseTo;

    if(doLocalServer.equals("false")) {
      browseTo = getBrowsableURLFromItem(entryId);
    } else {
      String localServerPort = JConfig.queryConfiguration("server.port", Constants.DEFAULT_SERVER_PORT_STRING);
      if(inEntry.isInvalid()) {
        browseTo = "http://localhost:" + localServerPort + "/cached_" + entryId;
      } else {
        browseTo = "http://localhost:" + localServerPort + '/' + entryId;
      }
    }

    MQFactory.getConcrete("browse").enqueue(browseTo);
  }

  private Date mNow = new Date();
  private GregorianCalendar mCal;

  public String getTime() {
    TimeZone serverTZ = getOfficialServerTimeZone();
    if (serverTZ != null) {
      if(mCal == null) {
        mCal = new GregorianCalendar(serverTZ);
        if(JConfig.queryConfiguration("display.ebayTime", "false").equals("true")) {
          Constants.remoteClockFormat.setCalendar(mCal);
        }
      }

      if (JConfig.queryConfiguration("timesync.enabled", "true").equals("true")) {
        mNow.setTime(System.currentTimeMillis() +
                getOfficialServerTimeDelta() +
                getPageRequestTime());
      } else {
        mNow.setTime(System.currentTimeMillis());
      }
      mCal.setTime(mNow);
      //  Just in case it changes because of the setup.
      mNow.setTime(mCal.getTimeInMillis());
      return getUserId() + '@' + getName() + ": " + Constants.remoteClockFormat.format(mNow);
    } else {
      mNow.setTime(System.currentTimeMillis());
      return getUserId() + '@' + getName() + ": " + Constants.localClockFormat.format(mNow);
    }
  }

  /**
   * @brief Convert an auction item description URL in String format into a java.net.URL.
   * This is a brutally simple utility function, so it's static, and should be referred
   * to via the AuctionServer class directly.
   * 
   * @param siteAddress - The string URL to convert into a 'real' URL on the given site.
   * 
   * @return - A java.net.URL that points to what we consider the 'official' item URL on the site.
   */
  public static URL getURLFromString(String siteAddress) {
    URL auctionURL=null;

    try {
      auctionURL = new URL(siteAddress);
    } catch(MalformedURLException e) {
      ErrorManagement.handleException("getURLFromString failed on " + siteAddress, e);
    }

    return auctionURL;
  }

  //  Generalized logic
  //  -----------------

  /** 
   * @brief Add an auction to this server, based on item ID.
   * 
   * @param itemId - The auction item to add.
   * 
   * @return - The underlying 'AuctionInfo' object that contains all
   * the basic accessors for auction data.
   */
  public AuctionInfo addAuction(String itemId) {
    URL auctionURL = getURLFromItem(itemId);
    return( addAuction(auctionURL, itemId));
  }

  /** 
   * @brief construct the site-related configuration strings for use
   * in looking up usernames and passwords for this auction server.
   */
  private void checkUserPassStartup() {
    if(userCfgString == null) {
      userCfgString = siteId + ".user";
      passCfgString = siteId + ".password";
    }
  }

  /** 
   * @brief Get the human-readable auction site name for this server.
   * 
   * @return - A String with the human-readable auction site name.
   */
  public String getName() {
    return siteId;
  }

  /** 
   * @brief Auctions must be registered when they are added, so that
   * the auction server can keep a list of auctions that it is
   * managing.  This is used when storing out the list of auctions per
   * server.
   * 
   * @param ae - The AuctionEntry to add to the server's list.
   */
  public void registerAuction(AuctionEntry ae) {
    synchronized(_aucList) { _aucList.add(ae); }
  }

  /** 
   * @brief When an auction is deleted, it should unregister itself,
   * so that the AuctionServer object won't store it out, when doing
   * saves.
   * 
   * @param ae - The AuctionEntry to remove from the server's list.
   */
  public void unregisterAuction(AuctionEntry ae) {
    synchronized(_aucList) { _aucList.remove(ae); }
  }

  public class AuctionStats {
    private int _snipes;
    private int _count;
    private int _completed;
    private AuctionEntry _nextSnipe;
    private AuctionEntry _nextEnd;
    private AuctionEntry _nextUpdate;

    public AuctionStats() {
      _snipes = _count = _completed = 0;
      _nextSnipe = _nextEnd = _nextUpdate = null;
    }

    public int getCompleted() {
      return _completed;
    }

    public int getSnipes() {
      return _snipes;
    }

    public int getCount() {
      return _count;
    }

    public AuctionEntry getNextSnipe() {
      return _nextSnipe;
    }

    public AuctionEntry getNextEnd() {
      return _nextEnd;
    }

    public AuctionEntry getNextUpdate() {
      return _nextUpdate;
    }
  }

  public AuctionStats getStats() {
    AuctionStats outStat = new AuctionStats();
    outStat._count = _aucList.size();
    long lastUpdateTime = Long.MAX_VALUE;
    long lastEndedTime = Long.MAX_VALUE;
    long lastSnipeTime = Long.MAX_VALUE;

    for (AuctionEntry ae : _aucList) {
      if (ae.isEnded()) {
        outStat._completed++;
      } else {
        long thisTime = ae.getEndDate().getTime();
        if (ae.isSniped()) {
          outStat._snipes++;
          if (thisTime < lastSnipeTime) {
            outStat._nextSnipe = ae;
            lastSnipeTime = thisTime;
          }
        }

        if (thisTime < lastEndedTime) {
          outStat._nextEnd = ae;
          lastEndedTime = thisTime;
        }

        long nextTime = ae.getNextUpdate();
        if (nextTime < lastUpdateTime) {
          outStat._nextUpdate = ae;
          lastUpdateTime = nextTime;
        }
      }
    }

    return outStat;
  }

  public void fromXML(XMLElement inXML) {
    siteId = inXML.getProperty("NAME", "unknown");

    checkUserPassStartup();

    String username = inXML.getProperty("USER", null);
    if(username != null) {
      JConfig.setConfiguration(userCfgString, username);

      //  If password1 is available, use it as a Base64 encoded
      //  password.  If it's not available, fall back to
      //  compatibility, loading the password as unencrypted.  This
      //  can be extended, by including encryption algorithms with
      //  increasing numbers at the end of PASSWORD, and preserving
      //  backwards compatibility.
      String b64Password = inXML.getProperty("PASSWORD1");
      String password = b64Password != null ? Base64.decodeToString(b64Password) : inXML.getProperty("PASSWORD", null);

      if(password != null) {
        JConfig.setConfiguration(passCfgString, password);
      }
    }

    Iterator<XMLElement> entryStep = inXML.getChildren();
    while(entryStep.hasNext()) {
      XMLElement perEntry = entryStep.next();
      AuctionEntry ae = new AuctionEntry();

	  ae.setServer(this);
      ae.fromXML(perEntry);
      if(_em != null) {
        _em.addEntry(ae);
      }
    }
  }

  public void setEntryManager(EntryManager em) {
    _em = em;
  }

  public XMLElement toXML() {
    XMLElement xmlResult = new XMLElement("server");

    if(getUserId() != null) {
      xmlResult.setProperty("user", getUserId());
      xmlResult.setProperty("password1", Base64.encodeString(getPassword(), false));
    }
    xmlResult.setProperty("name", siteId);

    synchronized(_aucList) {
      for (AuctionEntry ae : _aucList) {
        xmlResult.addChild(ae.toXML());
      }
    }

    return xmlResult;
  }

  /** 
    * @brief How many auctions is this auction server managing?
    * 
    * @return - The number of auctions that this server is currently aware of.
    */
  public int getAuctionCount() {
    return(_aucList.size());
  }

  /** 
    * @brief Get the user's ID for this auction server.
    * 
    * @return - The user's ID, as they entered it.
    */
  public String getUserId() {
    checkUserPassStartup();
    return JConfig.queryConfiguration(userCfgString, "default");
  }

  /** 
    * @brief Get the user's password for this auction server.
    * 
    * @return - The user's password, as they entered it.
    */
  public String getPassword() {
    checkUserPassStartup();
    return JConfig.queryConfiguration(passCfgString, "default");
  }

  /** 
    * @brief Resynchronize with the server's 'official' time, so as to
    * make sure not to miss a snipe, for example.
    *
    * Primarily exists to be used 'interactively'.
    */
  public static void reloadTime() {
    if (JConfig.queryConfiguration("timesync.enabled", "true").equals("true")) {
      MQFactory.getConcrete("auction_manager").enqueue("TIMECHECK");
    }
  }

  public void reloadTimeNow() {
    if(setOfficialTimeDifference()) {
      MQFactory.getConcrete("Swing").enqueue("Successfully synchronized time with " + siteId + '.');
    } else {
      MQFactory.getConcrete("Swing").enqueue("Failed to synchronize time with " + siteId + '!');
    }
  }

  /**
   * @brief Returns the amount of time it takes to retrieve a page
   * from the auction server.
   * 
   * @return The amount of milliseconds it takes to get a simple page
   * from the auction server.
   */
  public long getPageRequestTime() {
    return _pageRequestTime;
  }

  /**
   * @brief Returns the amount of time it takes to retrieve an item
   * from the auction server via their affiliate program.
   *
   * @return The amount of milliseconds it takes to get an item
   * from the auction server via their affiliate server.
   */
  public long getAffiliateRequestTime() {
    return _affRequestTime;
  }

  /**
   * @brief Returns the difference in time between the local machine's
   * normalized time, and the auction site's normalized time.
   * 
   * @return The amount of milliseconds off the server time is from
   * local time.
   */
  public long getOfficialServerTimeDelta() {
    return _officialServerTimeDelta;
  }

  /** 
   * @brief Retrieve what time zone the server is in.
   * 
   * @return - The time zone of the auction server.
   */
  public TimeZone getOfficialServerTimeZone() {
    return _officialServerTimeZone;
  }

  /** 
   * @brief Load an auction, and return it.  It really doesn't 'add'
   * anything...
   * 
   * @param auctionURL - The URL to the item description to add.
   * @param item_id - The item ID to add.
   * 
   * @return - An AuctionInfo low-level generic Auction object.
   */
  public AuctionInfo addAuction(URL auctionURL, String item_id) {
    SpecificAuction newAuction = (SpecificAuction) loadAuction(auctionURL, item_id, null, true);

    return(newAuction);
  }

  /** 
   * @brief Load an auction from a given URL, and return the textual
   * form of that auction to the caller in a Stringbuffer, having
   * passed in any necessary cookies, passwords, etc.
   * 
   * @param auctionURL - The URL of the auction to load.
   * 
   * @return - A StringBuffer containing the text of the auction at that URL.
   *
   * @throws java.io.FileNotFoundException -- If the URL doesn't exist on the auction server.
   */
  public StringBuffer getAuction(URL auctionURL) throws FileNotFoundException {
    StringBuffer loadedPage;

    try {
      String cookie = null;
      CookieJar curCook = getNecessaryCookie(false);
      if(curCook != null) {
        cookie = curCook.toString();
      }
      loadedPage = Http.receivePage(Http.makeRequest(auctionURL, cookie));
    } catch(FileNotFoundException fnfe) {
      ErrorManagement.logDebug("Item not found: " + auctionURL.toString());
      throw fnfe;
    } catch(IOException e) {
      ErrorManagement.handleException("Error loading URL (" + auctionURL.toString() + ')', e);
      loadedPage = null;
    }
    return loadedPage;
  }

  static long s_last_updated = 0;

  public AuctionInfo loadAuction(URL auctionURL, String item_id, AuctionEntry ae) {
    return loadAuction(auctionURL, item_id, ae, false);
  }

  private void markCommunicationError(AuctionEntry ae) {
    if (ae != null) {
      MQFactory.getConcrete("Swing").enqueue("LINK DOWN Communications failure talking to the server during item #" + ae.getIdentifier() + "( " + ae.getTitle() + " )");
    } else {
      MQFactory.getConcrete("Swing").enqueue("LINK DOWN Communications failure talking to the server");
    }
  }

  /**
   * @brief Load an auction, given its URL, its item id, and its 'AuctionEntry'.
   *
   * BUGBUG - This function should not require either of the last two entries.
   *
   * @param auctionURL - The URL to load the auction from.
   * @param item_id - The item # to associate this returned info with.
   * @param ae - An object to notify when an error occurs.
   * @param viaAffiliate - Whether to load via an affiliate link or not.
   * 
   * @return - An object containing the information extracted from the auction.
   */
  public AuctionInfo loadAuction(URL auctionURL, String item_id, AuctionEntry ae, boolean viaAffiliate) {
    SpecificAuction curAuction = getNewSpecificAuction();

    if(item_id != null) {
      curAuction.setIdentifier(item_id);
    }
    StringBuffer sb = null;

    if(viaAffiliate) {
      try {
        long pre = System.currentTimeMillis();
        sb = getAuctionViaAffiliate(getNecessaryCookie(false), ae, item_id);
        long post = System.currentTimeMillis();
        _affRequestTime = (post - pre);
        if(sb == null) viaAffiliate = false;
      } catch (CookieJar.CookieException e) {
        //  This failed...  Let's retry with a normal get...
        viaAffiliate = false;
      }
    }

    try {
      if(!viaAffiliate) {
        if (JConfig.queryConfiguration("timesync.enabled", "true").equals("true")) {
          long pre = System.currentTimeMillis();
          sb = getAuction(auctionURL);
          long post = System.currentTimeMillis();
          _pageRequestTime = (post - pre);
        } else /* TimeSync disabled */
          sb = getAuction(auctionURL);
      }
      if(sb != null) {
        curAuction.setContent(sb, false);
      }
    } catch(FileNotFoundException ignored) {
      //  Just get out.  The item no longer exists on the auction
      //  server, so we shouldn't be trying any of the rest.  The
      //  Error should have been logged at the lower level, so just
      //  punt.  It's not a communications error, either.
      return null;
    } catch(Exception catchall) {
      if(JConfig.debugging()) ErrorManagement.handleException("Some unexpected error occurred during loading the auction.", catchall);
      sb = null;
    }
    boolean successfulParse = true;

    if(sb == null) {
      checkLogError(ae);
      successfulParse = false;
    }

    if(successfulParse) {
      successfulParse = curAuction.preParseAuction();
      if(successfulParse) {
        successfulParse = curAuction.parseAuction(ae);
      } else {
        ErrorManagement.logMessage("Bad Parse!");
        checkLogError(ae);
      }
    }

    if(!successfulParse) {
      //  Whoops!  Bad thing happened on the way to loading the auction!
      ErrorManagement.logDebug("Failed to parse auction!  Bad return result from auction server.");
      //  Only retry the login cookie once every ten minutes of these errors.
      if( (s_last_updated + Constants.ONE_MINUTE * 10) > System.currentTimeMillis()) {
        s_last_updated = System.currentTimeMillis();
        MQFactory.getConcrete(siteId).enqueue(new AuctionQObject(AuctionQObject.MENU_CMD, UPDATE_LOGIN_COOKIE, null)); //$NON-NLS-1$ //$NON-NLS-2$
      }
      curAuction = null;
    }

    if(curAuction != null) {
      curAuction.save();
    }
    return(curAuction);
  }

  private void checkLogError(AuctionEntry ae) {
    if (ae != null) {
      ae.logError();
    } else {
      markCommunicationError(ae);
    }
  }

  public AuctionInfo reloadAuction(AuctionEntry inEntry) {
    URL auctionURL = getURLFromItem(inEntry.getIdentifier());

    SpecificAuction curAuction = (SpecificAuction) loadAuction(auctionURL, inEntry.getIdentifier(), inEntry);

    if(curAuction != null) {
      synchronized(_aucList) {
        _aucList.remove(inEntry);
        inEntry.setAuctionInfo(curAuction);
        inEntry.clearInvalid();
        _aucList.add(inEntry);
      }
      MQFactory.getConcrete("Swing").enqueue("LINK UP");
    } else {
      inEntry.setLastStatus("Failed to load from server!");
      inEntry.setInvalid();
    }

    return(curAuction);
  }

  private static String stripHigh(String inString, String fmtString) {
    char[] stripOut = new char[inString.length()];

    inString.getChars(0, inString.length(), stripOut, 0);
    char[] format = new char[fmtString.length()];
    fmtString.getChars(0, fmtString.length(), format, 0);
    String legalString = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-:,";
    for(int i=0; i<stripOut.length; i++) {
      if(stripOut[i] > HIGHBIT_ASCII) stripOut[i] = ' ';

      if(i < format.length) {
        if( (format[i] == ' ') && (legalString.indexOf(stripOut[i]) == -1)) {
            stripOut[i] = ' ';
        }
      }
    }
    return new String(stripOut);
  }

  protected Date figureDate(String endTime, String siteDateFormat) {
    return figureDate(endTime, siteDateFormat, true);
  }

  /** 
   * @brief Use the date parsing code to figure out the time an
   * auction ends (also used to parse the 'official' time) from the
   * web page.
   * 
   * @param endTime - The string containing the human-readable time to be parsed.
   * @param siteDateFormat - The format describing the human-readable time.
   * @param strip_high - Whether or not to strip high characters.
   * 
   * @return - The date/time in Date format that was represented by
   * the human readable date string.
   */
  protected Date figureDate(String endTime, String siteDateFormat, boolean strip_high) {
    String endTimeFmt = endTime;
    SimpleDateFormat sdf = new SimpleDateFormat(siteDateFormat, Locale.US);

    sdf.set2DigitYearStart(midpointDate.getTime());

    if(endTime == null) return null;

    if(strip_high) {
      endTimeFmt = stripHigh(endTime, siteDateFormat);
    }
    Date endingDate;

    try {
      endingDate = sdf.parse(endTimeFmt);
      _officialServerTimeZone = sdf.getCalendar().getTimeZone();
    } catch(java.text.ParseException e) {
      ErrorManagement.handleException("Error parsing date (" + endTimeFmt + "), setting to completed.", e);
      endingDate = new Date();
    }
    return(endingDate);
  }

  public boolean setOfficialTimeDifference() {
    long localDateBeforePage = System.currentTimeMillis();

    Date serverTime = getOfficialTime();
    if (serverTime != null) {
      long localDateAfterPage = System.currentTimeMillis();

      long reqTime = localDateAfterPage - localDateBeforePage;
      //  eBay's current time, minus the current time before we loaded the page, minus half the request-time
      //  tells how far off our system clock is to eBay.
      //noinspection MultiplyOrDivideByPowerOfTwo
      _officialServerTimeDelta = (serverTime.getTime() - localDateBeforePage)-(reqTime/2);
      return true;
    } else {
      //  This is bad...
      ErrorManagement.logMessage(siteId + ": Error, can't accurately set delta to server's official time.");
      //  This should be LOGGED!  -- FIXME  -- mrs: 13-February-2001 19:59
      _pageRequestTime = 0;
      _officialServerTimeDelta = 0;
      return false;
    }
  }

  /** 
   * @brief Determine if the provided string is all digits, a commonly
   * needed check for auction ids.
   * 
   * @param checkVal - The string to check for digits.
   * 
   * @return - true if all characters in checkVal are digits, false
   * otherwise or if the string is empty.
   */
  protected static boolean isNumberOnly(String checkVal) {
    int strLength = checkVal.length();

    if(strLength == 0) return(false);

    for(int i = 0; i<strLength; i++) {
      if(!Character.isDigit(checkVal.charAt(i))) return(false);
    }

    return(true);
  }
}
