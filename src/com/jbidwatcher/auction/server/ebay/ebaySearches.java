package com.jbidwatcher.auction.server.ebay;

/*
 * Copyright (c) 2000-2007, CyberFOX Software, Inc. All Rights Reserved.
 *
 * Developed by mrs (Morgan Schweers)
 */

import com.jbidwatcher.util.html.JHTML;
import com.jbidwatcher.util.config.JConfig;
import com.jbidwatcher.util.Externalized;
import com.jbidwatcher.util.Constants;
import com.jbidwatcher.util.StringTools;
import com.jbidwatcher.util.Pair;
import com.jbidwatcher.util.http.CookieJar;
import com.jbidwatcher.auction.server.AuctionServerManager;
import com.jbidwatcher.auction.*;
import com.jbidwatcher.util.html.CleanupHandler;
import com.jbidwatcher.util.queue.MQFactory;
import com.jbidwatcher.util.queue.DropQObject;
import com.jbidwatcher.util.queue.SuperQueue;
import com.jbidwatcher.search.Searcher;

import java.util.*;
import java.net.URLEncoder;
import java.io.UnsupportedEncodingException;

/**
 * Created by IntelliJ IDEA.
 * User: Morgan
 * Date: Feb 25, 2007
 * Time: 6:36:13 PM
 *
 * Code to support searching eBay auctions.
 */
public class ebaySearches {
  private static final int ITEMS_PER_PAGE = 100;
  private final EntryCorral mCorral;
  private final AuctionServerManager mServerManager;
  private CleanupHandler mCleaner;
  private com.jbidwatcher.auction.LoginManager mLogin;

  private class ItemResults extends Pair<List<String>, Map<String,String>> {
    public ItemResults(List<String> s, Map<String,String> c) { super(s, c); }
  }

  public ebaySearches(EntryCorral corral, AuctionServerManager serverManager, CleanupHandler cleaner, LoginManager login) {
    mCleaner = cleaner;
    mLogin = login;
    mCorral = corral;
    mServerManager = serverManager;
  }

  private ItemResults getAllItemsOnPage(JHTML htmlDocument) {
    List<String> allURLsOnPageUnprocessed = htmlDocument.getAllURLsOnPage(true);
    List<String> allURLsOnPage = new ArrayList<String>();
    if(allURLsOnPageUnprocessed != null) for(String process : allURLsOnPageUnprocessed) { allURLsOnPage.add(process.replaceAll("\n|\r", "")); }
    Map<String,String> allItemsOnPage = new LinkedHashMap<String,String>();
    List<String> newItems = new ArrayList<String>();
    AuctionServerInterface aucServ = mServerManager.getServer();
    for(String url : allURLsOnPage) {
      // Does this look like an auction server item URL?
      String hasId = aucServ.extractIdentifierFromURLString(url);

      if(hasId != null && StringTools.isNumberOnly(hasId)) {
        if(!allItemsOnPage.containsKey(hasId)) {
          if (mCorral.takeForRead(hasId) == null) {
            allItemsOnPage.put(hasId, url);
            newItems.add(url);
          }
        }
      }
    }
    return new ItemResults(newItems, allItemsOnPage);
  }

  /**
   * @param htmlDocument - The document to get all the items from.
   * @param category     - What 'group' to label items retrieved this way as.
   * @param interactive  - Is this operation being done interactively, by the user?
   * @return - A count of items added.
   * @brief Add all the items on the page to the list of monitored auctions.
   */
  private ItemResults addAllItemsOnPage(JHTML htmlDocument, String category, boolean interactive) {
    ItemResults ir = getAllItemsOnPage(htmlDocument);
    addAll(ir.getLast().values(), category, interactive);

    return ir;
  }

  private void addAll(Collection<String> urls, String category, boolean interactive) {
    if (urls.isEmpty()) {
      JConfig.log().logDebug("No items on page!");
    } else {
      for (String url : urls) {
        MQFactory.getConcrete("drop").enqueueBean(new DropQObject(url.trim(), category, interactive));
      }
    }
  }

