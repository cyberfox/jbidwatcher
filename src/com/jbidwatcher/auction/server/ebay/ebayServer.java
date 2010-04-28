package com.jbidwatcher.auction.server.ebay;

/*
 * Copyright (c) 2000-2007, CyberFOX Software, Inc. All Rights Reserved.
 *
 * Developed by mrs (Morgan Schweers)
 */

//  This is the concrete implementation of AuctionServer to handle
//  parsing eBay auction pages.  There should be *ZERO* eBay specific
//  logic outside this class.  A pipe-dream, perhaps, but it seems
//  mostly doable.

import com.jbidwatcher.util.config.*;
import com.jbidwatcher.util.Externalized;
import com.jbidwatcher.auction.server.ServerMenu;
import com.jbidwatcher.util.queue.*;
import com.jbidwatcher.util.queue.TimerHandler;
import com.jbidwatcher.util.html.JHTML;
import com.jbidwatcher.util.http.CookieJar;
import com.jbidwatcher.util.*;
import com.jbidwatcher.util.Currency;
import com.jbidwatcher.search.Searcher;
import com.jbidwatcher.search.SearchManager;
import com.jbidwatcher.search.SearchManagerInterface;
import com.jbidwatcher.auction.*;
import com.jbidwatcher.auction.server.AuctionServer;

import java.net.URL;
import java.util.*;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.io.FileNotFoundException;

/** @noinspection OverriddenMethodCallInConstructor*/
public final class ebayServer extends AuctionServer implements MessageQueue.Listener,JConfig.ConfigListener {
  private final static ebayCurrencyTables sCurrencies = new ebayCurrencyTables();
  private TT T;

  /** @noinspection FieldAccessedSynchronizedAndUnsynchronized*/
  private eBayTimeQueueManager _etqm;
  private Searcher mSellerSearch;
  private ebaySearches mSearcher;
  private ebayLoginManager mLogin;
  private SnipeListener mSnipeQueue;

  /** @noinspection FieldCanBeLocal*/
  private TimerHandler eQueue;

  /**< The full amount of time it takes to request a single page from this site. */
  private long mPageRequestTime;

  /**< The amount of time to adjust the system clock by, to make it be nearly second-accurate to eBay time. */
  private long mOfficialServerTimeDelta;

  /**< The time zone the auction server is in (for eBay this will be PST or PDT). */
  private TimeZone mOfficialServerTimeZone;
  private Date mNow = new Date();
  private GregorianCalendar mCal;
  private ebayCleaner mCleaner;
  private Bidder mBidder;

  public Currency getMinimumBidIncrement(Currency currentBid, int bidCount) {
    return sCurrencies.getMinimumBidIncrement(currentBid, bidCount);
  }

  public void updateConfiguration() {
    mLogin.setUserId(JConfig.queryConfiguration(getName() + ".user", "default"));
    mLogin.setPassword(JConfig.queryConfiguration(getName() + ".password", "default"));

    if(!mLogin.isDefault()) {
      Searcher s = SearchManager.getInstance().getSearchByName("My Selling Items");
      if(s == null) {
        mSellerSearch = SearchManager.getInstance().buildSearch(System.currentTimeMillis(), "Seller", "My Selling Items", mLogin.getUserId(), getName(), null, 1);
        mSellerSearch.setCategory("selling");
        SearchManager.getInstance().addSearch(mSellerSearch);
      } else {
        s.setSearch(mLogin.getUserId());
      }
    }
  }

  private class eBayTimeQueueManager extends TimeQueueManager {
    /**
     * Don't start checking until the server time delta becomes non-zero, i.e. we've done a time-check.
     *
     * @return false.  Always false.
     */
    public boolean check() {
      return getServerTimeDelta() != 0 && super.check();
    }

    /**
     * Adjust the current time by the difference in time between localtime and servertime, including page request time.
     *
     * @return As close an approximation of 'time at the eBay server' as can be done.
     */
    public long getCurrentTime() {
      return super.getCurrentTime() + getServerTimeDelta();
    }
  }

