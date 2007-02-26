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

import com.jbidwatcher.config.JConfig;
import com.jbidwatcher.config.JConfigTab;
import com.jbidwatcher.config.JBConfig;
import com.jbidwatcher.queue.*;
import com.jbidwatcher.util.html.JHTML;
import com.jbidwatcher.util.http.CookieJar;
import com.jbidwatcher.util.http.Http;
import com.jbidwatcher.util.*;
import com.jbidwatcher.search.Searcher;
import com.jbidwatcher.search.SearchManager;
import com.jbidwatcher.search.SearchManagerInterface;
import com.jbidwatcher.auction.*;
import com.jbidwatcher.auction.server.AuctionServer;
import com.jbidwatcher.auction.server.BadBidException;
import com.jbidwatcher.TimerHandler;
import com.jbidwatcher.Constants;
import com.jbidwatcher.xml.XMLElement;

import javax.swing.*;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.io.FileNotFoundException;
import java.net.URL;
import java.net.URLConnection;
import java.util.*;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** @noinspection OverriddenMethodCallInConstructor*/
public final class ebayServer extends AuctionServer implements MessageQueue.Listener,JConfig.ConfigListener {
  private final static String eBayDisplayName = "eBay";
  private final static String eBayServerName = "ebay";

  private static final int THREE_SECONDS = 3 * Constants.ONE_SECOND;

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
  private eBayTimeQueueManager _etqm;
  private Searcher mMyeBay = null;
  private Searcher mSellerSearch = null;
  private ebaySearches mSearcher;
  private ebayLoginManager mLogin;

  /** @noinspection FieldCanBeLocal*/
  private TimerHandler eQueue;
  private Map<String, AuctionQObject> snipeMap = new HashMap<String, AuctionQObject>();

  /**< The amount of time it takes to request an item via their affiliate program. */
  private long mAffiliateRequestTime =0;

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
    CookieJar cj = mLogin.getNecessaryCookie(false);
    String userCookie = null;
    if (cj != null) userCookie = cj.toString();

    JHTML htmlDocument = new JHTML(dutchWinners, userCookie, mCleaner);
    String matchedName = null;
    if(htmlDocument.isLoaded()) {
      matchedName = htmlDocument.getNextContentAfterContent(getUserId());
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
        mSearcher.loadAllFromURLString(ac.getData(), ac.getLabel());
        return;
      case AuctionQObject.LOAD_SEARCH:
        /**
         * Check for searches, and execute one if that's what is requested.
         */
        mSearcher.loadSearchString(ac.getData(), ac.getLabel(), false);
        return;
      case AuctionQObject.LOAD_TITLE:
        /**
         * Check for searches, and execute one if that's what is requested.
         */
        mSearcher.loadSearchString(ac.getData(), ac.getLabel(), true);
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
        AuctionQObject payload = new AuctionQObject(AuctionQObject.SNIPE, new Snipe(mLogin, snipeOn), null);

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
      if(ac.getData().equals("Get My eBay Items")) {
        //  TODO -- Mark 'My eBay' search as having run (update 'last run').
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
          mLogin.resetCookie();
          mLogin.getNecessaryCookie(true);
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

    mCleaner = new ebayCleaner();
    mLogin = new ebayLoginManager(eBayServerName, getPassword(), getUserId());
    mSearcher = new ebaySearches(mCleaner, mLogin);

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
              mLogin.resetCookie();
              mLogin.getNecessaryCookie(true);
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
      sb = mLogin.getNecessaryCookie(false).getAllCookiesAndPage(buyRequest, null, false);
      JHTML doBuy = new JHTML(sb);
      JHTML.Form buyForm = doBuy.getFormWithInput("uiid");

      if (buyForm != null) {
        buyForm.delInput("BIN_button");
        CookieJar cj = mLogin.getNecessaryCookie(false);
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
        safeGetAffiliate(mLogin.getNecessaryCookie(false), inEntry);
      }
    } catch (CookieJar.CookieException ignore) {
      //  We don't care that much about connection refused in this case.
    }
    JHTML.Form bidForm;

    try {
      bidForm = getBidForm(mLogin.getNecessaryCookie(false), inEntry, inBid, inQuantity);
    } catch(BadBidException bbe) {
      Auctions.endBlocking();
      return bbe.getResult();
    }

    if (bidForm != null) {
      int rval = placeFinalBid(mLogin.getNecessaryCookie(false), bidForm, inEntry, inBid, inQuantity);
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
                  result = mLogin.getNecessaryCookie(false).getAllCookiesAndPage(tagMatch.group(1), "http://offer.ebay.com/ws/eBayISAPI.dll", false);
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

  public synchronized CookieJar getNecessaryCookie(boolean force) {
    return mLogin.getNecessaryCookie(force);
  }

  //  TODO - The following are exposed to and used by the Snipe class only.  Is there another way?
  public CookieJar getSignInCookie(CookieJar old_cj) {
    return mLogin.getSignInCookie(old_cj);
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
    CookieJar cj = mLogin.getNecessaryCookie(false);
    String userCookie = null;
    if (cj != null) userCookie = cj.toString();
    JHTML htmlDocument = new JHTML(Externalized.getString("ebayServer.protocol") + Externalized.getString("ebayServer.bidderNamesHost") + Externalized.getString("ebayServer.file") + Externalized.getString("ebayServer.viewBidsCGI") + ae.getIdentifier(), userCookie, mCleaner);

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

  private void doMyEbaySynchronize(String label) {
    MQFactory.getConcrete("Swing").enqueue("Synchronizing with My eBay...");
    mSearcher.getMyEbayItems(getUserId(), label);
    MQFactory.getConcrete("Swing").enqueue("Done synchronizing with My eBay...");
  }

  private void doGetSelling(Object searcher, String label) {
    String userId = ((Searcher)searcher).getSearch();
    MQFactory.getConcrete("Swing").enqueue("Getting Selling Items for " + userId);
    mSearcher.getSellingItems(userId, getUserId(), label);
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

  /**
   * @return - An object containing eBay's date, or null if we fail to
   *         load or parse the 'official time' page properly.
   * @brief Go to eBay and get their official time page, parse it, and
   * mark the difference between that time and our current time
   * internally, so we know how far off this machine's time is.
   */
  protected Date getOfficialTime() {
    Auctions.startBlocking();
    long localDateBeforePage = System.currentTimeMillis();
    String timeRequest = Externalized.getString("ebayServer.protocol") + Externalized.getString("ebayServer.host") + Externalized.getString("ebayServer.file") + Externalized.getString("ebayServer.timeCmd");

//  Getting the necessary cookie here causes intense slowdown which fudges the time, badly.
    JHTML htmlDocument = new JHTML(timeRequest, null, mCleaner);
    ZoneDate result = null;

    String pageStep = htmlDocument.getNextContent();
    while (result == null && pageStep != null) {
      if (pageStep.equals(Externalized.getString("ebayServer.timePrequel1")) || pageStep.equals(Externalized.getString("ebayServer.timePrequel2"))) {
        result = StringTools.figureDate(htmlDocument.getNextContent(), Externalized.getString("ebayServer.officialTimeFormat"), false);
      }
      pageStep = htmlDocument.getNextContent();
    }

    Auctions.endBlocking();

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
    }

    return result.getDate();
  }

  /**
   * @param itemID - The eBay item ID to get a net.URL for.
   * @return - a URL to use to pull that item.
   * @brief Given a site-dependant item ID, get the URL for that item.
   */
  protected URL getURLFromItem(String itemID) {
    return (StringTools.getURLFromString(getStringURLFromItem(itemID)));
  }
}
