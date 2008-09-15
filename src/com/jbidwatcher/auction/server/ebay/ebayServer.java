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
import com.jbidwatcher.util.config.ErrorManagement;
import com.jbidwatcher.ui.config.JConfigTab;
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
import com.jbidwatcher.auction.Bidder;

import java.net.URL;
import java.util.*;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.io.FileNotFoundException;

/** @noinspection OverriddenMethodCallInConstructor*/
public final class ebayServer extends AuctionServer implements MessageQueue.Listener,JConfig.ConfigListener {
  private final static String eBayDisplayName = "eBay";
  private final static String eBayServerName = "ebay";

  private static final int THREE_SECONDS = 3 * Constants.ONE_SECOND;

  /**
   * The human-readable name of the auction server.
   */
  private static String siteId = "ebay";

  /** @noinspection FieldAccessedSynchronizedAndUnsynchronized*/
  private eBayTimeQueueManager _etqm;
  private Searcher mSellerSearch = null;
  private ebaySearches mSearcher;
  private ebayLoginManager mLogin;

  /** @noinspection FieldCanBeLocal*/
  private TimerHandler eQueue;
  private Map<String, AuctionQObject> snipeMap = new HashMap<String, AuctionQObject>();

  /**< The full amount of time it takes to request a single page from this site. */
  private long mPageRequestTime =0;

  /**< The amount of time to adjust the system clock by, to make it be nearly second-accurate to eBay time. */
  private long mOfficialServerTimeDelta =0;

  /**< The time zone the auction server is in (for eBay this will be PST or PDT). */
  private TimeZone mOfficialServerTimeZone = null;
  private Date mNow = new Date();
  private GregorianCalendar mCal;
  private final static ebayCurrencyTables sCurrencies = new ebayCurrencyTables();
  private final ebayCleaner mCleaner;
  private Bidder mBidder;

  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    ebayServer that = (ebayServer) o;