  /**
   * @brief Build a menu that can be added to the JBidwatcher standard
   * menu, to do eBay-specific things.
   *
   */
  public ServerMenu establishMenu() {
    ServerMenu esm = new ebayServerMenu(this, Constants.EBAY_DISPLAY_NAME, 'b');
    esm.initialize();

    return esm;
  }

  public void updateHighBid(AuctionEntry ae) {
    String bidHistory = Externalized.getString("ebayServer.protocol") + T.s("ebayServer.bidHost") + Externalized.getString("ebayServer.V3file") + Externalized.getString("ebayServer.viewBidsCGI") + ae.getIdentifier();
    CookieJar cj = mLogin.getNecessaryCookie(false);
    String userCookie = null;
    if (cj != null) userCookie = cj.toString();
    JHTML htmlDocument = new JHTML(bidHistory, userCookie, mCleaner);

    if(htmlDocument.isLoaded()) {
      List<JHTML.Table> bidderTables = htmlDocument.extractTables();
      for (JHTML.Table t : bidderTables) {
        if (t.rowCellMatches(0, "^(Bidder|User ID).*")) {
          int bidCount = t.getRowCount() - 1; // 1 for the header

          // -1 for the starting price
          if(t.rowCellMatches(bidCount, "Starting Price")) bidCount -= 1;
          if(t.rowCellMatches(bidCount, "No purchases have been made.")) {
            ae.setNumBids(0);
            ae.saveDB();
            return;
          }

          if(ae.getNumBidders() == 0) ae.setNumBids(bidCount);
          int myMostRecentRow = -1;
          for(int i=1; i < bidCount+1; i++) {
            if(t.getCell(0, i).equals(mLogin.getUserId())) {
              myMostRecentRow = i;
              break;
            }
          }
          if(myMostRecentRow != -1) {
            String newCurrency = t.getCell(1, myMostRecentRow);
            if (newCurrency != null) {
              Currency highBid = Currency.getCurrency(newCurrency);
              try {
                if (!ae.isBidOn() || ae.getBid().less(highBid)) {
                  ae.setBid(highBid);
                  ae.setBidQuantity(bidCount);
                  ae.saveDB();
                }
              } catch (Currency.CurrencyTypeException cte) {
                //  Bad things happen here.  Ignore it for now.
              }
            }
          }
          if(bidCount > 0) {
            AuctionInfo ai = ae.getAuction();
            String highBidder = t.getCell(0, 1);
            int feedbackStart = highBidder.indexOf(" (");
            if(feedbackStart != -1) {
              highBidder = highBidder.substring(0, feedbackStart);
            }
            ai.setHighBidder(highBidder);
            ai.saveDB();
          }
        }
      }
    }
  }
    /**
     * @brief Process an action, based on messages passed through our internal queues.
     */
  public void messageAction(Object deQ) {
    AuctionQObject ac = (AuctionQObject)deQ;
    String failString = null;

    switch(ac.getCommand()) {
      case AuctionQObject.LOAD_URL:
        mSearcher.loadAllFromURLString(SearchManager.getSearchById((Long) ac.getData()), ac.getLabel());
        return;
      case AuctionQObject.LOAD_SEARCH:
        mSearcher.loadSearchString(SearchManager.getSearchById((Long) ac.getData()), ac.getLabel(), false);
        return;
      case AuctionQObject.LOAD_TITLE:
        mSearcher.loadSearchString(SearchManager.getSearchById((Long)ac.getData()), ac.getLabel(), true);
        return;
      case AuctionQObject.LOAD_SELLER:
        doGetSelling(SearchManager.getSearchById((Long) ac.getData()), ac.getLabel());
        return;
      case AuctionQObject.LOAD_MYITEMS:
        if(mLogin.isDefault()) {
          failString = Externalized.getString("ebayServer.cantLoadWithoutUsername1") + " " + getName() + Externalized.getString("ebayServer.cantLoadWithoutUsername2");
        } else {
          doMyEbaySynchronize(ac.getLabel());
          return;
        }
        break;
      case AuctionQObject.BID:
        bidMsg(ac);
        return;
      default:
        //  It's okay if we don't recognize it.
    }

    if(ac.getData() != null) {
      if(ac.getData().equals("Get My eBay Items")) {
        if(mLogin.isDefault()) {
          failString = Externalized.getString("ebayServer.cantLoadWithoutUsername1") + " " + getName() + Externalized.getString("ebayServer.cantLoadWithoutUsername2");
        } else {
          SearchManager.getInstance().getSearchByName("My eBay").execute();
          return;
        }
      }

      /**
       * Get items this user is selling.
       */
      if(ac.getData().equals("Get Selling Items")) {
        if(mLogin.isDefault()) {
          failString = Externalized.getString("ebayServer.cantLoadSellerWithoutUser1") + " " + getName() + Externalized.getString("ebayServer.cantLoadWithoutUsername2");
        } else {
          if(mSellerSearch == null) updateConfiguration();
          if(mSellerSearch != null) mSellerSearch.execute();
          return;
        }
      }

      /**
       * Update the login cookie, that contains session and adult information, for example.
       */
      if(ac.getData().equals("Update login cookie")) {
        if(mLogin.isDefault()) {
          failString = Externalized.getString("ebayServer.cantUpdateCookieWithoutUser1") + " " + getName() + Externalized.getString("ebayServer.cantLoadWithoutUsername2");
        } else {
          forceLogin();
          if (getBackupServer() != null) {
            ((ebayServer) getBackupServer()).forceLogin();
          }
          return;
        }
      }

      if(ac.getData().equals("Dump eBay Activity Queue")) {
        if(getBackupServer() != null && getBackupServer() != this) MQFactory.getConcrete(getBackupServer()).enqueueBean(ac);
        _etqm.dumpQueue(T.getBundle());
        return;
      }
    }

    /**
     * If we've made a failure string, and we're using the default
     * user, then display the error, otherwise indicate that we got an
     * unexpected command.
     */
    if(failString != null && failString.length() != 0 && mLogin.isDefault()) {
      MQFactory.getConcrete("Swing").enqueue("NOACCOUNT " + failString);
    } else {
      if (ac.getData() instanceof String) {
        String acData = (String) ac.getData();
        JConfig.log().logMessage("Dequeue'd unexpected command or fell through: " + ac.getCommand() + ':' + acData);
      } else {
        //noinspection ObjectToString
        JConfig.log().logMessage("Can't recognize ebay-queued data: " + ac.getData());
      }
    }
  }