  /**
   * @brief Load the contents of a URL in, find all hrefs on that page
   * that point to an auction item, and add them.
   *
   * @param searcher - The Searcher object that contains the URL to load and search for items in.
   * @param label    - What 'group' to label items retrieved this way as.
   */
  void loadAllFromURLString(Object searcher, String label) {
    String urlStr = ((Searcher) searcher).getSearch();
    MQFactory.getConcrete("Swing").enqueue("Loading from URL " + urlStr);

    //noinspection MismatchedQueryAndUpdateOfCollection
    EbayAuctionURLPager pager = new EbayAuctionURLPager(urlStr, mLogin);
    int results = 0;

    ListIterator li = pager.listIterator();

    while (li.hasNext()) {
      MQFactory.getConcrete("Swing").enqueue("Loading page " + li.nextIndex() + "/" + pager.size() + " from URL " + urlStr);

      JHTML htmlDocument = (JHTML) li.next();
      if (htmlDocument != null) {
        ItemResults rval = addAllItemsOnPage(htmlDocument, label, !((Searcher) searcher).shouldSkipDeleted());
        results += rval.getFirst().size();
      }
    }

    if (results == 0) {
      MQFactory.getConcrete("Swing").enqueue("No new items found at URL: " + urlStr);
    } else {
      MQFactory.getConcrete("Swing").enqueue("Done loading from URL: " + urlStr);
    }
  }

  /**
   * @param userId - The user to load their selling items for.
   * @param curUser- The current user's eBay id, to compare against the to-be-searched id, and treated as 'interactive' if equal.
   * @param label  - What 'group' to label items retrieved this way as. @brief Do a Seller Search to see all the items a given user is selling.
   * <p/>
   * This obsoletes our previous use of 'My eBay' to get the selling
   * information.
   */
  void getSellingItems(String userId, String curUser, String label) {
    CookieJar cj = mLogin.getNecessaryCookie(false);
    String userCookie = null;
    if (cj != null) userCookie = cj.toString();

    if (userId == null || userId.equals("default")) {
      JConfig.log().logMessage("Cannot load selling pages without at least a userid.");
      return;
    }

    String myEBayURL = Externalized.getString("ebayServer.protocol") + Externalized.getString("ebayServer.sellingListHost") + Externalized.getString("ebayServer.V3file") +
        Externalized.getString("ebayServer.listedCGI") +
        Externalized.getString("ebayServer.sortOrderCGI") +
        Externalized.getString("ebayServer.userIdCGI") + userId;

    JHTML htmlDocument = new JHTML(myEBayURL, userCookie, mCleaner);

    if (htmlDocument.isLoaded()) {
      ItemResults rval = addAllItemsOnPage(htmlDocument, label, userId.equals(curUser));
      int count = rval.getFirst().size();

      MQFactory.getConcrete("Swing").enqueue("Loaded " + count + " new items for seller " + userId);
    } else {
      JConfig.log().logMessage("getSellingItems failed!");
    }
  }

  /**
   * @brief Load all items we can find in the 'My eBay' bidding page.
   * <p/>
   * Unfortunately, that can include items in the 'You might like...' area.
   *
   * @param curUser - The current user that we are loading My eBay for.
   * @param label - What 'group' to label items retrieved this way as.
   */
  void getMyEbayItems(String curUser, String label) {
    CookieJar cj = mLogin.getNecessaryCookie(false);
    String userCookie = null;
    ItemResults rval;
    if (cj != null) userCookie = cj.toString();

    if (curUser == null || curUser.equals("default")) {
      JConfig.log().logMessage("Cannot load My eBay pages without a userid and password.");
      return;
    }

    Map<String, String> collatedItems = new LinkedHashMap<String,String>();
    int newWatchCount = pullWatchingItems(curUser, userCookie, collatedItems);

    //  Get items you're watching
    rval = getAllItemsOnPage(new JHTML(Externalized.getString("ebayServer.oldWatching"), userCookie, mCleaner));
    collatedItems.putAll(rval.getLast());
    newWatchCount += rval.getFirst().size();
    int watchCount = collatedItems.size();
    newWatchCount = Math.min(newWatchCount, watchCount);

    //  Get items you're bidding on
    rval = getBiddingOnItems(label);
    collatedItems.putAll(rval.getLast());
    int newBidCount = rval.getFirst().size();
    int bidCount = rval.getLast().size();

    //  Get items you're selling
    newWatchCount += pullSellingItems(curUser, userCookie, collatedItems);
    //  There's no overlap between selling and bidding, but there might be on watching and selling.
    newWatchCount = Math.min(newWatchCount, collatedItems.size());

    addAll(collatedItems.values(), label, true);
    reportMyeBayResults(label, watchCount, newWatchCount, bidCount, newBidCount);
  }