    return getName().equals(that.getName()) && mLogin.equals(that.mLogin);
  }

  public int hashCode() {
    String user = mLogin.getUserId();
    String pass = mLogin.getPassword();

    int result = siteId.hashCode();
    result = 31 * result + (user != null ? user.hashCode() : 0);
    result = 31 * result + (pass != null ? pass.hashCode() : 0);

    return result;
  }

  private final static String[] sSiteChoices = {
    "ebay.com",
    "ebay.de",
    "ebay.ca",
    "ebay.co.uk",
    "tw.ebay.com",
    "ebay.es",
    "ebay.fr",
    "ebay.it",
    "ebay.com.au",
    "ebay.at",
    "benl.ebay.be",
    "ebay.nl",
    "ebay.com.sg",
    "ebaysweden.com",
    "ebay.ch",
    "befr.ebay.be",
    "ebay.ie"};

  /** @noinspection RedundantIfStatement*/
  public boolean doHandleThisSite(URL checkURL) {
    if(checkURL == null) return false;
    if( (checkURL.getHost().startsWith(Externalized.getString("ebayServer.detectionHost"))) ) return true;
    if( (checkURL.getHost().startsWith(Externalized.getString("ebayServer.TaiwanDetectionHost"))) ) return true;
    if( (checkURL.getHost().startsWith(Externalized.getString("ebayServer.SpainDetectionHost"))) ) return true;

    return false;
  }

  public Currency getMinimumBidIncrement(Currency currentBid, int bidCount) {
    return sCurrencies.getMinimumBidIncrement(currentBid, bidCount);
  }

  public void updateConfiguration() {
    mLogin.setUserId(JConfig.queryConfiguration(getName() + ".user", "default"));
    mLogin.setPassword(JConfig.queryConfiguration(getName() + ".password", "default"));

    if(!mLogin.isDefault()) {
      SearchManager.getInstance().deleteSearch("My Selling Items");
      mSellerSearch = SearchManager.getInstance().buildSearch(System.currentTimeMillis(), "Seller", "My Selling Items", mLogin.getUserId(), getName(), null, 1);
      mSellerSearch.setCategory("selling");
      SearchManager.getInstance().addSearch(mSellerSearch);
    }
  }

  private class eBayTimeQueueManager extends TimeQueueManager {
    public long getCurrentTime() {
      return super.getCurrentTime() + getServerTimeDelta();
    }
  }

  /**
   * @brief Return the UI tab used to configure eBay-specific information.
   *
   * @return - A new tab to be added to the configuration display.
   */
  public List<JConfigTab> getConfigurationTabs() {
    List<JConfigTab> tabs = new ArrayList<JConfigTab>();
    //  Always return a new one, to fix a problem on first startup.
    tabs.add(new JConfigEbayTab(eBayDisplayName, sSiteChoices));
    return tabs;
  }

  /**
   * @brief Build a menu that can be added to the JBidwatcher standard
   * menu, to do eBay-specific things.
   *
   */
  public ServerMenu establishMenu() {
    ebayServerMenu esm = new ebayServerMenu(eBayDisplayName, 'b');
    esm.initialize();

    return esm;
  }

  /**
   * @brief Determine if an identifier looks like an eBay ID.
   *
   * For now, this just determines that it's pure numbers.  It should
   * be 8-11 digits long, also, but we're not that specific right now.
   *
   * @param auctionId - The auction id to test for 'eBay compatibility'.
   *
   * @return - true if the item looks like it's an eBay id, false otherwise.
   */
  public boolean checkIfIdentifierIsHandled(String auctionId) {
    return auctionId != null && StringTools.isNumberOnly(auctionId);
  }

  /**
   * @brief Very simplistic check to see if the current user is the
   * high bidder on a Dutch item.
   *
   * This only works, really, on closed items, I believe.  It shows
   * you as a 'winner' always, otherwise.
   *
   * @param inAE - The auction entry to check.
   *
   * @return - true if the user is one of the high bidders on a dutch item, false otherwise.
   */
  public boolean isHighDutch(AuctionEntry inAE) {
    String dutchWinners = Externalized.getString("ebayServer.protocol") + Externalized.getString("ebayServer.dutchRequestHost") + Externalized.getString("ebayServer.V3WS3File") + Externalized.getString("ebayServer.viewDutch") + inAE.getIdentifier();
    CookieJar cj = mLogin.getNecessaryCookie(false);
    String userCookie = null;
    if (cj != null) userCookie = cj.toString();

    JHTML htmlDocument = new JHTML(dutchWinners, userCookie, mCleaner);
    String matchedName = null;
    if(htmlDocument.isLoaded()) {
      matchedName = htmlDocument.getNextContentAfterContent(mLogin.getUserId());
    }

    return matchedName != null;
  }

  public void updateHighBid(AuctionEntry ae) {
    String bidHistory = Externalized.getString("ebayServer.protocol") + Externalized.getString("ebayServer.bidHost") + Externalized.getString("ebayServer.V3file") + Externalized.getString("ebayServer.viewBidsCGI") + ae.getIdentifier();
    CookieJar cj = mLogin.getNecessaryCookie(false);
    String userCookie = null;
    if (cj != null) userCookie = cj.toString();
    JHTML htmlDocument = new JHTML(bidHistory, userCookie, mCleaner);

    if(htmlDocument.isLoaded()) {
      String newCurrency = htmlDocument.getNextContentAfterContent(mLogin.getUserId());

      //  If we couldn't find anything, ditch, because we're probably wrong that the user is in the high bid table.
      if(newCurrency == null) return;

      //  If we're dealing with feedback, skip it.
      if(StringTools.isNumberOnly(newCurrency)) newCurrency = htmlDocument.getNextContent(); //  Skip feedback number
      int bidCount = 1;

      //  Check the next two columns for acceptable values.
      for(int i=0; i<2; i++) {
        Currency highBid = Currency.getCurrency(newCurrency);
        if(!highBid.isNull()) {
          if(ae.isDutch()) {
            String quant = htmlDocument.getNextContent();
            try {
              bidCount = Integer.parseInt(quant);
            } catch(NumberFormatException ignored) {
              //  We don't care what happened, that it's not a number means the bid count is 1.
              bidCount = 1;
            }
          }
          try {
            if(!ae.isBidOn() || ae.getBid().less(highBid)) {
              ae.setBid(highBid);
              ae.setBidQuantity(bidCount);
              ae.saveDB();
            }
          } catch(Currency.CurrencyTypeException cte) {
            //  Bad things happen here.  Ignore it for now.
          }
        }
        newCurrency = htmlDocument.getNextContent();
      }
    }
  }

  /**
   * @brief Given a server name, determine if we would normally handle requests for items on that server.
   *
   * @param serverName - The server name to check.
   *
   * @return - true if it is a server this class should handle, false otherwise.
   */
  public boolean checkIfSiteNameHandled(String serverName) {
    return(serverName.equalsIgnoreCase(eBayServerName));
  }

  /**
   * @brief Process an action, based on messages passed through our internal queues.
   */
  public void messageAction(Object deQ) {
    AuctionQObject ac = (AuctionQObject)deQ;
    String failString = null;

    switch(ac.getCommand()) {
      case AuctionQObject.LOAD_URL:
        mSearcher.loadAllFromURLString(ac.getData(), ac.getLabel());
        return;
      case AuctionQObject.LOAD_SEARCH:
        mSearcher.loadSearchString(ac.getData(), ac.getLabel(), false);
        return;
      case AuctionQObject.LOAD_TITLE:
        mSearcher.loadSearchString(ac.getData(), ac.getLabel(), true);
        return;
      case AuctionQObject.LOAD_SELLER:
        doGetSelling(ac.getData(), ac.getLabel());
        return;
      case AuctionQObject.LOAD_MYITEMS:
        if(mLogin.isDefault()) {
          failString = Externalized.getString("ebayServer.cantLoadWithoutUsername1") + " " + getName() + Externalized.getString("ebayServer.cantLoadWithoutUsername2");
        } else {
          doMyEbaySynchronize(ac.getLabel());
          return;
        }
        break;
      case AuctionQObject.CANCEL_SNIPE:
        cancelSnipeMsg(ac);
        return;
      case AuctionQObject.SET_SNIPE:
        setSnipeMsg(ac);
        return;
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
          mLogin.resetCookie();
          mLogin.getNecessaryCookie(true);
          return;
        }
      }

      if(ac.getData().equals("Dump eBay Activity Queue")) {
        _etqm.dumpQueue();
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
        ErrorManagement.logMessage("Dequeue'd unexpected command or fell through: " + ac.getCommand() + ':' + acData);
      } else {
        //noinspection ObjectToString
        ErrorManagement.logMessage("Can't recognize ebay-queued data: " + ac.getData());
      }
    }
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

  private void setSnipeMsg(AuctionQObject ac) {
    AuctionEntry snipeOn = (AuctionEntry)ac.getData();
    AuctionQObject currentlyExists = snipeMap.get(snipeOn.getIdentifier());
    //  If we already have a snipe set for it, first cancel the old one, and then set up the new.
    if(currentlyExists != null) {
      _etqm.erase(currentlyExists);
      snipeMap.remove(snipeOn.getIdentifier());
    }

    long two_minutes = Constants.ONE_MINUTE*2;
    AuctionQObject payload = new AuctionQObject(AuctionQObject.SNIPE, new Snipe(mLogin, mBidder, snipeOn), null);

    _etqm.add(payload, "snipes", (snipeOn.getEndDate().getTime()-snipeOn.getSnipeTime())-two_minutes);
    _etqm.add(payload, "snipes", (snipeOn.getEndDate().getTime()-snipeOn.getSnipeTime()));
    snipeMap.put(snipeOn.getIdentifier(), payload);
  }

  private void cancelSnipeMsg(AuctionQObject ac) {
    AuctionEntry snipeCancel = (AuctionEntry)ac.getData();
    String id = snipeCancel.getIdentifier();
    AuctionQObject cancellable = snipeMap.get(id);

    _etqm.erase(cancellable);
    snipeMap.remove(id);
  }

  /**
   * @brief Constructor for the eBay server object.
   *
   * It's not a terribly good idea to have multiple of these, right
   * now, but it is probably not broken.  -- mrs: 18-September-2003 15:08
   */
  public ebayServer() {
    mCleaner = new ebayCleaner();
    mLogin = new ebayLoginManager(eBayServerName,
        JConfig.queryConfiguration(getName() + ".password", "default"),
        JConfig.queryConfiguration(getName() + ".user", "default"));
    mSearcher = new ebaySearches(mCleaner, mLogin);
    mBidder = new ebayBidder(mLogin);

    _etqm = new eBayTimeQueueManager();
    eQueue = new TimerHandler(_etqm);
    eQueue.setName("eBay SuperQueue");
    //noinspection CallToThreadStartDuringObjectConstruction
    eQueue.start();

    MQFactory.getConcrete("snipes").registerListener(new SnipeListener());
    MQFactory.getConcrete("ebay").registerListener(this);

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

    ErrorManagement.logDebug("extractIdentifierFromURLString failed.");
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
    return Externalized.getString("ebayServer.protocol") + Externalized.getString("ebayServer.viewHost") + Externalized.getString("ebayServer.file") + '?' + Externalized.getString("ebayServer.viewCmd") + Externalized.getString("ebayServer.viewCGI") + itemID;
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

    return Externalized.getString("ebayServer.protocol") + Externalized.getString("ebayServer.browseHost") + sSiteChoices[browse_site] + Externalized.getString("ebayServer.file") + '?' + Externalized.getString("ebayServer.viewCmd") + Externalized.getString("ebayServer.viewCGI") + itemID;
  }

  /**
   * @brief Factory for generating an auction that contains the rules specific to eBay.
   *
   * @return - An object that can be used as an AuctionInfo object.
   */
  public SpecificAuction getNewSpecificAuction() {
    return new ebayAuction();
  }

  /**
   * @brief Returns the amount of time it takes to retrieve a page
   * from the auction server.
   *
   * @return The amount of milliseconds it takes to get a simple page
   * from the auction server.
   */
  private long getSnipePadding() {
    return 1;
  }

  public StringBuffer getAuction(String id) {
    StringBuffer sb;

    try {
      long pre = System.currentTimeMillis();
      sb = getAuction(getURLFromItem(id));
      long post = System.currentTimeMillis();
      if (JConfig.queryConfiguration("timesync.enabled", "true").equals("true")) {
        mPageRequestTime = (post - pre);
      }
    } catch (FileNotFoundException ignored) {
      sb = null;
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
    MQFactory.getConcrete("ebay").clear();
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
    if(s == null) searchManager.addSearch("My Items", "My eBay", "", "ebay", -1, 1);
  }

  /**
   * @brief Get the list of bidders on an item.
   *
   * This is primarily useful for networks-of-interest searching.
   *
   * @param ae - The item you are interested in.
   *
   * @return - A list containing strings with the names of each
   * user who was interested in the item enough to bid.
   */
  public List<String> getBidderNames(AuctionEntry ae) {
    CookieJar cj = mLogin.getNecessaryCookie(false);
    String userCookie = null;
    if (cj != null) userCookie = cj.toString();
    JHTML htmlDocument = new JHTML(Externalized.getString("ebayServer.protocol") + Externalized.getString("ebayServer.bidderNamesHost") + Externalized.getString("ebayServer.file") + Externalized.getString("ebayServer.viewBidsCGI") + ae.getIdentifier(), userCookie, mCleaner);

    String curName = htmlDocument.getNextContentAfterContent(Externalized.getString("ebayServer.bidListPrequel"));

    if(curName == null) {
      ErrorManagement.logMessage("Problem with loaded page when getting bidder names for auction " + ae.getIdentifier());
      return null;
    }

    List<String> outNames = new ArrayList<String>();

    do {
      if(!outNames.contains(curName)) {
        outNames.add(curName);
      }
      curName = htmlDocument.getNextContent();
      while(curName != null && ! (curName.endsWith("PDT") || curName.endsWith("PST"))) {
        curName = htmlDocument.getNextContent();
      }
      if(curName != null) curName = htmlDocument.getNextContent();
      if(curName != null) {
        if(curName.indexOf(Externalized.getString("ebayServer.earlierCheck")) != -1) curName = null;
      }
    } while(curName != null);

    return outNames;
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
    public void messageAction(Object deQ) {
      AuctionQObject ac = (AuctionQObject) deQ;
      if (ac.getCommand() == AuctionQObject.SNIPE) {
        Snipe snipe = (Snipe) ac.getData();
        int snipeResult = snipe.fire();
        switch(snipeResult) {
          case Snipe.RESNIPE:
            /**
             *  The formula for 'when' the next resnipe is, is a little complex.
             * It's all in the code, though.  If we're 3 seconds or less away,
             * give up.  Otherwise wait another 20% of the remaining time
             * (minimum of 3 seconds), and retry.
             */
            long snipeIn = snipe.getItem().getEndDate().getTime() - _etqm.getCurrentTime();
            if(snipeIn > THREE_SECONDS) {
              long retry_wait = (snipeIn / 10) * 2;
              if(retry_wait < THREE_SECONDS) retry_wait = THREE_SECONDS;

              _etqm.erase(deQ);
              _etqm.add(deQ, "snipes", _etqm.getCurrentTime()+retry_wait);
            }
            break;
          case Snipe.FAIL:
            _etqm.erase(deQ);
            //  A failed snipe is a serious, hard error, and should fall through to being removed from the snipe map.
          case Snipe.DONE:
            snipeMap.remove(snipe.getItem().getIdentifier());
            break;
          case Snipe.SUCCESSFUL:
          default:
            break;
        }
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
    return System.currentTimeMillis() + getServerTimeDelta() + getPageRequestTime() + getSnipePadding();
  }

  public long getServerTimeDelta() {
    return mOfficialServerTimeDelta;
  }

  public TimeZone getOfficialServerTimeZone() {
    return mOfficialServerTimeZone;
  }

  public String getName() {
    return siteId;
  }

  public static String getSiteName() {
    return siteId;
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
      if (pageStep.equals(Externalized.getString("ebayServer.timePrequel1")) || pageStep.equals(Externalized.getString("ebayServer.timePrequel2"))) {
        result = StringTools.figureDate(htmlDocument.getNextContent(), Externalized.getString("ebayServer.officialTimeFormat"), false, false);
      }
      pageStep = htmlDocument.getNextContent();
    }

    UpdateBlocker.endBlocking();

    //  If we couldn't get a number, clear the page request time.
    if (result == null || result.getDate() == null) {
      mPageRequestTime = 0;
      //  This is bad...
      ErrorManagement.logMessage(getName() + ": Error, can't accurately set delta to server's official time.");
      mOfficialServerTimeDelta = 0;
      return null;
    } else {
      long localDateAfterPage = System.currentTimeMillis();

      long reqTime = localDateAfterPage - localDateBeforePage;
      //  eBay's current time, minus the current time before we loaded the page, minus half the request-time
      //  tells how far off our system clock is to eBay.
      //noinspection MultiplyOrDivideByPowerOfTwo
      mOfficialServerTimeDelta = (result.getDate().getTime() - localDateBeforePage) - (reqTime / 2);
      if (result.getZone() != null) mOfficialServerTimeZone = (result.getZone());
      if(Math.abs(mOfficialServerTimeDelta) > Constants.ONE_DAY * 7) {
        MQFactory.getConcrete("Swing").enqueue("NOTIFY Your system time is off from eBay's by more than a week.");
      }
    }

    return result.getDate();
  }

  /**
   * @brief Given a site-dependant item ID, get the URL for that item.
   *
   * @param itemID - The eBay item ID to get a net.URL for.
   *
   * @return - a URL to use to pull that item.
   */
  protected URL getURLFromItem(String itemID) {
    return (StringTools.getURLFromString(getStringURLFromItem(itemID)));
  }
}