  public void forceLogin() {
    mLogin.resetCookie();
    mLogin.getNecessaryCookie(true);
  }

  private void bidMsg(AuctionQObject ac) {
    AuctionAction ab = (AuctionAction)ac.getData();
    String bidResultString = ab.activate();
    String configBidMsg;

    if(ab.isSuccessful()) {
      configBidMsg = "prompt.hide_bidalert";
    } else {
      configBidMsg = "prompt.hide_bidfailalert";
    }

    MQFactory.getConcrete("Swing").enqueue("IGNORE " + configBidMsg + ' ' + bidResultString);
  }

  private static final int THIRTY_SECONDS = 30 * Constants.ONE_SECOND;
  private static final long TWO_MINUTES = Constants.ONE_MINUTE * 2;

  public void setSnipe(AuctionEntry snipeOn) {
    String identifier = snipeOn.getIdentifier();
    Date endDate = snipeOn.getEndDate();
    long snipeDelta = snipeOn.getSnipeTime();
    //  If we already have a snipe set for it, first cancel the old one, and then set up the new.
    _etqm.erase(identifier);

    if (endDate != null && endDate != Constants.FAR_FUTURE) {
      _etqm.add(identifier, mSnipeQueue, (endDate.getTime() - snipeDelta) - TWO_MINUTES);
      _etqm.add(identifier, mSnipeQueue, (endDate.getTime() - snipeDelta));
      _etqm.add(identifier, "drop",       endDate.getTime() + THIRTY_SECONDS);
    }

    MQFactory.getConcrete("my").enqueue("SNIPE " + identifier);
  }