  private int pullWatchingItems(String curUser, String userCookie, Map<String, String> collatedItems) {
    return pullItems("ebayServer.watchingURLPaginated", curUser, userCookie, collatedItems);
  }

  private int pullSellingItems(String curUser, String userCookie, Map<String, String> collatedItems) {
    return pullItems("ebayServer.sellingURLPaginated", curUser, userCookie, collatedItems);
  }

  private int pullItems(String propertyKey, String curUser, String userCookie, Map<String, String> collatedItems) {
    int page = 1;
    int newWatchCount = 0;
    ItemResults rval;
    boolean doneWatching = false;
    while (!doneWatching) {
      //  First load items that the user is watching (!)
      //    String watchingURL = Externalized.getString("ebayServer.watchingURL");
      String watchingURL = generateWatchedItemsURL(propertyKey, curUser, page);
      JHTML htmlDocument = getWatchedItemsPage(userCookie, watchingURL);
      String nextPage = null;
      if(htmlDocument.isLoaded()) {
        rval = getAllItemsOnPage(htmlDocument);
        collatedItems.putAll(rval.getLast());

        newWatchCount += rval.getFirst().size();

        nextPage = htmlDocument.getLinkForContent("Next");
        page++;
      }

      if (nextPage == null) doneWatching = true;
    }
    return newWatchCount;
  }

  //  Now load items the user is bidding on...
  private ItemResults getBiddingOnItems(String userCookie) {
    String biddingURL = Externalized.getString("ebayServer.biddingURL");
    JConfig.log().logDebug("Loading page: " + biddingURL);

    JHTML htmlDocument = new JHTML(biddingURL, userCookie, mCleaner);
    return getAllItemsOnPage(htmlDocument);
  }

  /**
   * If the My eBay search has a destination category/tab name, check to
   * see if it has any entries.  If not, display the results in the 'current'
   * tab, otherwise display them in the appropriate tab.
   *
   * @param label - The label to potentially show the results in.
   * @param watchCount - The number of items found in the watch list.
   * @param newWatchCount - The count of items in your watch list that aren't already in JBidwatcher.
   * @param bidCount - The number of items found in the bid list.
   * @param newBidCount - The count of items you've bid on that aren't already in JBidwatcher.
   */
  private void reportMyeBayResults(String label, int watchCount, int newWatchCount, int bidCount, int newBidCount) {
    String watchInfo = watchCount == 0 ? "" : " (about " + newWatchCount + " new)";
    String bidInfo = bidCount == 0 ? "" : " (" + newBidCount + " new)";
    String reportTab = "current";
    if(label != null) {
      Category c = Category.findFirstByName(label);
      if(c !=  null) {
        int count = EntryCorral.countByCategory(c);
        if(count != 0) reportTab = label;
      }
    }
    reportTab = reportTab + " Tab";
    String report = "Found " + watchCount + " watched items" + watchInfo;
    if(bidCount != 0) report += ", and " + bidCount + " items" + bidInfo + " you've apparently bid on";
    report += '.';
    MQFactory.getConcrete(reportTab).enqueue("REPORT " + report);
    MQFactory.getConcrete(reportTab).enqueue("SHOW");
    SuperQueue.getInstance().preQueue("HIDE", reportTab, System.currentTimeMillis() + Constants.ONE_MINUTE);
  }

