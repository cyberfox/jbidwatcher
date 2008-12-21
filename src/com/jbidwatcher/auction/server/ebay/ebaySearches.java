package com.jbidwatcher.auction.server.ebay;

/*
 * Copyright (c) 2000-2007, CyberFOX Software, Inc. All Rights Reserved.
 *
 * Developed by mrs (Morgan Schweers)
 */

import com.jbidwatcher.util.html.JHTML;
import com.jbidwatcher.util.config.JConfig;
import com.jbidwatcher.util.Externalized;
import com.jbidwatcher.util.http.CookieJar;
import com.jbidwatcher.auction.AuctionServerInterface;
import com.jbidwatcher.auction.server.AuctionServerManager;
import com.jbidwatcher.auction.LoginManager;
import com.jbidwatcher.util.html.CleanupHandler;
import com.jbidwatcher.util.queue.MQFactory;
import com.jbidwatcher.util.queue.DropQObject;
import com.jbidwatcher.search.Searcher;

import java.util.List;
import java.util.ListIterator;
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
  private CleanupHandler mCleaner;
  private com.jbidwatcher.auction.LoginManager mLogin;

  public ebaySearches(CleanupHandler cleaner, LoginManager login) {
    mCleaner = cleaner;
    mLogin = login;
  }

  /**
   * @param htmlDocument - The document to get all the items from.
   * @param category     - What 'group' to label items retrieved this way as.
   * @param interactive  - Is this operation being done interactively, by the user?
   * @return - A count of items added.
   * @brief Add all the items on the page to the list of monitored auctions.
   */
  private int addAllItemsOnPage(JHTML htmlDocument, String category, boolean interactive) {
    List<String> allItemsOnPage = htmlDocument.getAllURLsOnPage(true);
    int item_count = 0;

    if (allItemsOnPage == null) {
      JConfig.log().logDebug("No items on page!");
    } else {
      for (ListIterator<String> it = allItemsOnPage.listIterator(); it.hasNext();) {
        String url = it.next();

        url = url.replaceAll("\n|\r", "");
        boolean gotNext;
        String nextURL;

        if (it.hasNext()) {
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
          AuctionServerInterface aucServ = AuctionServerManager.getInstance().getServer();
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
        results += addAllItemsOnPage(htmlDocument, label, !((Searcher) searcher).shouldSkipDeleted());
      }
    }

    if (results == 0) {
      MQFactory.getConcrete("Swing").enqueue("Failed to load from URL " + urlStr);
    } else {
      MQFactory.getConcrete("Swing").enqueue("Done loading from URL " + urlStr);
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
      int count = addAllItemsOnPage(htmlDocument, label, userId.equals(curUser));
      MQFactory.getConcrete("Swing").enqueue("Loaded " + count + " items for seller " + userId);
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
    if (cj != null) userCookie = cj.toString();

    if (curUser == null || curUser.equals("default")) {
      JConfig.log().logMessage("Cannot load My eBay pages without a userid and password.");
      return;
    }

    int page = 0;
    boolean done_watching = false;
    while (!done_watching) {
      //  First load items that the user is watching (!)
      //    String watchingURL = Externalized.getString("ebayServer.watchingURL");
      String watchingURL = Externalized.getString("ebayServer.bigWatchingURL1") + curUser +
          Externalized.getString("ebayServer.bigWatchingURL2") + page +
          Externalized.getString("ebayServer.bigWatchingURL3") + (page + 1);
      JConfig.log().logDebug("Loading page " + page + " of My eBay for user " + curUser);
      JConfig.log().logDebug("URL: " + watchingURL);

      JHTML htmlDocument = new JHTML(watchingURL, userCookie, mCleaner);
      if(htmlDocument.getTitle().equals("eBay Message")) {
        JConfig.log().logDebug("eBay is presenting an interstitial 'eBay Message' page!");
        JHTML.Form f = htmlDocument.getFormWithInput("MfcISAPICommand");
        if(f != null) {
          try {
            JConfig.log().logDebug("Navigating to the 'Continue to My eBay' page.");
            htmlDocument = new JHTML(f.getCGI(), userCookie, mCleaner);
          } catch(UnsupportedEncodingException uee) {
            JConfig.log().handleException("Failed to get the real My eBay page", uee);
          }
        }
      }
      addAllItemsOnPage(htmlDocument, label, true);
      String ofX = htmlDocument.getNextContentAfterRegex("Page " + (page + 1));
      if (ofX == null || !ofX.startsWith("of ")) done_watching = true;
      else try {
        done_watching = (page + 1) == Integer.parseInt(ofX.substring(3));
      } catch (NumberFormatException ignored) {
        done_watching = true;
      }
      if (!done_watching) page++;
    }

    boolean done_bidding = false;
    while (!done_bidding) {
      //  Now load items the user is bidding on...
      String biddingURL = Externalized.getString("ebayServer.biddingURL");
      JConfig.log().logDebug("Loading page: " + biddingURL);

      JHTML htmlDocument = new JHTML(biddingURL, userCookie, mCleaner);
      addAllItemsOnPage(htmlDocument, label, true);
      done_bidding = true;
    }
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
      int skipCount = 0;
      boolean done;

      do {
        done = true;

        CookieJar cj = mLogin.getNecessaryCookie(false);
        String userCookie = null;
        if (cj != null) userCookie = cj.toString();
        JHTML htmlDocument = new JHTML(fullSearch, userCookie, mCleaner);
        if (htmlDocument.isLoaded()) {
          int pageResults = addAllItemsOnPage(htmlDocument, label, !((Searcher) searcher).shouldSkipDeleted());
          if (pageResults != 0) {
            if (pageResults >= ITEMS_PER_PAGE) {
              skipCount += ITEMS_PER_PAGE;
              fullSearch = new StringBuffer(Externalized.getString("ebayServer.searchURL1")).append(encodedSearch).append(sacur).append(title_only ? Externalized.getString("ebayServer.searchURLNoDesc") : Externalized.getString("ebayServer.searchURL2")).append("&skip=").append(skipCount).toString();
              done = false;
            }

            allResults += pageResults;
          }
        }
      } while (!done);
    }

    if (allResults == 0) {
      MQFactory.getConcrete("Swing").enqueue("No results found for search: " + search);
    } else {
      MQFactory.getConcrete("Swing").enqueue("Done searching for: " + search);
    }
  }
}