  /**
   * Erase the pending snipe
   * @param identifier - The auction identifier of the listing whose snipe to cancel.
   */
  public void cancelSnipe(String identifier) {
    _etqm.erase(identifier);
    MQFactory.getConcrete("my").enqueue("CANCEL " + identifier);
  }

  public ebayServer(String site, String username, String password) {
    if(site == null) site = JConfig.queryConfiguration(getName() + ".browse.site");
    if(site == null) site = "0";
    constructServer(site, username, password);
  }

  /**
   * @brief Constructor for the eBay server object.
   */
  public ebayServer() {
    String username = JConfig.queryConfiguration(getName() + ".user", "default");
    String siteNumber = JConfig.queryConfiguration(getName() + ".browse.site");
    String password = JConfig.queryConfiguration(getName() + ".password", "default");

    constructServer(siteNumber, username, password);
  }

  /**
   * @brief Constructor for the eBay server object.
   * @param country - The country site to create an ebay server for.
   */
  public ebayServer(String country) {
    String username = JConfig.queryConfiguration(getName() + ".user", "default");
    String password = JConfig.queryConfiguration(getName() + ".password", "default");

    constructServer(country, username, password);
  }

  private void constructServer(String site, String username, String password) {
    if(site == null) {
      T = new TT("ebay.com");
    } else if(StringTools.isNumberOnly(site)) {
      T = new TT("ebay.com");
//      String countrySite = Constants.SITE_CHOICES[Integer.parseInt(site)];
//      T = new TT(countrySite);
    } else {
      T = new TT(site);
    }
    mCleaner = new ebayCleaner();
    mLogin = new ebayLoginManager(T, Constants.EBAY_SERVER_NAME, password, username);
    mSearcher = new ebaySearches(mCleaner, mLogin);
    if(JConfig.queryConfiguration("ebay.mock_bidding", "false").equals("true")) {
      mBidder = new Bidder() {
        public int buy(AuctionEntry ae, int quantity) {
          return BID_BOUGHT_ITEM;
        }

        public int bid(AuctionEntry inEntry, Currency inBid, int inQuantity) {
          return BID_ERROR_OUTBID;
        }

        //  These two are called by sniping.
        public JHTML.Form getBidForm(CookieJar cj, AuctionEntry inEntry, Currency inCurr) throws BadBidException {
          return new JHTML.Form("<form action=\"http://example.com\">");
        }

        public int placeFinalBid(CookieJar cj, JHTML.Form bidForm, AuctionEntry inEntry, Currency inBid, int inQuantity) {
          return BID_ERROR_OUTBID;
        }
      };
    } else {
      mBidder = new ebayBidder(T, mLogin);
    }

    _etqm = new eBayTimeQueueManager();
    eQueue = new TimerHandler(_etqm);
    eQueue.setName("eBay SuperQueue");
    //noinspection CallToThreadStartDuringObjectConstruction
    eQueue.start();

    mSnipeQueue = new SnipeListener();
    MQFactory.getConcrete(mSnipeQueue).registerListener(mSnipeQueue);
    MQFactory.getConcrete(this).registerListener(this);

    JConfig.registerListener(this);
  }

