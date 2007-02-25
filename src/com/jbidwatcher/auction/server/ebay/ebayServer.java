package com.jbidwatcher.auction.server.ebay;

/*
 * Copyright (c) 2000-2007, CyberFOX Software, Inc. All Rights Reserved.
 *
 * Developed by mrs (Morgan Schweers)
 */

//  TODO -- Talk about a monstrosity.  This is FAR too large.  Split into a dozen files?

//  This is the concrete implementation of AuctionServer to handle
//  parsing eBay auction pages.  There should be *ZERO* eBay specific
//  logic outside this class.  A pipe-dream, perhaps, but it seems
//  mostly doable.

import com.jbidwatcher.platform.Platform;
import com.jbidwatcher.config.JConfig;
import com.jbidwatcher.config.JConfigTab;
import com.jbidwatcher.config.JBConfig;
import com.jbidwatcher.queue.*;
import com.jbidwatcher.util.html.JHTML;
import com.jbidwatcher.util.html.htmlToken;
import com.jbidwatcher.util.http.CookieJar;
import com.jbidwatcher.util.http.Http;
import com.jbidwatcher.util.*;
import com.jbidwatcher.util.Currency;
import com.jbidwatcher.ui.OptionUI;
import com.jbidwatcher.search.Searcher;
import com.jbidwatcher.search.SearchManager;
import com.jbidwatcher.search.SearchManagerInterface;
import com.jbidwatcher.auction.*;
import com.jbidwatcher.auction.server.AuctionServerManager;
import com.jbidwatcher.auction.server.AuctionServer;
import com.jbidwatcher.auction.server.AuctionServerInterface;
import com.jbidwatcher.auction.server.BadBidException;
import com.jbidwatcher.TimerHandler;
import com.jbidwatcher.Constants;
import com.jbidwatcher.xml.XMLElement;
import com.jbidwatcher.auction.ThumbnailManager;

import javax.swing.*;
import java.io.FileWriter;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.io.FileNotFoundException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.util.*;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.text.SimpleDateFormat;

/** @noinspection OverriddenMethodCallInConstructor*/
public final class ebayServer extends AuctionServer implements MessageQueue.Listener,CleanupHandler,JConfig.ConfigListener {
  private final static String eBayDisplayName = "eBay";
  private final static String eBayServerName = "ebay";

  private static final int THREE_SECONDS = 3 * Constants.ONE_SECOND;
  private static final int ITEMS_PER_PAGE = 100;
  private static final int YEAR_BASE = 1990;

  /**
   * The human-readable name of the auction server.
   */
  private String siteId = "ebay";
  private String userCfgString = null;
  private String passCfgString = null;

  private HashMap<String, Integer> mResultHash = null;
  private String mBidResultRegex = null;
  private Pattern mFindBidResult;

  /** @noinspection FieldAccessedSynchronizedAndUnsynchronized*/
  private volatile CookieJar mSignInCookie = null;
  private eBayTimeQueueManager _etqm;
  private Searcher mMyeBay = null;
  private Searcher mSellerSearch = null;

  /** @noinspection FieldCanBeLocal*/
  private TimerHandler eQueue;
  private Map<String, AuctionQObject> snipeMap = new HashMap<String, AuctionQObject>();
  private String mBadPassword = null;
  private String mBadUsername = null;

  /**< The amount of time it takes to request an item via their affiliate program. */
  private long mAffiliateRequestTime =0;

  /**< The full amount of time it takes to request a single page from this site. */
  private long mPageRequestTime =0;

  /**< The amount of time to adjust the system clock by, to make it be nearly second-accurate to eBay time. */
  private long mOfficialServerTimeDelta =0;

  /**< The time zone the auction server is in (for eBay this will be PST or PDT). */
  private TimeZone mOfficialServerTimeZone = null;
  private static GregorianCalendar sMidpointDate = new GregorianCalendar(YEAR_BASE, Calendar.JANUARY, 1);
  private Date mNow = new Date();
  private GregorianCalendar mCal;
  private final static ebayCurrencyTables sCurrencies = new ebayCurrencyTables();

  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    ebayServer that = (ebayServer) o;

    if (!siteId.equals(that.siteId)) return false;

    String user1 = JConfig.queryConfiguration(userCfgString);
    String pass1 = JConfig.queryConfiguration(passCfgString);
    String user2 = JConfig.queryConfiguration(that.userCfgString);
    String pass2 = JConfig.queryConfiguration(that.passCfgString);