  private String generateWatchedItemsURL(String propertyKey, String curUser, int page) {
    String watchingURL = Externalized.getString(propertyKey);
    watchingURL = watchingURL.replace("{page}", Integer.toString(page));

    JConfig.log().logDebug("Loading page " + page + " of My eBay for user " + curUser);
    JConfig.log().logDebug("URL: " + watchingURL);
    return watchingURL;
  }

  private JHTML getWatchedItemsPage(String userCookie, String watchingURL) {
    JHTML htmlDocument = new JHTML(watchingURL, userCookie, mCleaner);
    if(htmlDocument.isLoaded()) {
      if (htmlDocument.getTitle().equals("eBay Message")) {
        JConfig.log().logDebug("eBay is presenting an interstitial 'eBay Message' page!");
        JHTML.Form f = htmlDocument.getFormWithInput("MfcISAPICommand");
        if (f != null) {
          try {
            JConfig.log().logDebug("Navigating to the 'Continue to My eBay' page.");
            htmlDocument = new JHTML(f.getCGI(), userCookie, mCleaner);
          } catch (UnsupportedEncodingException uee) {
            JConfig.log().handleException("Failed to get the real My eBay page", uee);
          }
        }
      }
      //  If there's a link with content '200', then it's the new My eBay
      //  page, and the 200 is to show that many watched items on the page.
//      String biggestLink = htmlDocument.getLinkForContent("200");
//      if (biggestLink != null) {
//        JConfig.log().logDebug("Navigating to the '200 at a time' watching page: " + biggestLink);
//        htmlDocument = new JHTML(biggestLink, userCookie, mCleaner);
//      }
    }
    return htmlDocument;
  }

  /**
   * Check for searches, and execute one if that's what is requested.
   * @brief Given a search string, send it to eBay's search, and gather up the results.

   * @param searcher   - The Searcher object containing the string to search for.
   * @param label      - What 'group' to label items retrieved this way as.
   * @param title_only - Should the search focus on the titles only, or titles and descriptions?
   */
  void loadSearchString(Object searcher, String label, boolean title_only) {
    String search = ((Searcher) searcher).getSearch();
    //  This should be encode(search, "UTF-8"); but that's a 1.4+ feature!
    //  Ignore the deprecation warning for this one.
    String encodedSearch;
    try {
      encodedSearch = URLEncoder.encode(search, "UTF-8");
    } catch (UnsupportedEncodingException ignored) {
      encodedSearch = null;
      JConfig.log().logMessage("Failed to search because of encoding transformation failure.");
    }
    int allResults = 0;

    if (encodedSearch != null) {
      MQFactory.getConcrete("Swing").enqueue("Searching for: " + search);
      String sacur = "";

      String currency = ((Searcher) searcher).getCurrency();
      if (currency != null) sacur = "&sacur=" + currency;

      String fullSearch;

      if (title_only) {
        fullSearch = Externalized.getString("ebayServer.searchURL1") + encodedSearch + sacur + Externalized.getString("ebayServer.searchURLNoDesc");
      } else {
        fullSearch = Externalized.getString("ebayServer.searchURL1") + encodedSearch + sacur + Externalized.getString("ebayServer.searchURL2");
      }
      String baseSearch = fullSearch;
      int skipCount = 0;
      boolean done;

      do {
        done = true;

        CookieJar cj = mLogin.getNecessaryCookie(false);
        String userCookie = null;
        if (cj != null) userCookie = cj.toString();
        JHTML htmlDocument = new JHTML(fullSearch, userCookie, mCleaner);
        if (htmlDocument.isLoaded()) {
          ItemResults rval = addAllItemsOnPage(htmlDocument, label, !((Searcher) searcher).shouldSkipDeleted());
          int pageResults = rval.getLast().size();
          if (pageResults != 0) {
            if (pageResults >= ITEMS_PER_PAGE) {
              skipCount += ITEMS_PER_PAGE;
              fullSearch = baseSearch + "&skip=" + skipCount;
              done = false;
            }

            allResults += pageResults;
          }
        }
      } while (!done);
    }

    if (allResults == 0) {
      MQFactory.getConcrete("Swing").enqueue("No new results found for search: " + search);
    } else {
      MQFactory.getConcrete("Swing").enqueue("Done searching for: " + search);
    }
  }
}