  /**
   * @brief Given a standard URL, strip it apart, and find the items
   * identifier from the standard eBay 'ViewItem' URL.
   *
   * @param urlStyle - The string to parse the identifier out of.
   *
   * @return - The identifier for the auction referenced by the URL
   * string passed in, or null if no identifier could be found.
   */
  public String extractIdentifierFromURLString(String urlStyle) {
    Pattern url = Pattern.compile(Externalized.getString("ebayServer.itemNumberMatch"));
    Matcher urlMatch = url.matcher(urlStyle);
    if(urlMatch.find()) {
        String itemNum = urlMatch.group(2);
        if(StringTools.isNumberOnly(itemNum)) return itemNum;
    }
    URL siteAddr = StringTools.getURLFromString(urlStyle);

    if(siteAddr != null) {
      String lastPart = siteAddr.toString();
      if(lastPart.indexOf(Externalized.getString("ebayServer.viewCmd")) != -1) {
        int index = lastPart.indexOf(Externalized.getString("ebayServer.viewCGI"));
        if(index != -1) {
          String aucId = lastPart.substring(index+ Externalized.getString("ebayServer.viewCGI").length());

          if (aucId.indexOf("&") != -1) {
            aucId = aucId.substring(0, aucId.indexOf("&"));
          }

          if (aucId.indexOf("#") != -1) {
            aucId = aucId.substring(0, aucId.indexOf("#"));
          }

          return(aucId);
        }
      }
    }

    try {
      URL pieces = new URL(urlStyle);
      String path = pieces.getPath();
      String digits = path.substring(path.lastIndexOf('/')+1);
      if(StringTools.isNumberOnly(digits)) return(digits);
    } catch (Exception e) {
      JConfig.log().logDebug("Failed to parse " + urlStyle + " as a URL");
    }

    JConfig.log().logDebug("extractIdentifierFromURLString failed.");
    return null;
  }

  /**
   * @brief Given a site-dependant item ID, get the string-form URL for that item.
   *
   * @param itemID - The item ID to get the URL for.
   *
   * @return - The real URL pointing to the item referenced by the passed in ID.
   */
  public String getStringURLFromItem(String itemID) {
    return Externalized.getString("ebayServer.protocol") + T.s("ebayServer.viewHost") + Externalized.getString("ebayServer.file") + '?' + Externalized.getString("ebayServer.viewCmd") + Externalized.getString("ebayServer.viewCGI") + itemID;
  }

  /**
   * @brief Get a string form URL that the user can browse to.
   *
   * This involves going to the users preferred country site.
   *
   * @param itemID - The item to browse w/r/t.
   *
   * @return - A string containing the way to browse to the users preferred international site.
   */
  public String getBrowsableURLFromItem(String itemID) {
    int browse_site = Integer.parseInt(JConfig.queryConfiguration(getName() + ".browse.site", "0"));

    return Externalized.getString("ebayServer.protocol") + Externalized.getString("ebayServer.browseHost") + Constants.SITE_CHOICES[browse_site] + Externalized.getString("ebayServer.file") + '?' + Externalized.getString("ebayServer.viewCmd") + Externalized.getString("ebayServer.viewCGI") + itemID;
  }

  /**
   * @brief Factory for generating an auction that contains the rules specific to eBay.
   *
   * @return - An object that can be used as an AuctionInfo object.
   */
  public SpecificAuction getNewSpecificAuction() {
    return new ebayAuction(T);
  }

  public StringBuffer getAuction(String id) throws FileNotFoundException {
    long pre = System.currentTimeMillis();
    StringBuffer sb = getAuction(getURLFromItem(id));
    long post = System.currentTimeMillis();
    if (JConfig.queryConfiguration("timesync.enabled", "true").equals("true")) {
      mPageRequestTime = (post - pre);
    }

    return sb;
  }

  public long getPageRequestTime() {
    return mPageRequestTime;
  }

  public synchronized CookieJar getNecessaryCookie(boolean force) {
    return mLogin.getNecessaryCookie(force);
  }

  public int bid(AuctionEntry inEntry, Currency inBid, int inQuantity) {
    return mBidder.bid(inEntry, inBid, inQuantity);
  }

  public int buy(AuctionEntry ae, int quantity) {
    return mBidder.buy(ae, quantity);
  }

  public boolean isDefaultUser() {
    return mLogin.isDefault();
  }