    return !(user1 != null ? !user1.equals(user2) : user2 != null) &&
           !(pass1 != null ? !pass1.equals(pass2) : pass2 != null) &&
            (user1 == null || pass1 == null || user1.equals(user2) && pass1.equals(pass2));
  }

  public int hashCode() {
    String user = JConfig.queryConfiguration(userCfgString);
    String pass = JConfig.queryConfiguration(passCfgString);

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
    "es.ebay.com",
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

  public com.jbidwatcher.util.Currency getMinimumBidIncrement(com.jbidwatcher.util.Currency currentBid, int bidCount) {
    return sCurrencies.getMinimumBidIncrement(currentBid, bidCount);
  }

  public void updateConfiguration() {
    mSellerSearch = SearchManager.getInstance().buildSearch(System.currentTimeMillis(), "Seller", "My Selling Items", getUserId(), getName(), null, 0);
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
  public JConfigTab getConfigurationTab() {
    //  Always return a new one, to fix a problem on first startup.
    return new JConfigEbayTab(eBayDisplayName, sSiteChoices);
  }

  /**
   * @brief Build a menu that can be added to the JBidwatcher standard
   * menu, to do eBay-specific things.
   *
   */
  public void establishMenu() {
    ebayServerMenu esm = new ebayServerMenu(eBayDisplayName, 'b');
    esm.initialize();
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
    CookieJar cj = getNecessaryCookie(false);
    String userCookie = null;
    if (cj != null) userCookie = cj.toString();

    JHTML htmlDocument = new JHTML(dutchWinners, userCookie, this);
    String matchedName = null;
    if(htmlDocument.isLoaded()) {
      matchedName = htmlDocument.getNextContentAfterContent(getUserId());
    }

    return matchedName != null;
  }

  public void updateHighBid(AuctionEntry ae) {
    String bidHistory = Externalized.getString("ebayServer.protocol") + Externalized.getString("ebayServer.bidHost") + Externalized.getString("ebayServer.V3file") + Externalized.getString("ebayServer.viewBidsCGI") + ae.getIdentifier();
    CookieJar cj = getNecessaryCookie(false);
    String userCookie = null;
    if (cj != null) userCookie = cj.toString();
    JHTML htmlDocument = new JHTML(bidHistory, userCookie, this);

    if(htmlDocument.isLoaded()) {
      String newCurrency = htmlDocument.getNextContentAfterContent(getUserId());

      //  If we couldn't find anything, ditch, because we're probably wrong that the user is in the high bid table.
      if(newCurrency == null) return;

      //  If we're dealing with feedback, skip it.
      if(StringTools.isNumberOnly(newCurrency)) newCurrency = htmlDocument.getNextContent(); //  Skip feedback number
      int bidCount = 1;

      //  Check the next two columns for acceptable values.
      for(int i=0; i<2; i++) {
        com.jbidwatcher.util.Currency highBid = com.jbidwatcher.util.Currency.getCurrency(newCurrency);
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
            }
          } catch(com.jbidwatcher.util.Currency.CurrencyTypeException cte) {
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
   * @brief Load a URL in, find all hrefs on the page that point to an
   * auction item, and load them into the program.
   *
   * @param searcher - The Searcher object that contains the URL to load and search for items in.
   * @param label - What 'group' to label items retrieved this way as.
   */
  private void loadAllFromURLString(Object searcher, String label) {
    String urlStr = ((Searcher)searcher).getSearch();
    MQFactory.getConcrete("Swing").enqueue("Loading from URL " + urlStr);

    //noinspection MismatchedQueryAndUpdateOfCollection
    EbayAuctionURLPager pager = new EbayAuctionURLPager(urlStr, this, this);
    int results = 0;

    ListIterator li = pager.listIterator();

    while(li.hasNext()) {
      MQFactory.getConcrete("Swing").enqueue("Loading page " + li.nextIndex() + "/" + pager.size() + " from URL " + urlStr);

    	JHTML htmlDocument = (JHTML)li.next();
    	if(htmlDocument != null) {
    		results += addAllItemsOnPage(htmlDocument, label, !((Searcher)searcher).shouldSkipDeleted());
    	}
    }

    if(results == 0) {
      MQFactory.getConcrete("Swing").enqueue("Failed to load from URL " + urlStr);
    } else {
      MQFactory.getConcrete("Swing").enqueue("Done loading from URL " + urlStr);
    }
  }

  /**
   * @brief Given a search string, send it to eBay's search, and gather up the results.
   *
   * @param searcher - The Searcher object containing the string to search for.
   * @param label - What 'group' to label items retrieved this way as.
   * @param title_only - Should the search focus on the titles only, or titles and descriptions?
   */
  private void loadSearchString(Object searcher, String label, boolean title_only) {
    String search = ((Searcher)searcher).getSearch();
    //  This should be encode(search, "UTF-8"); but that's a 1.4+ feature!
    //  Ignore the deprecation warning for this one.
    String encodedSearch;
    try {
      encodedSearch = URLEncoder.encode(search, "UTF-8");
    } catch(UnsupportedEncodingException ignored) {
      encodedSearch = null;
      ErrorManagement.logMessage("Failed to search because of encoding transformation failure.");
    }
    int allResults = 0;

    if(encodedSearch != null) {
      MQFactory.getConcrete("Swing").enqueue("Searching for: " + search);
      String sacur = "";

      String currency = ((Searcher)searcher).getCurrency();
      if(currency != null) sacur = "&sacur=" + currency;

      String fullSearch;

      if (title_only) {
        fullSearch = Externalized.getString("ebayServer.searchURL1") + encodedSearch + sacur + Externalized.getString("ebayServer.searchURLNoDesc");
      } else {
        fullSearch = Externalized.getString("ebayServer.searchURL1") + encodedSearch + sacur + Externalized.getString("ebayServer.searchURL2");
      }
      int skipCount = 0;
      boolean done;

      do {
        done = true;

        CookieJar cj = getNecessaryCookie(false);
        String userCookie = null;
        if (cj != null) userCookie = cj.toString();
        JHTML htmlDocument = new JHTML(fullSearch, userCookie, this);
        if(htmlDocument.isLoaded()) {
          int pageResults = addAllItemsOnPage(htmlDocument, label, !((Searcher)searcher).shouldSkipDeleted());
          if(pageResults != 0) {
            if(pageResults >= ITEMS_PER_PAGE) {
              skipCount += ITEMS_PER_PAGE;
              fullSearch = new StringBuffer(Externalized.getString("ebayServer.searchURL1")).append(encodedSearch).append(sacur).append(title_only? Externalized.getString("ebayServer.searchURLNoDesc") : Externalized.getString("ebayServer.searchURL2")).append("&skip=").append(skipCount).toString();
              done = false;
            }

            allResults += pageResults;
          }
        }
      } while(!done);
    }

    if(allResults == 0) {
      MQFactory.getConcrete("Swing").enqueue("No results found for search: " + search);
    } else {
      MQFactory.getConcrete("Swing").enqueue("Done searching for: " + search);
    }
  }

  /**
   * @brief Process an action, based on messages passed through our internal queues.
   *
   * This function is required, as an implementor of MessageQueue.Listener.
   *
   */
  public void messageAction(Object deQ) {
    AuctionQObject ac = (AuctionQObject)deQ;
    String failString = null;
    boolean defaultUser = getUserId().equals("default");

    /**
     * Just load all listings on a specific URL.
     */
    switch(ac.getCommand()) {
      case AuctionQObject.LOAD_URL:
        loadAllFromURLString(ac.getData(), ac.getLabel());
        return;
      case AuctionQObject.LOAD_SEARCH:
        /**
         * Check for searches, and execute one if that's what is requested.
         */
        loadSearchString(ac.getData(), ac.getLabel(), false);
        return;
      case AuctionQObject.LOAD_TITLE:
        /**
         * Check for searches, and execute one if that's what is requested.
         */
        loadSearchString(ac.getData(), ac.getLabel(), true);
        return;
      case AuctionQObject.LOAD_SELLER:
        /**
         * Load all items being sold by a given seller.
         */
        doGetSelling(ac.getData(), ac.getLabel());
        return;
      case AuctionQObject.LOAD_MYITEMS:
        if(defaultUser) {
          failString = Externalized.getString("ebayServer.cantLoadWithoutUsername1") + getName() + Externalized.getString("ebayServer.cantLoadWithoutUsername2");
        } else {
          doMyEbaySynchronize(ac.getLabel());
          return;
        }
        break;
      case AuctionQObject.CANCEL_SNIPE:
        AuctionEntry snipeCancel = (AuctionEntry)ac.getData();
        String id = snipeCancel.getIdentifier();
        AuctionQObject cancellable = snipeMap.get(id);

        _etqm.erase(cancellable);
        snipeMap.remove(id);
        return;
      case AuctionQObject.SET_SNIPE:
        AuctionEntry snipeOn = (AuctionEntry)ac.getData();
        AuctionQObject currentlyExists = snipeMap.get(snipeOn.getIdentifier());
        //  If we already have a snipe set for it, first cancel the old one, and then set up the new.
        if(currentlyExists != null) {
          _etqm.erase(currentlyExists);
          snipeMap.remove(snipeOn.getIdentifier());
        }

        long two_minutes = Constants.ONE_MINUTE*2;
        AuctionQObject payload = new AuctionQObject(AuctionQObject.SNIPE, new Snipe(snipeOn), null);

        _etqm.add(payload, "snipes", (snipeOn.getEndDate().getTime()-snipeOn.getSnipeTime())-two_minutes);
        _etqm.add(payload, "snipes", (snipeOn.getEndDate().getTime()-snipeOn.getSnipeTime()));
        snipeMap.put(snipeOn.getIdentifier(), payload);
        return;
      case AuctionQObject.BID:
        AuctionAction ab = (AuctionAction)ac.getData();
        String bidResultString = ab.activate();
        String configBidMsg;

        if(ab.isSuccessful()) {
          configBidMsg = "prompt.hide_bidalert";
        } else {
          configBidMsg = "prompt.hide_bidfailalert";
        }

        MQFactory.getConcrete("Swing").enqueue("IGNORE " + configBidMsg + ' ' + bidResultString);

        AuctionsManager.getInstance().changed();
        return;
      default:
        //  It's okay if we don't recognize it.
    }

    if(ac.getData() != null) {
      /**
       * This calls back to here, by adding a message onto the queue,
       * but it will update the 'last run' time for the 'My eBay' search.
       */
      if(ac.getData().equals("Get My eBay Items")) {
        if(getMyEbay() != null) {
          getMyEbay().execute();
          return;
        }
        /**
         * From here on, everything requires a 'real' user id.  failString
         * gets set if it's not, as a custom message for the user based on
         * the action they are trying to do.
         */
        if(defaultUser) {
          failString = Externalized.getString("ebayServer.cantLoadWithoutUsername1") + getName() + Externalized.getString("ebayServer.cantLoadWithoutUsername2");
        } else {
          doMyEbaySynchronize(null);
          return;
        }
      }

      /**
       * Get items this user is selling.
       */
      if(ac.getData().equals("Get Selling Items")) {
        if(defaultUser) {
          failString = Externalized.getString("ebayServer.cantLoadSellerWithoutUser1") + getName() + Externalized.getString("ebayServer.cantLoadWithoutUsername2");
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
        if(defaultUser) {
          failString = Externalized.getString("ebayServer.cantUpdateCookieWithoutUser1") + getName() + Externalized.getString("ebayServer.cantLoadWithoutUsername2");
        } else {
          mSignInCookie = null;
          getNecessaryCookie(true);
          return;
        }
      }

      if(ac.getData().equals("[Dump eBay activity queue]")) {
        _etqm.dumpQueue();
        return;
      }
    }

    /**
     * If we've made a failure string, and we're using the default
     * user, then display the error, otherwise indicate that we got an
     * unexpected command.
     */
    if(failString != null && failString.length() != 0 && defaultUser) {
      JOptionPane.showMessageDialog(null, failString, "No auction account error", JOptionPane.PLAIN_MESSAGE);
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

  /**
   * @brief Constructor for the eBay server object.
   *
   * It's not a terribly good idea to have multiple of these, right
   * now, but it is probably not broken.  -- mrs: 18-September-2003 15:08
   */
  public ebayServer() {
    userCfgString = getName() + ".user";
    passCfgString = getName() + ".password";
    /**
     * Build a simple hashtable of results that bidding might get.
     * Not the greatest solution, but it's working okay.  A better one
     * would be great.
     */
    if(mResultHash == null) {
      mResultHash = new HashMap<String, Integer>();
      mResultHash.put("you are not permitted to bid on their listings.", BID_ERROR_BANNED);
      mResultHash.put("the item is no longer available because the auction has ended.", BID_ERROR_ENDED);
      mResultHash.put("cannot proceed", BID_ERROR_CANNOT);
      mResultHash.put("problem with bid amount", BID_ERROR_AMOUNT);
      mResultHash.put("your bid must be at least ", BID_ERROR_TOO_LOW);
      mResultHash.put("you have been outbid by another bidder", BID_ERROR_OUTBID);
      mResultHash.put("your bid is confirmed!", BID_DUTCH_CONFIRMED);
      mResultHash.put("you are bidding on this multiple item auction", BID_DUTCH_CONFIRMED);
      mResultHash.put("you are the high bidder on all items you bid on", BID_DUTCH_CONFIRMED);
      mResultHash.put("you are the current high bidder", BID_WINNING);
      mResultHash.put("you purchased the item", BID_WINNING);
      mResultHash.put("the reserve price has not been met", BID_ERROR_RESERVE_NOT_MET);
      mResultHash.put("your new total must be higher than your current total", BID_ERROR_TOO_LOW_SELF);
      mResultHash.put("this exceeds or is equal to your current bid", BID_ERROR_TOO_LOW_SELF);
      mResultHash.put("you bought this item", BID_BOUGHT_ITEM);
      mResultHash.put("you committed to buy", BID_BOUGHT_ITEM);
      mResultHash.put("congratulations! you won!", BID_BOUGHT_ITEM);
      mResultHash.put("account suspended", BID_ERROR_ACCOUNT_SUSPENDED);
      mResultHash.put("to enter a higher maximum bid, please enter", BID_ERROR_TOO_LOW_SELF);
      mResultHash.put("you are registered in a country to which the seller doesn.t ship.", BID_ERROR_WONT_SHIP);
      mResultHash.put("this seller has set buyer requirements for this item and only sells to buyers who meet those requirements.", BID_ERROR_REQUIREMENTS_NOT_MET);
      //      mResultHash.put("You are the current high bidder", new Integer(BID_SELFWIN));
    }

    //"If you want to submit another bid, your new total must be higher than your current total";
    StringBuffer superRegex = new StringBuffer("(");
    Iterator<String> it = mResultHash.keySet().iterator();
    while (it.hasNext()) {
      String key = it.next();
      superRegex.append(key);
      if(it.hasNext()) {
        superRegex.append('|');
      } else {
        superRegex.append(')');
      }
    }
    mBidResultRegex = "(?i)" + superRegex.toString();
    mFindBidResult = Pattern.compile(mBidResultRegex);
    mResultHash.put("sign in", BID_ERROR_CANT_SIGN_IN);

    _etqm = new eBayTimeQueueManager();
    eQueue = new TimerHandler(_etqm);
    eQueue.setName("eBay SuperQueue");
    //noinspection CallToThreadStartDuringObjectConstruction
    eQueue.start();

    MQFactory.getConcrete("snipes").registerListener(new SnipeListener());
    MQFactory.getConcrete("ebay").registerListener(this);

    JConfig.registerListener(this);
  }

  private static final String srcMatch = "(?i)src=\"([^\"]*?)\"";
  private static Pattern srcPat = Pattern.compile(srcMatch);

  private static final String dateMatch = "(?i)(Ends|end.time).([A-Za-z]+(.[0-9]+)+.[A-Z]+)";
  private static Pattern datePat = Pattern.compile(dateMatch);

  /**
   * @brief Go to eBay and get their official time page, parse it, and
   * mark the difference between that time and our current time
   * internally, so we know how far off this machine's time is.
   *
   * @return - An object containing eBay's date, or null if we fail to
   * load or parse the 'official time' page properly.
   */
  protected Date getOfficialTime() {
    Auctions.startBlocking();
    long localDateBeforePage = System.currentTimeMillis();
    String timeRequest = Externalized.getString("ebayServer.protocol") + Externalized.getString("ebayServer.host") + Externalized.getString("ebayServer.file") + Externalized.getString("ebayServer.timeCmd");

//  Getting the necessary cookie here causes intense slowdown which fudges the time, badly.
//    htmlDocument = new JHTML(timeRequest, getNecessaryCookie(false).toString(), this);
    JHTML htmlDocument = new JHTML(timeRequest, null, this);
    Date result = null;

    String pageStep = htmlDocument.getNextContent();
    while(result == null && pageStep != null) {
      if(pageStep.equals(Externalized.getString("ebayServer.timePrequel1")) || pageStep.equals(Externalized.getString("ebayServer.timePrequel2"))) {
        result = figureDate(htmlDocument.getNextContent(), Externalized.getString("ebayServer.officialTimeFormat"), false);
      }
      pageStep = htmlDocument.getNextContent();
    }

    Auctions.endBlocking();

    //  If we couldn't get a number, clear the page request time.
    if(result == null) {
      mPageRequestTime = 0;
      //  This is bad...
      ErrorManagement.logMessage(getName() + ": Error, can't accurately set delta to server's official time.");
      mOfficialServerTimeDelta = 0;
    } else {
      long localDateAfterPage = System.currentTimeMillis();

      long reqTime = localDateAfterPage - localDateBeforePage;
      //  eBay's current time, minus the current time before we loaded the page, minus half the request-time
      //  tells how far off our system clock is to eBay.
      //noinspection MultiplyOrDivideByPowerOfTwo
      mOfficialServerTimeDelta = (result.getTime() - localDateBeforePage) - (reqTime / 2);
    }
    return result;
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
   * @brief Given a site-dependant item ID, get the URL for that item.
   *
   * @param itemID - The eBay item ID to get a net.URL for.
   *
   * @return - a URL to use to pull that item.
   */
  protected URL getURLFromItem(String itemID) {
    return(StringTools.getURLFromString(getStringURLFromItem(itemID)));
  }

  /**
   * @brief Factory for generating an auction that contains the rules specific to eBay.
   *
   * @return - An object that can be used as an AuctionInfo object.
   */
  public SpecificAuction getNewSpecificAuction() {
    return new ebayAuction();
  }

  public JHTML.Form getBidForm(CookieJar cj, AuctionEntry inEntry, com.jbidwatcher.util.Currency inCurr, int inQuant) throws BadBidException {
    String bidRequest = Externalized.getString("ebayServer.protocol") + Externalized.getString("ebayServer.bidHost") + Externalized.getString("ebayServer.V3file");
    String bidInfo;
    if(inEntry.isDutch()) {
      bidInfo = Externalized.getString("ebayServer.bidCmd") + "&co_partnerid=" + Externalized.getString("ebayServer.itemCGI") + inEntry.getIdentifier() +
                "&fb=2" + Externalized.getString("ebayServer.quantCGI") + inQuant +
                Externalized.getString("ebayServer.bidCGI") + inCurr.getValue();
    } else {
      bidInfo = Externalized.getString("ebayServer.bidCmd") + "&co_partnerid=" + Externalized.getString("ebayServer.itemCGI") + inEntry.getIdentifier() + "&fb=2" +
                Externalized.getString("ebayServer.bidCGI") + inCurr.getValue();
    }
    StringBuffer loadedPage = null;
    JHTML htmlDocument = null;

    try {
      String pageName = bidRequest + '?' + bidInfo;
      boolean checked_signon = false;
      boolean checked_reminder = false;
      boolean done = false;
      boolean post = false;
      while (!done) {
        done = true;

        if(JConfig.debugging) inEntry.setLastStatus("Loading bid request...");
        URLConnection huc = cj.getAllCookiesFromPage(pageName, null, post);
        post = false;
        //  We failed to load, entirely.  Punt.
        if (huc == null) return null;

        loadedPage = Http.receivePage(huc);
        //  We failed to load.  Punt.
        if (loadedPage == null) return null;

        htmlDocument = new JHTML(loadedPage);
        JHTML.Form bidForm = htmlDocument.getFormWithInput("key");
        if(bidForm != null) {
          if(JConfig.debugging) inEntry.setLastStatus("Done loading bid request, got form...");
          return bidForm;
        }

        if(!checked_signon) {
          checked_signon = true;
          String signOn = htmlDocument.getFirstContent();
          if (signOn != null) {
            ErrorManagement.logDebug("Checking sign in as bid key load failed!");
            if (signOn.equalsIgnoreCase("Sign In")) {
              //  This means we somehow failed to keep the login in place.  Bad news, in the middle of a snipe.
              ErrorManagement.logDebug("Being prompted again for sign in, retrying.");
              if(JConfig.debugging) inEntry.setLastStatus("Not done loading bid request, got re-login request...");
              mSignInCookie = null;
              getNecessaryCookie(true);
              if(JConfig.debugging) inEntry.setLastStatus("Done re-logging in, retrying load bid request.");
              done = false;
            }
          }
        }

        if(!checked_reminder) {
          if(htmlDocument.grep("Buying.Reminder") != null) {
            JHTML.Form continueForm = htmlDocument.getFormWithInput("firedFilterId");
            if(continueForm != null) {
              inEntry.setLastStatus("Trying to 'continue' for the actual bid.");
              pageName = continueForm.getCGI();
              pageName = pageName.replaceFirst("%[A-F][A-F0-9]%A0", "%A0");
              post = false;
            }
            checked_reminder = true;
          }
        }
      }
    } catch (IOException e) {
      ErrorManagement.handleException("Failure to get the bid key!  BID FAILURE!", e);
    }

    if(htmlDocument != null) {
      String signOn = htmlDocument.getFirstContent();
      if(signOn != null && signOn.equalsIgnoreCase("Sign In")) throw new BadBidException("sign in", BID_ERROR_CANT_SIGN_IN);
      String errMsg = htmlDocument.grep(mBidResultRegex);
      if(errMsg != null) {
        Matcher bidMatch = mFindBidResult.matcher(errMsg);
        bidMatch.find();
        String matched_error = bidMatch.group().toLowerCase();
        throw new BadBidException(matched_error, mResultHash.get(matched_error));
      }
    }

    if(JConfig.debugging) inEntry.setLastStatus("Failed to bid. 'Show Last Error' from context menu to see the failure page from the bid attempt.");
    inEntry.setErrorPage(loadedPage);

    //  We don't recognize this error.  Damn.  Log it and freak.
    ErrorManagement.logFile(bidInfo, loadedPage);
    return null;
  }

  /**
   * @brief Returns the amount of time it takes to retrieve a page
   * from the auction server.
   *
   * @return The amount of milliseconds it takes to get a simple page
   * from the auction server.
   */
  public long getSnipePadding() {
    if(JBConfig.doAffiliate(0)) {
      return 3;
    }
    return 1;
  }

  public StringBuffer getAuction(AuctionEntry ae, String id) {
    long end_time = 1;
    //  TODO -- Replace with a global lookup for auction entry by id.
    if(ae != null) {
      Date end = ae.getEndDate();
      if(end != null) end_time = end.getTime();
    }
    StringBuffer sb = null;
    if(JBConfig.doAffiliate(end_time) && allowAffiliate()) {
      CookieJar cj = getCookie();
      if (cj != null) {
        try {
          long pre = System.currentTimeMillis();
          sb = AffiliateRetrieve.getAuctionViaAffiliate(cj, id);
          long post = System.currentTimeMillis();
          mAffiliateRequestTime = (post - pre);
        } catch (CookieJar.CookieException cje) {
          //  Cookie failure...  Ignore it and do a regular get.
        }
      }
    }

    if(sb == null || sb.indexOf("eBay item") == -1) {
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
    }

    return sb;
  }

  public long getPageRequestTime() {
    return mPageRequestTime;
  }

  private boolean allowAffiliate() {
    ErrorManagement.logDebug("NOT allowing affiliate mode.");
    return false;
  }

  private CookieJar getCookie() {
    ErrorManagement.logDebug("NOT getting cookie.");
    return null;
  }

  public int buy(AuctionEntry ae, int quantity) {
    String buyRequest = "http://offer.ebay.com/ws/eBayISAPI.dll?MfcISAPICommand=BinConfirm&fb=1&co_partnerid=&item=" + ae.getIdentifier() + "&quantity=" + quantity;

    //  This updates the cookies with the affiliate information, if it's not a test auction.
    if(ae.getTitle().toLowerCase().indexOf("test") == -1) {
      if(JBConfig.doAffiliate(ae.getEndDate().getTime())) {
        //  Ignoring the result as it's just called to trigger affiliate mode.
        getAuction(ae, ae.getIdentifier());
      }
    }

    StringBuffer sb;

    try {
      sb = getNecessaryCookie(false).getAllCookiesAndPage(buyRequest, null, false);
      JHTML doBuy = new JHTML(sb);
      JHTML.Form buyForm = doBuy.getFormWithInput("uiid");

      if (buyForm != null) {
        buyForm.delInput("BIN_button");
        CookieJar cj = getNecessaryCookie(false);
        StringBuffer loadedPage = cj.getAllCookiesAndPage(buyForm.getCGI(), buyRequest, false);
        if (loadedPage == null) return BID_ERROR_CONNECTION;
        return handlePostBidBuyPage(cj, loadedPage, buyForm, ae);
      }
    } catch (CookieJar.CookieException ignored) {
      return BID_ERROR_CONNECTION;
    } catch (UnsupportedEncodingException uee) {
      ErrorManagement.handleException("UTF-8 not supported locally, can't URLEncode buy form.", uee);
      return BID_ERROR_CONNECTION;
    }

    ae.setErrorPage(sb);
    return BID_ERROR_UNKNOWN;
  }

  /**
   * @brief Perform the entire bidding process on an item.
   *
   * @param inEntry - The item to bid on.
   * @param inBid - The amount to bid.
   * @param inQuantity - The number of items to bid on.
   *
   * @return - A bid response code, or BID_ERROR_UNKNOWN if we can't
   * figure out what happened.
   */
  public int bid(AuctionEntry inEntry, com.jbidwatcher.util.Currency inBid, int inQuantity) {
    Auctions.startBlocking();
    if(JConfig.queryConfiguration("sound.enable", "false").equals("true")) MQFactory.getConcrete("sfx").enqueue("/audio/bid.mp3");

    try {
      //  If it's not closing within the next minute, then go ahead and try for the affiliate mode.
      if(inEntry.getEndDate().getTime() > (System.currentTimeMillis() + Constants.ONE_MINUTE)) {
        safeGetAffiliate(getNecessaryCookie(false), inEntry);
      }
    } catch (CookieJar.CookieException ignore) {
      //  We don't care that much about connection refused in this case.
    }
    JHTML.Form bidForm;

    try {
      bidForm = getBidForm(getNecessaryCookie(false), inEntry, inBid, inQuantity);
    } catch(BadBidException bbe) {
      Auctions.endBlocking();
      return bbe.getResult();
    }

    if (bidForm != null) {
      int rval = placeFinalBid(getNecessaryCookie(false), bidForm, inEntry, inBid, inQuantity);
      Auctions.endBlocking();
      return rval;
    }
    ErrorManagement.logMessage("Bad/nonexistent key read in bid, or connection failure!");

    Auctions.endBlocking();
    return BID_ERROR_UNKNOWN;
  }

  public int placeFinalBid(CookieJar cj, JHTML.Form bidForm, AuctionEntry inEntry, com.jbidwatcher.util.Currency inBid, int inQuantity) {
    String bidRequest = Externalized.getString("ebayServer.protocol") + Externalized.getString("ebayServer.bidHost") + Externalized.getString("ebayServer.V3file");
    String bidInfo = Externalized.getString("ebayServer.bidCmd") + Externalized.getString("ebayServer.itemCGI") + inEntry.getIdentifier() +
        Externalized.getString("ebayServer.quantCGI") + inQuantity +
        Externalized.getString("ebayServer.bidCGI") + inBid.getValue();
    String bidURL = bidRequest + '?' + bidInfo;

    bidForm.delInput("BIN_button");
    StringBuffer loadedPage = null;

    //  This SHOULD be POSTed, but only works if sent with GET.
    try {
      if (JConfig.debugging) inEntry.setLastStatus("Submitting bid form.");
      loadedPage = cj.getAllCookiesAndPage(bidForm.getCGI(), bidURL, false);
      if (JConfig.debugging) inEntry.setLastStatus("Done submitting bid form.");
    } catch (UnsupportedEncodingException uee) {
      ErrorManagement.handleException("UTF-8 not supported locally, can't URLEncode bid form.", uee);
    } catch (CookieJar.CookieException ignored) {
      return BID_ERROR_CONNECTION;
    }

    if (loadedPage == null) {
      return BID_ERROR_CONNECTION;
    }
    return handlePostBidBuyPage(cj, loadedPage, bidForm, inEntry);
  }

  public void safeGetAffiliate(CookieJar cj, AuctionEntry inEntry) throws CookieJar.CookieException {
    //  This updates the cookies with the affiliate information, if it's not a test auction.
    if(inEntry.getTitle().toLowerCase().indexOf("test") == -1) {
      if(JBConfig.doAffiliate(inEntry.getEndDate().getTime())) {
        if(JConfig.debugging) inEntry.setLastStatus("Loading item...");
        getAuction(inEntry, inEntry.getIdentifier());
        if(JConfig.debugging) inEntry.setLastStatus("Done loading item...");
      }
    }
  }

  private int handlePostBidBuyPage(CookieJar cj, StringBuffer loadedPage, JHTML.Form bidForm, AuctionEntry inEntry) {
    if(JConfig.debugging) inEntry.setLastStatus("Loading post-bid data.");
    JHTML htmlDocument = new JHTML(loadedPage);

    if(htmlDocument.grep("Buying.Reminder") != null) {
      JHTML.Form continueForm = htmlDocument.getFormWithInput("firedFilterId");
      if(continueForm != null) {
        try {
          inEntry.setLastStatus("Trying to 'continue' to the bid result page.");
          String cgi = continueForm.getCGI();
          //  For some reason, the continue page represents the currency as
          //  separated from the amount with a '0xA0' character.  When encoding,
          //  this becomes...broken somehow, and adds an extra character, which
          //  does not work when bidding.
          cgi = cgi.replaceFirst("%[A-F][A-F0-9]%A0", "%A0");
          URLConnection huc = cj.getAllCookiesFromPage(cgi, null, false);
          //  We failed to load, entirely.  Punt.
          if (huc == null) return BID_ERROR_CONNECTION;

          loadedPage = Http.receivePage(huc);
          //  We failed to load.  Punt.
          if (loadedPage == null) return BID_ERROR_CONNECTION;

          htmlDocument = new JHTML(loadedPage);
        } catch(Exception ignored) {
          return BID_ERROR_CONNECTION;
        }
      }
    }

    String errMsg = htmlDocument.grep(mBidResultRegex);
    if (errMsg != null) {
      Matcher bidMatch = mFindBidResult.matcher(errMsg);
      bidMatch.find();
      String matched_error = bidMatch.group().toLowerCase();
      Integer bidResult = mResultHash.get(matched_error);

      if(inEntry.getTitle().toLowerCase().indexOf("test") == -1) {
        if(JBConfig.doAffiliate(inEntry.getEndDate().getTime())) {
          List<String> images = htmlDocument.getAllImages();
          for (String tag : images) {
            Matcher tagMatch = srcPat.matcher(tag);
            if (tagMatch.find()) {
              int retry = 2;
              do {
                StringBuffer result = null;
                try {
                  result = getNecessaryCookie(false).getAllCookiesAndPage(tagMatch.group(1), "http://offer.ebay.com/ws/eBayISAPI.dll", false);
                } catch (CookieJar.CookieException ignored) {
                  //  Ignore connection refused errors.
                }
                if (result == null) {
                  retry--;
                } else {
                  retry = 0;
                }
              } while (retry != 0);
            }
          }
        }
      }

      if(JConfig.debugging) inEntry.setLastStatus("Done loading post-bid data.");

      if(bidResult != null) return bidResult;
    }

    // Skipping the userID and Password, so this can be submitted as
    // debugging info.
    bidForm.setText("user", "HIDDEN");
    bidForm.setText("pass", "HIDDEN");
    String safeBidInfo = "";
    try {
      safeBidInfo = bidForm.getCGI();
    } catch(UnsupportedEncodingException uee) {
      ErrorManagement.handleException("UTF-8 not supported locally, can't URLEncode CGI for debugging.", uee);
    }

    if(JConfig.debugging) inEntry.setLastStatus("Failed to load post-bid data. 'Show Last Error' from context menu to see the failure page from the post-bid page.");
    inEntry.setErrorPage(loadedPage);

    ErrorManagement.logFile(safeBidInfo, loadedPage);
    return BID_ERROR_UNKNOWN;
  }

  /**
   * @brief Returns the set of cookies necessary to be posted in order
   * to retrieve auctions.  getNecessaryCookie() can return null when
   * the process of logging in can't be done, for whatever reason.
   * (For instance, eBay's 2-3 hour downtime on Friday mornings @
   * 1-3am.)
   *
   * @param force - Force an update of the cookie, even if it's not
   * time yet.
   *
   * @return - A cookie jar of all the necessary cookies to do eBay connections.
   */
  public synchronized CookieJar getNecessaryCookie(boolean force) {
    if(mSignInCookie == null || force) {
      mSignInCookie = getSignInCookie(mSignInCookie);
    }

    return(mSignInCookie);
  }

  /**
   * @brief Debugging function to dump a string buffer out to a file.
   *
   * This is used for 'emergency' debugging efforts.
   *
   * @param fname - The filename to output to.
   * @param sb - The StringBuffer to dump out.
   */
  private static void dump2File(String fname, StringBuffer sb) {
    FileWriter fw = null;
    try {
      fw = new FileWriter(fname);

      fw.write(sb.toString());
    } catch(IOException ioe) {
      ErrorManagement.handleException("Threw exception in dump2File!", ioe);
    } finally {
      if(fw != null) try { fw.close(); } catch(IOException ignored) { /* I don't care about exceptions on close. */ }
    }
  }

  private URLConnection checkFollowRedirector(URLConnection current, CookieJar cj, String lookFor) throws IOException {
    StringBuffer signed_in = Http.receivePage(current);
    if (JConfig.queryConfiguration("debug.filedump", "false").equals("true")) dump2File("sign_in-a1.html", signed_in);

    //  Parse the redirector, and find the URL that points to the adult
    //  confirmation page.
    JHTML redirector = new JHTML(signed_in);
    if(checkSecurityConfirmation(redirector)) return null;
    return checkHTMLFollowRedirect(redirector, lookFor, cj);
  }

  private static URLConnection checkHTMLFollowRedirect(JHTML redirectPage, String lookFor, CookieJar cj) {
    redirectPage.reset();
    List<String> allURLs = redirectPage.getAllURLsOnPage(false);
    for (String url : allURLs) {
      //  If this URL has the text we're looking for in its body someplace, that's the one we want.
      if (url.indexOf(lookFor) != -1) {
        //  Replace nasty quoted amps with single-amps.
        url = url.replaceAll("&amp;", "&");
        url = url.replaceAll("\n", "");
        if (lookFor.equals("BidBin")) {
          int step = url.indexOf("BidBinInfo=");
          if (step != -1) {
            step += "BidBinInfo=".length();

            try {
              String encodedURL = URLEncoder.encode(url.substring(step), "UTF-8");
              //noinspection StringContatenationInLoop
              url = url.substring(0, step) + encodedURL;
            } catch (UnsupportedEncodingException ignored) {
              ErrorManagement.logMessage("Failed to build a URL because of encoding transformation failure.");
            }
          }
        }
        //  Now get the actual page...
        return cj.getAllCookiesFromPage(url, null, false);
      }
    }

    return null;
  }

  //  Get THAT page, which is actually (usually) a 'redirector' page with a meta-refresh
  //  and a clickable link in case meta-refresh doesn't work.
  private boolean getAdultRedirector(URLConnection uc_signin, CookieJar cj) throws IOException {
    uc_signin = checkFollowRedirector(uc_signin, cj, "Adult");
    return uc_signin != null && getAdultConfirmation(uc_signin, cj);

  }

  private static boolean getAdultConfirmation(URLConnection uc_signin, CookieJar cj) throws IOException {
    StringBuffer confirm = Http.receivePage(uc_signin);
    if (JConfig.queryConfiguration("debug.filedump", "false").equals("true")) dump2File("sign_in-a2.html", confirm);
    JHTML confirmPage = new JHTML(confirm);

    List<JHTML.Form> confirm_forms = confirmPage.getForms();
    for(JHTML.Form finalForm : confirm_forms) {
      if (finalForm.hasInput("MfcISAPICommand")) {
        uc_signin = cj.getAllCookiesFromPage(finalForm.getCGI(), null, false);
        StringBuffer confirmed = Http.receivePage(uc_signin);
        if (JConfig.queryConfiguration("debug.filedump", "false").equals("true")) dump2File("sign_in-a2.html", confirmed);
        JHTML htdoc = new JHTML(confirmed);
        JHTML.Form curForm = htdoc.getFormWithInput("pass");
        if (curForm != null) {
          return false;
        }
      }
    }
    return true;
  }

  public void loadAuthorization(XMLElement auth) {
    String username = auth.getProperty("USER", null);
    if (username != null) {
      JConfig.setConfiguration(userCfgString, username);

      //  If password1 is available, use it as a Base64 encoded
      //  password.  If it's not available, fall back to
      //  compatibility, loading the password as unencrypted.  This
      //  can be extended, by including encryption algorithms with
      //  increasing numbers at the end of PASSWORD, and preserving
      //  backwards compatibility.
      String b64Password = auth.getProperty("PASSWORD1");
      String password = b64Password != null ? Base64.decodeToString(b64Password) : auth.getProperty("PASSWORD", null);

      if (password != null) {
        JConfig.setConfiguration(passCfgString, password);
      }
    }
  }

  public void storeAuthorization(XMLElement auth) {
    if (getUserId() != null) {
      auth.setProperty("user", getUserId());
      auth.setProperty("password1", Base64.encodeString(getPassword(), false));
    }
  }

  /**
   * @return - The user's ID, as they entered it.
   * @brief Get the user's ID for this auction server.
   * TODO --  Fewer things should care about this.
   */
  public String getUserId() {
    return JConfig.queryConfiguration(userCfgString, "default");
  }

  /**
   * @return - The user's password, as they entered it.
   * @brief Get the user's password for this auction server.
   */
  private String getPassword() {
    return JConfig.queryConfiguration(passCfgString, "default");
  }

  // @noinspection TailRecursion
  public CookieJar getSignInCookie(CookieJar oldCookie, String username, String password) {
    boolean isAdult = JConfig.queryConfiguration(getName() + ".adult", "false").equals("true");
    CookieJar cj = (oldCookie==null)?new CookieJar():oldCookie;
    String startURL = Externalized.getString("ebayServer.signInPage");
    if(isAdult) {
      startURL = Externalized.getString("ebayServer.adultPageLogin");
    }
    URLConnection uc_signin = cj.getAllCookiesFromPage(startURL, null, false);
    try {
      StringBuffer signin = Http.receivePage(uc_signin);
      if(JConfig.queryConfiguration("debug.filedump", "false").equals("true")) dump2File("sign_in-1.html", signin);
      JHTML htdoc = new JHTML(signin);

      JHTML.Form curForm = htdoc.getFormWithInput("pass");
      if(curForm != null) {
        //  If it has a password field, this is the input form.
        curForm.setText("userid", username);
        curForm.setText("pass", password);
        uc_signin = cj.getAllCookiesFromPage(curForm.getCGI(), null, false);
        if (isAdult) {
          if (getAdultRedirector(uc_signin, cj)) {
            MQFactory.getConcrete("Swing").enqueue("VALID LOGIN");
          } else {
            //  Disable adult mode and try again.
            ErrorManagement.logMessage("Disabling 'adult' mode and retrying.");
            JConfig.setConfiguration(getName() + ".adult", "false");
            return getSignInCookie(cj, username, password);
          }
        } else {
          StringBuffer confirm = Http.receivePage(uc_signin);
          if (JConfig.queryConfiguration("debug.filedump", "false").equals("true")) dump2File("sign_in-2.html", confirm);
          JHTML doc = new JHTML(confirm);
          if(checkSecurityConfirmation(doc)) {
            cj = null;
          } else if(doc.grep("Your sign in information is not valid.") != null) {
            mBadPassword = getPassword();
            mBadUsername = getUserId();
            ErrorManagement.logMessage("Username/password not valid.");
            MQFactory.getConcrete("Swing").enqueue("INVALID LOGIN Username/password not recognized by eBay.");
          } else {
            MQFactory.getConcrete("Swing").enqueue("VALID LOGIN");
          }
        }
      }
    } catch (IOException e) {
      //  We don't know how far we might have gotten...  The cookies
      //  may be valid, even!  We can't assume it, though.
      MQFactory.getConcrete("Swing").enqueue("INVALID LOGIN " + e.getMessage());
      ErrorManagement.handleException("Couldn't sign in!", e);
    }

    return cj;
  }

  private void notifySecurityIssue() {
    MQFactory.getConcrete("Swing").enqueue("NOTIFY " + "eBay's security monitoring has been triggered, and temporarily requires\n" +
        "human intervention to log in.  JBidwatcher will not be able to log in\n" +
        "(including bids, snipes, and retrieving My eBay items) until this is fixed.");
  }

  private void notifyBadSignin() {
    MQFactory.getConcrete("Swing").enqueue("NOTIFY " + "Your sign in information appears to be incorrect, according to\n" +
                                                       "eBay.  Please fix it in the eBay tab in the Configuration Manager.");
  }

  private boolean checkSecurityConfirmation(JHTML doc) throws IOException {
    if(doc.grep("Security Confirmation") != null) {
      ErrorManagement.logMessage("eBay's security monitoring has been triggered, and temporarily requires human intervention to log in.");
      MQFactory.getConcrete("Swing").enqueue("INVALID LOGIN eBay's security monitoring has been triggered, and temporarily requires human intervention to log in.");
      notifySecurityIssue();
      mBadPassword = getPassword();
      mBadUsername = getUserId();
      throw new IOException("Failed eBay security check (captcha).");
    }

    if(doc.grep("Your sign in information is not valid.") != null) {
      ErrorManagement.logMessage("Your sign in information is not valid.");
      MQFactory.getConcrete("Swing").enqueue("INVALID LOGIN Your sign in information is not correct.  Fix it in the eBay tab in the Configuration Manager.");
      notifyBadSignin();
      mBadPassword = getPassword();
      mBadUsername = getUserId();
      return true;
    }

    return false;
  }

  /**
   * @brief eBay has a cookie that is needed to do virtually anything
   * interesting on their site; this function retrieves that cookie,
   * and holds on to it.
   *
   * If you are registered as an adult, it also logs in through that
   * page, getting all necessary cookies.
   *
   * @return - A collection of cookies that need to be passed around
   * (and updated) each time pages are requested, etc., on eBay.
   */
  public synchronized CookieJar getSignInCookie(CookieJar old_cj) {
    if(getPassword().equals(mBadPassword) && getUserId().equals(mBadUsername)) {
      return old_cj;
    }

    String msg = "Getting the sign in cookie.";

    if(JConfig.queryConfiguration("debug.verbose", "false").equals("true")) ErrorManagement.logDebug(msg);
    MQFactory.getConcrete("Swing").enqueue(msg);

    CookieJar cj = getSignInCookie(old_cj, getUserId(), getPassword());

    String done_msg = "Done getting the sign in cookie.";
    MQFactory.getConcrete("Swing").enqueue(done_msg);
    if(JConfig.queryConfiguration("debug.verbose", "false").equals("true")) ErrorManagement.logDebug(done_msg);

    return cj;
  }

  /**
   * @brief Add all the items on the page to the list of monitored auctions.
   *
   * @param htmlDocument - The document to get all the items from.
   * @param category - What 'group' to label items retrieved this way as.
   * @param interactive - Is this operation being done interactively, by the user?
   *
   * @return - A count of items added.
   */
  private static int addAllItemsOnPage(JHTML htmlDocument, String category, boolean interactive) {
    List<String> allItemsOnPage = htmlDocument.getAllURLsOnPage(true);
    int item_count = 0;

    if(allItemsOnPage == null) {
      ErrorManagement.logDebug("No items on page!");
    } else {
      for(ListIterator<String> it=allItemsOnPage.listIterator(); it.hasNext(); ) {
        String url = it.next();

        url = url.replaceAll("\n|\r", "");
        boolean gotNext;
        String nextURL;

        if(it.hasNext()) {
          nextURL = it.next();

          nextURL = nextURL.replaceAll("\n|\r", "");
          gotNext = true;
        } else {
          nextURL = "";
          gotNext = false;
        }

        //  If the URL is listed multiple times in order, then skip
        //  until the last instance of it.
        if (nextURL.equals(url)) {
          //  If they're equal, it pretty much has to have gotten the next entry, but for safety's sake, check.
          if (gotNext) it.previous();
        } else {
          //  Back out the move if we made one.
          if (gotNext) it.previous();
          url = url.trim();

          /**
           * Does this look like an auction server item URL?
           */
          AuctionServerInterface aucServ = AuctionServerManager.getInstance().getServerForUrlString(url);
          String hasId = aucServ.extractIdentifierFromURLString(url);

          if (hasId != null) {
            MQFactory.getConcrete("drop").enqueue(new DropQObject(url.trim(), category, interactive));
            item_count++;
          }
        }
      }
    }
    return item_count;
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
    String doSync = JConfig.queryConfiguration(getName() + ".synchronize", "false");

    if(!doSync.equals("ignore")) {
      if(doSync.equalsIgnoreCase("true")) {
        setMyEbay(searchManager.addSearch("My Items", "My eBay", "", "ebay", 1, 1)); //$NON-NLS-4$
      } else {
        setMyEbay(searchManager.addSearch("My Items", "My eBay", "", "ebay", -1, 1)); //$NON-NLS-4$
      }
      JConfig.setConfiguration(getName() + ".synchronize", "ignore");
    }
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
    CookieJar cj = getNecessaryCookie(false);
    String userCookie = null;
    if (cj != null) userCookie = cj.toString();
    JHTML htmlDocument = new JHTML(Externalized.getString("ebayServer.protocol") + Externalized.getString("ebayServer.bidderNamesHost") + Externalized.getString("ebayServer.file") + Externalized.getString("ebayServer.viewBidsCGI") + ae.getIdentifier(), userCookie, this);

//    if(htmlDocument == null) {
//      ErrorManagement.logMessage("Error getting bidder names for auction " + ae.getIdentifier());
//      return null;
//    }
//
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

  /**
   * @brief Do a Seller Search to see all the items a given user is selling.
   *
   * This obsoletes our previous use of 'My eBay' to get the selling
   * information.
   *
   * @param userId - The user to load their selling items for.
   * @param label - What 'group' to label items retrieved this way as.
   */
  private void getSellingItems(String userId, String label) {
    CookieJar cj = getNecessaryCookie(false);
    String userCookie = null;
    if (cj != null) userCookie = cj.toString();

    if(userId == null || userId.equals("default")) {
      ErrorManagement.logMessage("Cannot load selling pages without at least a userid.");
      return;
    }

    String myEBayURL = Externalized.getString("ebayServer.protocol") + Externalized.getString("ebayServer.sellingListHost") + Externalized.getString("ebayServer.V3file") +
                       Externalized.getString("ebayServer.listedCGI") +
                       Externalized.getString("ebayServer.sortOrderCGI") +
                       Externalized.getString("ebayServer.userIdCGI") + userId;

    JHTML htmlDocument = new JHTML(myEBayURL, userCookie, this);

    if(htmlDocument.isLoaded()) {
      int count = addAllItemsOnPage(htmlDocument, label, userId.equals(getUserId()));
      MQFactory.getConcrete("Swing").enqueue("Loaded " + count + " items for seller " + userId);
    } else {
      ErrorManagement.logMessage("getSellingItems failed!");
    }
  }

  /**
   * @brief Load all items we can find in the 'My eBay' bidding page.
   *
   * Unfortunately, that can include items in the 'You might like...' area.
   *
   * @param label - What 'group' to label items retrieved this way as.
   */
  private void getMyEbayItems(String label) {
    String localUser = getUserId();
    CookieJar cj = getNecessaryCookie(false);
    String userCookie = null;
    if (cj != null) userCookie = cj.toString();

    if(localUser == null || localUser.equals("default")) {
      ErrorManagement.logMessage("Cannot load My eBay pages without a userid and password.");
      return;
    }

    int page = 0;
    boolean done_watching = false;
    while(!done_watching) {
      //  First load items that the user is watching (!)
      //    String watchingURL = Externalized.getString("ebayServer.watchingURL");
      String watchingURL = Externalized.getString("ebayServer.bigWatchingURL1") + getUserId() +
              Externalized.getString("ebayServer.bigWatchingURL2") + page +
              Externalized.getString("ebayServer.bigWatchingURL3") + (page+1);
      ErrorManagement.logDebug("Loading page " + page + " of My eBay for user " + getUserId());
      ErrorManagement.logDebug("URL: " + watchingURL);

      JHTML htmlDocument = new JHTML(watchingURL, userCookie, this);
      addAllItemsOnPage(htmlDocument, label, true);
      String ofX = htmlDocument.getNextContentAfterRegex("Page " + (page+1));
      if(ofX == null || !ofX.startsWith("of ")) done_watching = true;
      else try { done_watching = (page+1)==Integer.parseInt(ofX.substring(3)); } catch(NumberFormatException ignored) { done_watching = true; }
      if(!done_watching) page++;
    }

    boolean done_bidding = false;
    while(!done_bidding) {
      //  Now load items the user is bidding on...
      String biddingURL = Externalized.getString("ebayServer.biddingURL");
      ErrorManagement.logDebug("Loading page: " + biddingURL);

      //noinspection ReuseOfLocalVariable
      JHTML htmlDocument = new JHTML(biddingURL, userCookie, this);
      addAllItemsOnPage(htmlDocument, label, true);
      done_bidding = true;
    }
  }

  /**
   * @brief Delete characters from a range within a stringbuffer, safely.
   *
   * @param sb - The stringbuffer to delete from.
   * @param desc_start - The start point to delete from.
   * @param desc_end - The endpoint to delete to.
   *
   * @return - true if a deletion occurred, false if the parameters
   * were invalid in any way.
   */
  private static boolean deleteRange(StringBuffer sb, int desc_start, int desc_end) {
    if(desc_start < desc_end &&
       desc_start != -1 &&
       desc_end != -1) {
      sb.delete(desc_start, desc_end);
      return true;
    }
    return false;
  }

  /**
   * @brief Delete a block of text, indicated by a start and end
   * string pair, with alternates.
   *
   * @param sb - The StringBuffer to delete from, In/Out.
   * @param startStr - The start string to delete from.
   * @param altStartStr - An alternate start string, in case the startStr isn't found.
   * @param endStr - The end string to delete to.
   * @param altEndStr - An alternate end string in case the endStr is found before the start string.
   *
   * @return - true if a delete occurred, false otherwise.
   */
  private static boolean deleteFirstToLast(StringBuffer sb, String startStr, String altStartStr, String endStr, String altEndStr) {
    String fullBuff = sb.toString();
    int desc_start = fullBuff.indexOf(startStr);

    if(desc_start == -1) {
      desc_start = fullBuff.indexOf(altStartStr);
    }

    int desc_end = fullBuff.lastIndexOf(endStr);

    if(desc_start > desc_end) desc_end = fullBuff.lastIndexOf(altEndStr);

    return deleteRange(sb, desc_start, desc_end);
  }

  /**
   * @brief Simple utility to delete from a stringbuffer starting
   * from a string, until the next following string.
   *
   * @param sb - The buffer to delete from.
   * @param startStr - The string to delete starting at.
   * @param endStr - The string to delete up until.
   *
   * @return - true if the delete happened, false otherwise.
   */
  private static boolean deleteRegexPair(StringBuffer sb, String startStr, String endStr) {
    Matcher start = Pattern.compile(startStr, Pattern.CASE_INSENSITIVE).matcher(sb);
    Matcher end = Pattern.compile(endStr, Pattern.CASE_INSENSITIVE).matcher(sb);

    if(start.find() &&
       end.find(start.start()+1)) {
      int desc_start = start.start();
      int desc_end = end.end();

      return deleteRange(sb, desc_start, desc_end);
    }
    return false;
  }

  /**
   * @brief Remove all scripts (javascript or other) in the string
   * buffer passed in.
   *
   * @param sb - The StringBuffer to eliminate script entries from.
   */
  private static void killScripts(StringBuffer sb) {
    boolean didDelete;
    do {
      didDelete = deleteRegexPair(sb, Externalized.getString("ebayServer.stripScript"), Externalized.getString("ebayServer.stripScriptEnd"));
    } while(didDelete);
  }

  /**
   * @brief Delete all scripts, and comments on an HTML page.
   *
   * @param sb - The StringBuffer to clean of scripts and comments.
   */
  private static void internalCleanup(StringBuffer sb) {
    killScripts(sb);

    //  Eliminate all comment sections.
    boolean didDelete;
    do {
      didDelete = deleteRegexPair(sb, Externalized.getString("ebayServer.stripComment"), Externalized.getString("ebayServer.stripCommentEnd"));
    } while(didDelete);
  }

  /**
   * @brief Chain to the internal cleanup code.
   *
   * @param sb - The StringBuffer to clean of scripts and comments.
   */
  public void cleanup(StringBuffer sb) {
    internalCleanup(sb);
  }

  protected class ebayAuction extends SpecificAuction {
    String _bidCountScript = null;
    String _startComment = null;
    private static final int TITLE_LENGTH = 60;
    private static final int HIGH_BIT_SET = 0x80;
    //private final Pattern p = Pattern.compile("src=\"(http://[a-z0-9]+?\\.ebayimg\\.com.*?(jpg|gif|png))\"", Pattern.DOTALL | Pattern.MULTILINE | Pattern.CASE_INSENSITIVE);
    private final Pattern p = Pattern.compile(Externalized.getString("ebayServer.thumbSearch"), Pattern.DOTALL | Pattern.MULTILINE | Pattern.CASE_INSENSITIVE);
    private final Pattern p2 = Pattern.compile(Externalized.getString("ebayServer.thumbSearch2"), Pattern.DOTALL | Pattern.MULTILINE | Pattern.CASE_INSENSITIVE);
    private String potentialThumbnail = null;

    private void checkThumb(StringBuffer sb) {
      Matcher imgMatch = p.matcher(sb);
      if(imgMatch.find()) {
        potentialThumbnail = imgMatch.group(1);
      } else {
        imgMatch = p2.matcher(sb);
        if(imgMatch.find()) {
          potentialThumbnail = imgMatch.group(1);
        }
      }
    }

    /**
     * @brief Delete the 'description' portion of a page, all scripts, and comments.
     *
     * @param sb - The StringBuffer to clean of description and scripts.
     */
    public void cleanup(StringBuffer sb) {
      checkThumb(sb);
      //  We ignore the result of this, because it's just useful if it
      //  works, it's not critical.
      deleteFirstToLast(sb, Externalized.getString("ebayServer.description"), Externalized.getString("ebayServer.descriptionMotors"), Externalized.getString("ebayServer.descriptionEnd"), Externalized.getString("ebayServer.descriptionClosedEnd"));

      deleteFirstToLast(sb, Externalized.getString("ebayServer.descStart"), Externalized.getString("ebayServer.descriptionMotors"), Externalized.getString("ebayServer.descEnd"), Externalized.getString("ebayServer.descriptionClosedEnd"));

      String skimOver = sb.toString();

      Matcher startCommentSearch = Pattern.compile(Externalized.getString("ebayServer.startedRegex")).matcher(skimOver);
      if(startCommentSearch.find()) _startComment = startCommentSearch.group(1);
      else _startComment = "";

      Matcher bidCountSearch = Pattern.compile(Externalized.getString("ebayServer.bidCountRegex")).matcher(skimOver);
      if(bidCountSearch.find()) _bidCountScript = bidCountSearch.group(1);
      else _bidCountScript = "";

      //  Use eBayServer's cleanup method to finish up with.
      internalCleanup(sb);
    }

    boolean checkTitle(String auctionTitle) {
      if(auctionTitle.startsWith(Externalized.getString("ebayServer.liveAuctionsTitle"))) {
        ErrorManagement.logMessage("JBidWatcher cannot handle live auctions!");
        return false;
      }

      if(auctionTitle.startsWith(Externalized.getString("ebayServer.greatCollectionsTitle"))) {
        ErrorManagement.logMessage("JBidWatcher cannot handle Great Collections items yet.");
        return false;
      }

      String[] eBayTitles = new String[]{
          Externalized.getString("ebayServer.titleEbay"),
          Externalized.getString("ebayServer.titleEbay2"),
          Externalized.getString("ebayServer.titleMotors"),
          Externalized.getString("ebayServer.titleMotors2"),
          Externalized.getString("ebayServer.titleDisney"),
          Externalized.getString("ebayServer.titleCollections")};

      for (String eBayTitle : eBayTitles) {
        if (auctionTitle.startsWith(eBayTitle)) return true;
      }

      return false;
    }

    private com.jbidwatcher.util.Currency getUSCurrency(com.jbidwatcher.util.Currency val, JHTML _htmlDoc) {
      com.jbidwatcher.util.Currency newCur = null;

      if(val != null && !val.isNull()) {
        if (val.getCurrencyType() == com.jbidwatcher.util.Currency.US_DOLLAR) {
          newCur = val;
        } else {
          String approxAmount = _htmlDoc.getNextContent();
          //  If the next text doesn't contain a USD amount, it's seperated somehow.
          //  Skim forwards until we either find something, or give up.  (6 steps for now.)
          int i = 0;
          while (i++ < 6 && approxAmount.indexOf(Externalized.getString("ebayServer.USD")) == -1) {
            approxAmount = _htmlDoc.getNextContent();
          }
          //  If we still have no values visible, punt and treat it as zero.
          if (approxAmount.indexOf(Externalized.getString("ebayServer.USD")) == -1) {
            newCur = com.jbidwatcher.util.Currency.getCurrency("$0.00");
          } else {
            approxAmount = approxAmount.substring(approxAmount.indexOf(Externalized.getString("ebayServer.USD")));
            newCur = com.jbidwatcher.util.Currency.getCurrency(approxAmount);
          }
        }
      }

      return newCur;
    }

    private Pattern digits = Pattern.compile("([0-9]+)");

    int getDigits(String digitsStarting) {
      Matcher m = digits.matcher(digitsStarting);
      m.find();
      String rawCount = m.group();
      if(rawCount != null) {
        return Integer.parseInt(rawCount);
      }
      return -1;
    }

    /**
     * @brief Check the title for unavailable or 'removed item' messages.
     *
     * @param in_title - The title from the web page, to check.
     */
    private void handle_bad_title(String in_title) {
      if(in_title.indexOf(Externalized.getString("ebayServer.unavailable")) != -1) {
        MQFactory.getConcrete("Swing").enqueue("LINK DOWN eBay (or the link to eBay) appears to be down.");
        MQFactory.getConcrete("Swing").enqueue("eBay (or the link to eBay) appears to be down for the moment.");
      } else if(in_title.indexOf(Externalized.getString("ebayServer.invalidItem")) != -1) {
        ErrorManagement.logDebug("Found bad/deleted item.");
      } else {
        ErrorManagement.logDebug("Failed to load auction title from header: \"" + in_title + '\"');
      }
    }

    /**
     * @brief Build the title from the data on the web page, pulling HTML tokens out as it goes.
     *
     * @param doc - The document to pull the title from.
     *
     * @return - A string consisting of just the title part of the page, with tags stripped.
     */
    private String buildTitle(JHTML doc) {
      //  This is an HTML title...  Suck.
      doc.reset();
      doc.getNextTag();
      StringBuffer outTitle = new StringBuffer(TITLE_LENGTH);
      //  Iterate over the tokens, adding all content to the
      //  title tag until the end of the title.
      htmlToken jh;
      do {
        jh = doc.nextToken();
        if(jh.getTokenType() == htmlToken.HTML_CONTENT) {
          outTitle.append(jh.getToken());
        }
      } while(!(jh.getTokenType() == htmlToken.HTML_ENDTAG &&
                jh.getToken().equalsIgnoreCase("/title")));

      return outTitle.toString();
    }

    private Pattern amountPat = Pattern.compile("(([0-9]+\\.[0-9]+|(?i)free))");

    private void load_shipping_insurance(com.jbidwatcher.util.Currency sampleAmount) {
      String shipString = _htmlDocument.getNextContentAfterRegex(Externalized.getString("ebayServer.shipping"));
      //  Sometimes the next content might not be the shipping amount, it might be the next-next.
      Matcher amount = null;
      boolean amountFound = false;
      if(shipString != null) {
        amount = amountPat.matcher(shipString);
        amountFound = amount.find();
        if (!amountFound) {
          shipString = _htmlDocument.getNextContent();
          amount = amountPat.matcher(shipString);
          if (shipString != null) amountFound = amount.find();
        }
      }
      //  This will result in either 'null' or the amount.
      if(shipString != null && amountFound) shipString = amount.group();

      //  Step back two contents, to check if it's 'Payment
      //  Instructions', in which case, the shipping and handling
      //  came from their instructions box, not the
      //  standard-formatted data.
      String shipStringCheck = _htmlDocument.getPrevContent(2);

      String insureString = _htmlDocument.getNextContentAfterRegex(Externalized.getString("ebayServer.shippingInsurance"));
      String insuranceOptionalCheck = _htmlDocument.getNextContent();

      //  Default to thinking it's optional if the word 'required' isn't found.
      //  You don't want to make people think it's required if it's not.
      _insurance_optional = insuranceOptionalCheck == null || (insuranceOptionalCheck.toLowerCase().indexOf(Externalized.getString("ebayServer.requiredInsurance")) == -1);

      if(insureString != null) {
        if(insureString.equals("-") || insureString.equals("--")) {
          insureString = null;
        } else {
          insureString = insureString.trim();
        }
      }

      if(shipStringCheck != null && !shipStringCheck.equals(Externalized.getString("ebayServer.paymentInstructions"))) {
        if(shipString != null) {
          if(shipString.equals("-")) {
            shipString = null;
          } else {
            shipString = shipString.trim();
          }
        }
      } else {
        shipString = null;
      }

      if(shipString != null) {
        if(shipString.equalsIgnoreCase("free")) {
          _shipping = com.jbidwatcher.util.Currency.getCurrency(sampleAmount.fullCurrencyName(), "0.0");
        } else {
          try {
            _shipping = com.jbidwatcher.util.Currency.getCurrency(sampleAmount.fullCurrencyName(), shipString);
          } catch(NumberFormatException nfe) {
            _shipping = com.jbidwatcher.util.Currency.NoValue();
          }
        }
      } else {
        _shipping = com.jbidwatcher.util.Currency.NoValue();
      }
      try {
        _insurance = com.jbidwatcher.util.Currency.getCurrency(insureString);
      } catch(NumberFormatException nfe) {
        _insurance = com.jbidwatcher.util.Currency.NoValue();
      }
    }

    private void load_buy_now() {
      _buy_now = com.jbidwatcher.util.Currency.NoValue();
      _buy_now_us = com.jbidwatcher.util.Currency.NoValue();
      String buyNowString = _htmlDocument.getNextContentAfterContent(Externalized.getString("ebayServer.buyItNow"));
      if(buyNowString != null) {
        buyNowString = buyNowString.trim();
        while(buyNowString.length() == 0 || buyNowString.equals(Externalized.getString("ebayServer.buyNowFor"))) {
          buyNowString = _htmlDocument.getNextContent().trim();
        }
      }

      if(buyNowString != null && !buyNowString.equals(Externalized.getString("ebayServer.ended"))) {
        _buy_now = com.jbidwatcher.util.Currency.getCurrency(buyNowString);
        _buy_now_us = getUSCurrency(_buy_now, _htmlDocument);
      }
      if(buyNowString == null || buyNowString.equals(Externalized.getString("ebayServer.ended")) || _buy_now == null || _buy_now.isNull()) {
        String altBuyNowString1 = _htmlDocument.getNextContentAfterRegexIgnoring(Externalized.getString("ebayServer.price"), "[Ii]tem.[Nn]umber");
        if(altBuyNowString1 != null) {
          altBuyNowString1 = altBuyNowString1.trim();
        }
        if(altBuyNowString1 != null && altBuyNowString1.length() != 0) {
          _buy_now = com.jbidwatcher.util.Currency.getCurrency(altBuyNowString1);
          _buy_now_us = getUSCurrency(_buy_now, _htmlDocument);
        }
      }
    }

    private String getEndDate(String inTitle) {
      String result = null;

      Matcher dateMatch = datePat.matcher(inTitle);
      if(dateMatch.find()) result = dateMatch.group(2);

      return result;
    }

    private String decodeLatin(String latinString) {
      //  Why?  Because it seems to Just Work on Windows.  Argh.
      if(!Platform.isMac()) return latinString;
      try {
        return new String(latinString.getBytes(), "ISO-8859-1");
      } catch (UnsupportedEncodingException ignore) {
        return latinString;
      }
    }

    /**
     * A utility function to check the provided preferred object against an arbitrary 'bad' value,
     * and return the preferred object if it's not bad, and an alternative object if it the preferred
     * object is bad.
     *
     * @param preferred - The preferred object (to be compared against the 'bad' value)
     * @param alternate - The alternative object, if the first object is bad.
     * @param bad - The bad object to validate the preferred object against.
     *
     * @return - preferred if it's not bad, alternate if the preferred object is bad.
     * @noinspection ObjectEquality
     **/
    private Object ensureSafeValue(Object preferred, Object alternate, Currency bad) {
      return (preferred == bad)?alternate:preferred;
    }

    private String getResult(JHTML doc, String regex, int match) {
      String rval = doc.grep(regex);
      if(rval != null) {
        if(match == 0) return rval;
        Pattern searcher = Pattern.compile(regex);
        Matcher matcher = searcher.matcher(rval);
        if(matcher.matches()) return matcher.group(match);
      }

      return null;
    }

    private void loadSecondaryInformation(JHTML doc) {
      try {
        String score = getResult(doc, Externalized.getString("ebayServer.feedbackRegex"), 1);
        if(score != null && StringTools.isNumberOnly(score)) {
          _feedback = Integer.parseInt(score);
        }

        String percentage = getResult(doc, Externalized.getString("ebayServer.feedbackPercentageRegex"), 1);
        if(percentage != null) _postivePercentage = percentage;

        String location = doc.getNextContentAfterRegex(Externalized.getString("ebayServer.itemLocationRegex"));
        if(location != null) {
          _itemLocation = location;
        }

        String pbp = getResult(doc, Externalized.getString("ebayServer.paypalMatcherRegex"), 0);
        if(pbp != null) _paypal = true;
      } catch(Throwable t) {
        //  I don't actually CARE about any of this data, or any errors that occur on loading it, so don't mess things up on errors.
        ErrorManagement.logDebug(t.getMessage());
      }
    }

    public boolean parseAuction(AuctionEntry ae) {
      //  Verify the title (in case it's an invalid page, the site is
      //  down for maintenance, etc).
      String prelimTitle = _htmlDocument.getFirstContent();
      if( prelimTitle == null) {
        prelimTitle = Externalized.getString("ebayServer.unavailable");
      }
      if(prelimTitle.equals(Externalized.getString("ebayServer.adultPageTitle")) || prelimTitle.indexOf("Terms of Use: ") != -1) {
        boolean isAdult = JConfig.queryConfiguration(getName() + ".adult", "false").equals("true");
        if(isAdult) {
          getNecessaryCookie(true);
        } else {
          ErrorManagement.logDebug("Failed to load adult item, user not registered as Adult.  Check eBay configuration.");
        }
        finish();
        return false;
      }

      //  Is this a valid eBay item page?
      if(!checkTitle(prelimTitle)) {
        handle_bad_title(prelimTitle);
        finish();
        return false;
      }

      //  If we got a valid title, mark the link as up, because it worked...
      MQFactory.getConcrete("Swing").enqueue("LINK UP");

      boolean ebayMotors = false;
      if(prelimTitle.indexOf(Externalized.getString("ebayServer.ebayMotorsTitle")) != -1) ebayMotors = true;
      //  This is mostly a hope, not a guarantee, as eBay might start
      //  cross-advertising eBay Motors in their normal pages, or
      //  something.
      if(doesLabelExist(Externalized.getString("ebayServer.ebayMotorsTitle"))) ebayMotors = true;

      _end = null;
      _title = null;

      //  This sucks.  They changed to: eBay: {title} (item # end time {endtime})
      if(prelimTitle.startsWith(Externalized.getString("ebayServer.titleEbay2")) ||
         prelimTitle.startsWith(Externalized.getString("ebayServer.titleMotors2"))) {
        //  Handle the new titles.
        Pattern newTitlePat = Pattern.compile(Externalized.getString("ebayServer.titleMatch"));
        Matcher newTitleMatch = newTitlePat.matcher(prelimTitle);
//        Regex newTitleR = new Regex(Externalized.getString("ebayServer.titleMatch"));
        if(newTitleMatch.find()) {
          _title = decodeLatin(newTitleMatch.group(2));
          String endDate = newTitleMatch.group(4);
          _end = figureDate(endDate, Externalized.getString("ebayServer.dateFormat"));
        }
      }

      if(_title == null) {
        boolean htmlTitle = false;
        //  The first element after the title is always the description.  Unfortunately, it's in HTML-encoded format,
        //  so there are &lt;'s, and such.  While I could translate that, that's something I can wait on.  --  HACKHACK
        //      _title = (String)_contentFields.get(1);
        //  For now, just load from the title, everything after ') - '.
        int titleIndex = prelimTitle.indexOf(") - ");
        if(titleIndex == -1) {
          titleIndex = prelimTitle.indexOf(") -");
          //  This is an HTML title...  Suck.
          htmlTitle = true;
        }

        //  Always convert, at this point, from iso-8859-1 (iso latin-1) to UTF-8.
        if(htmlTitle) {
          _title = decodeLatin(buildTitle(_htmlDocument));
        } else {
          _title = decodeLatin(prelimTitle.substring(titleIndex+4).trim());
        }
      }

      if(_title.length() == 0) _title = "(bad title)";
      _title = JHTML.deAmpersand(_title);

      // eBay Motors titles are really a combination of the make/model,
      // and the user's own text.  Under BIBO, the user's own text is
      // below the 'description' fold.  For now, we don't get the user
      // text.
      if(ebayMotors) {
        extractMotorsTitle();
      }

      //  Get the integer values (Quantity, Bidcount)
      _quantity = getNumberFromLabel(_htmlDocument, Externalized.getString("ebayServer.quantity"), Externalized.getString("ebayServer.postTitleIgnore"));

      _fixed_price = false;
      _numBids = getBidCount(_htmlDocument, _quantity);

      try {
        load_buy_now();
      } catch(Exception e) {
        ErrorManagement.handleException("Buy It Now Loading error", e);
      }

      if(_end == null) {
        String endDate = getEndDate(prelimTitle);
        _end = figureDate(endDate, Externalized.getString("ebayServer.dateFormat"));
      }

      _start = figureDate(_htmlDocument.getNextContentAfterRegexIgnoring(Externalized.getString("ebayServer.startTime"), Externalized.getString("ebayServer.postTitleIgnore")), Externalized.getString("ebayServer.dateFormat"));
      if(_start == null) {
        _start = figureDate(_startComment, Externalized.getString("ebayServer.dateFormat"));
      }
      _start = (Date)ensureSafeValue(_start, ae!=null?ae.getStartDate():null, null);

      if(_start == null) {
        _start = new Date(-1);
      }

      if (_fixed_price) {
        if(_buy_now != null && !_buy_now.isNull()) {
          _minBid = _buy_now;
          _curBid = _buy_now;
          _us_cur = _buy_now_us;
        } else {
          //  The set of tags that indicate the current/starting/lowest/winning
          //  bid are 'Starts at', 'Current bid', 'Starting bid', 'Lowest bid',
          //  'Winning bid' so far.  'Starts at' is mainly for live auctions!
          String cvtCur = _htmlDocument.getNextContentAfterRegex(Externalized.getString("ebayServer.currentBid"));
          _curBid = com.jbidwatcher.util.Currency.getCurrency(cvtCur);
          _us_cur = getUSCurrency(_curBid, _htmlDocument);

          _curBid = (com.jbidwatcher.util.Currency)ensureSafeValue(_curBid, ae!=null?ae.getCurBid()  : com.jbidwatcher.util.Currency.NoValue(), com.jbidwatcher.util.Currency.NoValue());
          _us_cur = (com.jbidwatcher.util.Currency)ensureSafeValue(_us_cur, ae!=null?ae.getUSCurBid(): com.jbidwatcher.util.Currency.NoValue(), com.jbidwatcher.util.Currency.NoValue());
        }
      } else {
        //  The set of tags that indicate the current/starting/lowest/winning
        //  bid are 'Current bid', 'Starting bid', 'Lowest bid',
        //  'Winning bid' so far.
        String cvtCur = _htmlDocument.getNextContentAfterRegex(Externalized.getString("ebayServer.currentBid"));
        _curBid = com.jbidwatcher.util.Currency.getCurrency(cvtCur);
        _us_cur = getUSCurrency(_curBid, _htmlDocument);

        if(_curBid == null || _curBid.isNull()) {
          if(_quantity > 1) {
            _curBid = com.jbidwatcher.util.Currency.getCurrency(_htmlDocument.getNextContentAfterContent(Externalized.getString("ebayServer.lowestBid")));
            _us_cur = getUSCurrency(_curBid, _htmlDocument);
          }
        }

        _minBid = com.jbidwatcher.util.Currency.getCurrency(_htmlDocument.getNextContentAfterContent(Externalized.getString("ebayServer.firstBid")));
        //  Handle odd case...
        if(_end == null) {
          _end = figureDate(_htmlDocument.getNextContentAfterContent(Externalized.getString("ebayServer.endsPrequel")), Externalized.getString("ebayServer.dateFormat"));
        }
        com.jbidwatcher.util.Currency maxBid = com.jbidwatcher.util.Currency.getCurrency(_htmlDocument.getNextContentAfterContent(Externalized.getString("ebayServer.yourMaxBid")));

        _end    =     (Date)ensureSafeValue(_end,    ae!=null?ae.getEndDate() :null, null);
        _minBid = (com.jbidwatcher.util.Currency)ensureSafeValue(_minBid, ae!=null?ae.getMinBid()  : com.jbidwatcher.util.Currency.NoValue(), com.jbidwatcher.util.Currency.NoValue());
        _curBid = (com.jbidwatcher.util.Currency)ensureSafeValue(_curBid, ae!=null?ae.getCurBid()  : com.jbidwatcher.util.Currency.NoValue(), com.jbidwatcher.util.Currency.NoValue());
        _us_cur = (com.jbidwatcher.util.Currency)ensureSafeValue(_us_cur, ae!=null?ae.getUSCurBid(): com.jbidwatcher.util.Currency.NoValue(), com.jbidwatcher.util.Currency.NoValue());

        if(_numBids == 0 && (_minBid == null || _minBid.isNull())) _minBid = _curBid;

        if(_minBid == null || _minBid.isNull()) {
          String original = _htmlDocument.grep(Externalized.getString("ebayServer.originalBid"));
          if(original != null) {
            Pattern bidPat = Pattern.compile(Externalized.getString("ebayServer.originalBid"));
            Matcher bidMatch = bidPat.matcher(original);
            if(bidMatch.find()) {
              _minBid = com.jbidwatcher.util.Currency.getCurrency(bidMatch.group(1));
            }
          }
        }

        // This is dangerously intimate with the AuctionEntry class,
        // and it won't work the first time, since the first time ae
        // is null.
        if(ae != null && !maxBid.isNull()) {
          try {
            if(!ae.isBidOn() || ae.getBid().less(maxBid)) ae.setBid(maxBid);
          } catch(com.jbidwatcher.util.Currency.CurrencyTypeException cte) {
            ErrorManagement.handleException("eBay says my max bid is a different type of currency than I have stored!", cte);
          }
        }
        _outbid = _htmlDocument.grep(Externalized.getString("ebayServer.outbid")) != null;
      }

      try {
        load_shipping_insurance(_curBid);
      } catch(Exception e) {
        ErrorManagement.handleException("Shipping / Insurance Loading Failed", e);
      }

      _seller = _htmlDocument.getNextContentAfterRegex(Externalized.getString("ebayServer.seller"));
      if(_seller == null) {
        _seller = _htmlDocument.getNextContentAfterContent(Externalized.getString("ebayServer.sellerInfoPrequel"), false, true);
      }
      if(_seller == null) {
        if(_htmlDocument.grep(Externalized.getString("ebayServer.sellerAway")) != null) {
          if(ae != null) {
            ae.setLastStatus("Seller away - item unavailable.");
          }
          finish();
          return false;
        } else {
          _seller = "(unknown)";
        }
      }
      _seller = _seller.trim();

      if(_end.getTime() > System.currentTimeMillis()) {
        //  Item is not ended yet.
        if(ae != null) {
          ae.setEnded(false);
          ae.setSticky(false);
        }
      }
      /**
       * THIS is absurd.  This needs to be cleaned up.  -- mrs: 18-September-2003 21:08
       */
      if (_fixed_price) {
        _highBidder = _htmlDocument.getNextContentAfterRegex(Externalized.getString("ebayServer.buyer"));
        if (_highBidder != null) {
          _numBids = 1;
          _highBidder = _highBidder.trim();
          _highBidderEmail = _htmlDocument.getNextContentAfterContent(_highBidder, true, false);
          if (_highBidderEmail != null) {
            _highBidderEmail = _highBidderEmail.trim();
            if (_highBidderEmail.charAt(0) == '(' && _highBidderEmail.charAt(_highBidderEmail.length()-1) == ')' && _highBidderEmail.indexOf('@') != -1) {
              _highBidderEmail = _highBidderEmail.substring(1, _highBidderEmail.length() - 1);
            }
          }
          if (_highBidderEmail == null || _highBidderEmail.equals("(")) {
            _highBidderEmail = "(unknown)";
          }
        } else {
          _highBidder = "";
        }
      } else {
        if (_quantity > 1) {
          _highBidder = "(dutch)";
          _isDutch = true;
        } else {
          _highBidder = "";
          if (_numBids != 0) {
            _highBidder = _htmlDocument.getNextContentAfterRegex(Externalized.getString("ebayServer.highBidder"));
            if (_highBidder != null) {
              _highBidder = _highBidder.trim();

              _highBidderEmail = _htmlDocument.getNextContentAfterContent(_highBidder, true, false);
              if (_highBidderEmail.charAt(0) == '(' && _highBidderEmail.charAt(_highBidderEmail.length()-1) == ')' && _highBidderEmail.indexOf('@') != -1) {
                _highBidderEmail = _highBidderEmail.substring(1, _highBidderEmail.length() - 1);
              }
            } else {
              _highBidder = "(unknown)";
            }
          }
        }
      }

      if(doesLabelExist(Externalized.getString("ebayServer.reserveNotMet1")) ||
         doesLabelExist(Externalized.getString("ebayServer.reserveNotMet2"))) {
        _isReserve = true;
        _reserveMet = false;
      } else {
        if(doesLabelExist(Externalized.getString("ebayServer.reserveMet1")) ||
           doesLabelExist(Externalized.getString("ebayServer.reserveMet2"))) {
          _isReserve = true;
          _reserveMet = true;
        }
      }
      if(_highBidder.indexOf(Externalized.getString("ebayServer.keptPrivate")) != -1) {
        _isPrivate = true;
        _highBidder = "(private)";
      }
      loadSecondaryInformation(_htmlDocument);
      try {
        if(JConfig.queryConfiguration("show.images", "true").equals("true")) {
          if(!_no_thumbnail && !hasThumbnail()) {
            MQFactory.getConcrete("thumbnail").enqueue(this);
          }
        }
      } catch(Exception e) {
        ErrorManagement.handleException("Error handling thumbnail loading", e);
      }
      finish();
      return true;
    }

    private int getBidCount(JHTML doc, int quantity) {
      String rawBidCount = doc.getNextContentAfterRegex(Externalized.getString("ebayServer.bidCount"));
      int bidCount = 0;
      if(rawBidCount != null) {
        if(rawBidCount.equals(Externalized.getString("ebayServer.purchasesBidCount")) ||
           rawBidCount.endsWith(Externalized.getString("ebayServer.offerRecognition")) ||
           rawBidCount.equals(Externalized.getString("ebayServer.offerRecognition"))) {
          _fixed_price = true;
          bidCount = -1;
        } else {
          if(rawBidCount.equals(Externalized.getString("ebayServer.bidderListCount"))) {
            bidCount = Integer.parseInt(_bidCountScript);
          } else {
            bidCount = getDigits(rawBidCount);
          }
        }
      }

      //  If we can't match any digits in the bidcount, or there is no match for the eBayBidCount regex, then
      //  this is a store or FP item.  Still true under BIBO?
      if (rawBidCount == null || _numBids == -1) {
        _highBidder = Externalized.getString("ebayServer.fixedPrice");
        _fixed_price = true;

        if (doesLabelExist(Externalized.getString("ebayServer.hasBeenPurchased")) ||
            doesLabelPrefixExist(Externalized.getString("ebayServer.endedEarly"))) {
          bidCount = quantity;
          _start = _end = new Date();
        } else {
          bidCount = 0;
        }
      }

      return bidCount;
    }

    public ByteBuffer getSiteThumbnail() {
      ByteBuffer thumb = null;
      if(potentialThumbnail != null) {
        thumb = getThumbnailByURL(potentialThumbnail);
      }
      if(thumb == null) {
        thumb = getThumbnailById(getIdentifier());
      }
      return thumb;
    }

    public ByteBuffer getAlternateSiteThumbnail() {
      return getThumbnailById(getIdentifier() + "6464");
    }

    private ByteBuffer getThumbnailById(String id) {
      return getThumbnailByURL("http://thumbs.ebaystatic.com/pict/" + id + ".jpg");
    }

    private ByteBuffer getThumbnailByURL(String url) {
      ByteBuffer tmpThumb;
      try {
        tmpThumb = ThumbnailManager.downloadThumbnail(new URL(url));
      } catch(Exception ignored) {
        tmpThumb = null;
      }
      return tmpThumb;
    }

    private int getNumberFromLabel(JHTML doc, String label, String ignore) {
      String rawQuantity;
      if(ignore == null) {
        rawQuantity = doc.getNextContentAfterRegex(label);
      } else {
        rawQuantity = doc.getNextContentAfterRegexIgnoring(label, ignore);
      }
      int quant2;
      if(rawQuantity != null) {
        quant2 = getDigits(rawQuantity);
      } else {
        //  Why would I set quantity to 0?
        quant2 = 1;
      }
      return quant2;
    }

    private void extractMotorsTitle() {
      String motorsTitle = _htmlDocument.getContentBeforeContent(Externalized.getString("ebayServer.itemNum"));
      if(motorsTitle != null) {
        motorsTitle = motorsTitle.trim();
      }
      if(motorsTitle != null && motorsTitle.length() != 0 && !_title.equals(motorsTitle)) {
        if(motorsTitle.length() != 1 || motorsTitle.charAt(0) < HIGH_BIT_SET) {
          if(_title.length() == 0) {
            _title = decodeLatin(motorsTitle);
          } else {
            _title = decodeLatin(motorsTitle + " (" + _title + ')');
          }
        }
      }
    }
  }

  private void doMyEbaySynchronize(String label) {
    MQFactory.getConcrete("Swing").enqueue("Synchronizing with My eBay...");
    getMyEbayItems(label);
    MQFactory.getConcrete("Swing").enqueue("Done synchronizing with My eBay...");
  }

  private void doGetSelling(Object searcher, String label) {
    String userId = ((Searcher)searcher).getSearch();
    MQFactory.getConcrete("Swing").enqueue("Getting Selling Items for " + userId);
    getSellingItems(userId, label);
    MQFactory.getConcrete("Swing").enqueue("Done Getting Selling Items for " + userId);
  }

  protected static void doLoadAuctions() {
    OptionUI oui = new OptionUI();

    //  Use the right parent!  FIXME -- mrs: 17-February-2003 23:53
    String endResult = oui.promptString(null, "Enter the URL to load auctions from", "Loading Auctions");

    if(endResult == null) return;

    MQFactory.getConcrete("ebay").enqueue(new AuctionQObject(AuctionQObject.LOAD_URL, endResult, null));
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

  /**
   * @brief Returns the amount of time it takes to retrieve an item
   * from the auction server via their affiliate program.
   *
   * @return The amount of milliseconds it takes to get an item
   * from the auction server via their affiliate server.
   */
  public long getAffiliateRequestTime() {
    return mAffiliateRequestTime;
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
      return getUserId() + '@' + getName() + ": " + Constants.remoteClockFormat.format(mNow);
    } else {
      mNow.setTime(System.currentTimeMillis());
      return getUserId() + '@' + getName() + ": " + Constants.localClockFormat.format(mNow);
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

    sdf.set2DigitYearStart(sMidpointDate.getTime());

    if(endTime == null) return null;

    if(strip_high) {
      endTimeFmt = StringTools.stripHigh(endTime, siteDateFormat);
    }
    Date endingDate;

    try {
      endingDate = sdf.parse(endTimeFmt);
      mOfficialServerTimeZone = sdf.getCalendar().getTimeZone();
    } catch(java.text.ParseException e) {
      ErrorManagement.handleException("Error parsing date (" + endTimeFmt + "), setting to completed.", e);
      endingDate = new Date();
    }
    return(endingDate);
  }

  public String getName() {
    return siteId;
  }

  public boolean validate(String username, String password) {
    return !getUserId().equals("default") && getUserId().equals(username) && getPassword().equals(password);
  }

  public Searcher getMyEbay() {
    return mMyeBay;
  }

  public void setMyEbay(Searcher myeBay) {
    mMyeBay = myeBay;
  }
}