  /**
   * @brief Get the user's ID for this auction server.
   *
   * @return - The user's ID, as they entered it.
   */
  public String getUserId() {
    return mLogin.getUserId();
  }

  /**
   *  @brief Clear the search queue.
   *
   *  This queue is basically only used for starting searches.
   */
  public void cancelSearches() {
    MQFactory.getConcrete(this).clear();
  }

  /**
   * @brief Add search types to the search manager.
   *
   * Allows an auction server class to add unusual or site-specific
   * searches to the search manager.
   *
   * @param searchManager - The search manager to add these searches to.
   */
  public void addSearches(SearchManagerInterface searchManager) {
    Searcher s = searchManager.getSearchByName("My eBay");
    if(s == null) searchManager.addSearch("My Items", "My eBay", "", Constants.EBAY_SERVER_NAME, -1, 1);
  }

  private void doMyEbaySynchronize(String label) {
    MQFactory.getConcrete("Swing").enqueue("Synchronizing with My eBay...");
    mSearcher.getMyEbayItems(mLogin.getUserId(), label);
    MQFactory.getConcrete("Swing").enqueue("Done synchronizing with My eBay...");
  }

  /**
   * Load all items being sold by a given seller.
   *
   * @param searcher - The search to run
   * @param label - The category/label/tab to put it under
   */
  private void doGetSelling(Object searcher, String label) {
    String userId = ((Searcher)searcher).getSearch();
    MQFactory.getConcrete("Swing").enqueue("Getting Selling Items for " + userId);
    mSearcher.getSellingItems(userId, mLogin.getUserId(), label);
    MQFactory.getConcrete("Swing").enqueue("Done Getting Selling Items for " + userId);
  }

  private class SnipeListener implements MessageQueue.Listener {
    private Map<String, Snipe> mSnipeMap = new HashMap<String, Snipe>();

    /**
     * Retrieve a stored Snipe object if one exists (containing the cookie information),
     * or create a new one if one doesn't exist.
     *
     * @param identifier - The auction identifier to create the snipe for.
     *
     * @return - A Snipe object, either with a cookie object, or w/o. Returns
     * null if the AuctionEntry associated with the identifier does not exist
     * or is not sniped.
     */
    private Snipe getSnipe(String identifier) {
      AuctionEntry ae = EntryCorral.getInstance().takeForRead(identifier);
      if (ae == null || !ae.isSniped()) return null;

      Snipe snipe;
      if (mSnipeMap.containsKey(identifier)) {
        snipe = mSnipeMap.get(identifier);
      } else {
        snipe = new Snipe(mLogin, mBidder, ae);
        mSnipeMap.put(identifier, snipe);
      }
      return snipe;
    }

    public void messageAction(Object deQ) {
      String identifier = (String)deQ;
      Snipe snipe = getSnipe(identifier);
      if(snipe == null) return;

      int snipeResult = snipe.fire();
      switch (snipeResult) {
        case Snipe.RESNIPE:
          /**
           *  The formula for 'when' the next resnipe is, is a little complex.
           * It's all in the code, though.  If we're 3 seconds or less away,
           * give up.  Otherwise wait another 20% of the remaining time
           * (minimum of 3 seconds), and retry.
           */
          long snipeIn = snipe.getItem().getEndDate().getTime() - _etqm.getCurrentTime();
          if (snipeIn > Constants.THREE_SECONDS) {
            long retry_wait = (snipeIn / 10) * 2;
            if (retry_wait < Constants.THREE_SECONDS) retry_wait = Constants.THREE_SECONDS;

            _etqm.add(identifier, mSnipeQueue, _etqm.getCurrentTime() + retry_wait);
            break;
          }
          //  If there are less than 3 seconds left, give up by falling through to FAIL and DONE.
          JConfig.log().logDebug("Resnipes failed, and less than 3 seconds away.  Giving up.");
        case Snipe.FAIL:
          _etqm.erase(identifier);
          JConfig.log().logDebug("Snipe appears to have failed; cancelling.");
          snipe.getItem().snipeFailed();
          //  A failed snipe is a serious, hard error, and should fall through to being removed from any other lists.
        case Snipe.DONE:
          mSnipeMap.remove(identifier);
          break;
        case Snipe.SUCCESSFUL:
        default:
          break;
      }
    }
  }

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
                getServerTimeDelta() +
                getPageRequestTime());
      } else {
        mNow.setTime(System.currentTimeMillis());
      }
      mCal.setTime(mNow);
      //  Just in case it changes because of the setup.
      mNow.setTime(mCal.getTimeInMillis());
      return mLogin.getUserId() + '@' + getName() + ": " + Constants.remoteClockFormat.format(mNow);
    } else {
      mNow.setTime(System.currentTimeMillis());
      return mLogin.getUserId() + '@' + getName() + ": " + Constants.localClockFormat.format(mNow);
    }
  }

  public long getAdjustedTime() {
    return System.currentTimeMillis() + getServerTimeDelta() + getPageRequestTime();
  }

  public long getServerTimeDelta() {
    return mOfficialServerTimeDelta;
  }

  public TimeZone getOfficialServerTimeZone() {
    return mOfficialServerTimeZone;
  }

  public String getName() {
    return Constants.EBAY_SERVER_NAME;
  }

  public boolean validate(String username, String password) {
    return mLogin.validate(username, password);
  }

  /**
   * @brief Go to eBay and get their official time page, parse it, and
   * mark the difference between that time and our current time
   * internally, so we know how far off this machine's time is.
   *
   * @return - An object containing eBay's date, or null if we fail to
   *         load or parse the 'official time' page properly.
   */
  protected Date getOfficialTime() {
    UpdateBlocker.startBlocking();
    long localDateBeforePage = System.currentTimeMillis();
    String timeRequest = Externalized.getString("ebayServer.timeURL");

    //  Getting the necessary cookie here causes intense slowdown which fudges the time, badly.
    JHTML htmlDocument = new JHTML(timeRequest, null, mCleaner);
    ZoneDate result = null;

    String pageStep = htmlDocument.getNextContent();
    while (result == null && pageStep != null) {
      if (pageStep.equals(T.s("ebayServer.timePrequel1")) || pageStep.equals(T.s("ebayServer.timePrequel2"))) {
        result = StringTools.figureDate(htmlDocument.getNextContent(), Externalized.getString("ebayServer.officialTimeFormat"), false, false);
      }
      pageStep = htmlDocument.getNextContent();
    }

    UpdateBlocker.endBlocking();

    //  If we couldn't get a number, clear the page request time.
    if (result == null || result.getDate() == null) {
      mPageRequestTime = 0;
      //  This is bad...
      JConfig.log().logMessage(getName() + ": Error, can't accurately set delta to server's official time.");
      mOfficialServerTimeDelta = 1;
      return null;
    } else {
      long localDateAfterPage = System.currentTimeMillis();

      long reqTime = localDateAfterPage - localDateBeforePage;
      //  eBay's current time, minus the current time before we loaded the page, minus half the request-time
      //  tells how far off our system clock is to eBay.
      //noinspection MultiplyOrDivideByPowerOfTwo
      mOfficialServerTimeDelta = (result.getDate().getTime() - localDateBeforePage) - (reqTime / 2);
      //  mOSTD of 0 is a sentinel that we haven't gotten the official time yet, so if we magically get it, make it 1ms instead.
      if(mOfficialServerTimeDelta == 0) mOfficialServerTimeDelta = 1;
      if (result.getZone() != null) mOfficialServerTimeZone = (result.getZone());
      if(Math.abs(mOfficialServerTimeDelta) > Constants.ONE_DAY * 7) {
        MQFactory.getConcrete("Swing").enqueue("NOTIFY Your system time is off from eBay's by more than a week.");
      }
    }

    return result.getDate();
  }
}
