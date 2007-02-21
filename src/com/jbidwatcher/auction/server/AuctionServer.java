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
import com.jbidwatcher.util.html.JHTML;
import com.jbidwatcher.util.http.CookieJar;
import com.jbidwatcher.util.http.Http;
import com.jbidwatcher.util.*;
import com.jbidwatcher.util.Currency;
import com.jbidwatcher.search.SearchManagerInterface;
import com.jbidwatcher.*;
import com.jbidwatcher.auction.AuctionEntry;
import com.jbidwatcher.auction.SpecificAuction;
import com.jbidwatcher.auction.AuctionInfo;

import java.util.*;
import java.net.*;
import java.io.*;

public abstract class AuctionServer implements AuctionServerInterface {
  private static long sLastUpdated = 0;

  //  Note: JBidProxy
  public abstract CookieJar getNecessaryCookie(boolean force);

  //  TODO - The following are exposed to and used by the Snipe class only.  Is there another way?
  public abstract CookieJar getSignInCookie(CookieJar old_cj);
  public abstract JHTML.Form getBidForm(CookieJar cj, AuctionEntry inEntry, com.jbidwatcher.util.Currency inCurr, int inQuant) throws BadBidException;
  public abstract int placeFinalBid(CookieJar cj, JHTML.Form bidForm, AuctionEntry inEntry, Currency inBid, int inQuantity);

  //  UI functions
  //  Note: AuctionServerManager
  public abstract void establishMenu();
  public abstract JConfigTab getConfigurationTab();

  // TODO -- A better search structure would be nice.
  //  Note: AuctionServerManager
  public abstract void cancelSearches();
  public abstract void addSearches(SearchManagerInterface searchManager);

  //  Exposed to AuctionEntry for checking high bidder status.
  public abstract boolean isHighDutch(AuctionEntry inAE);
  public abstract void updateHighBid(AuctionEntry ae);

  /**
   * @brief Get the string-form URL for a given item ID on this
   * auction server, for when we aren't browsing.
   *
   * Note: AuctionEntry, JBWDropHandler
   * 
   * @param itemID - The item to retrieve the URL for.
   * 
   * @return - A String with the full URL of the item description on the auction server.
   */
  public abstract String getStringURLFromItem(String itemID);

  /**
   * @brief Allocate a new auction that is of this auction server's specific subtype.
   *
   * In order for the auctions to be able to fill out relevant
   * information, each has to have a subclass of AuctionInfo dedicated
   * to it.  This abstract function is defined by the auction server
   * specific classes, and actually returns an object of their type of
   * 'auction'.
   *
   *  Note: AuctionEntry
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

  public abstract void setAuthorization(XMLElement auth);
  public abstract void extractAuthorization(XMLElement auth);

  /**
   * @return - The current time as reported by the auction site's 'official time' mechanism.
   * @brief Get the official 'right now' time from the server.
   */
  protected abstract Date getOfficialTime();

  /**
   * @param itemID - The item to get the URL for.
   * @return - A URL that refers to the item at the auction site.
   * @brief Get a java.net.URL that points to the item on the auction site's server.
   */
  protected abstract URL getURLFromItem(String itemID);

  protected abstract StringBuffer getAuction(AuctionEntry ae, String id);

  public void reloadTime() {
    if (setTimeDifference()) {
      MQFactory.getConcrete("Swing").enqueue("Successfully synchronized time with " + getName() + '.');
    } else {
      MQFactory.getConcrete("Swing").enqueue("Failed to synchronize time with " + getName() + '!');
    }
  }

  /**
   * @brief Show an auction entry in the browser.
   * TODO -- Move this someplace more sane.
   * Note: JBidMouse
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

  //  Generalized logic
  //  -----------------
  //  Note: AuctionEntry
  public AuctionInfo createAuction(String itemId) {
    URL auctionURL = getURLFromItem(itemId);

    return loadAuction(auctionURL, itemId, null);
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
    if(auctionURL == null) return null;
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

  //  Note: AuctionEntry
  public AuctionInfo reloadAuction(AuctionEntry inEntry) {
    URL auctionURL = getURLFromItem(inEntry.getIdentifier());

    SpecificAuction curAuction = (SpecificAuction) loadAuction(auctionURL, inEntry.getIdentifier(), inEntry);

    if (curAuction != null) {
      AuctionServerManager.getInstance().deleteEntry(inEntry);
      inEntry.setAuctionInfo(curAuction);
      inEntry.clearInvalid();
      AuctionServerManager.getInstance().addEntry(inEntry);
      MQFactory.getConcrete("Swing").enqueue("LINK UP");
    } else {
      inEntry.setLastStatus("Failed to load from server!");
      inEntry.setInvalid();
    }

    return (curAuction);
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
   * @return - An object containing the information extracted from the auction.
   */
  private AuctionInfo loadAuction(URL auctionURL, String item_id, AuctionEntry ae) {
    StringBuffer sb = getAuction(ae, item_id);

    if (sb == null) {
      try {
        sb = getAuction(auctionURL);
      } catch (FileNotFoundException ignored) {
        //  Just get out.  The item no longer exists on the auction
        //  server, so we shouldn't be trying any of the rest.  The
        //  Error should have been logged at the lower level, so just
        //  punt.  It's not a communications error, either.
        return null;
      } catch (Exception catchall) {
        if (JConfig.debugging()) {
          ErrorManagement.handleException("Some unexpected error occurred during loading the auction.", catchall);
        }
      }
    }

    SpecificAuction curAuction = null;
    if(sb != null) {
      curAuction = doParse(sb, ae, item_id);
    }

    if(curAuction == null) {
      noteRetrieveError(ae);
    }
    return curAuction;
  }

  private SpecificAuction doParse(StringBuffer sb, AuctionEntry ae, String item_id) {
    SpecificAuction curAuction = getNewSpecificAuction();

    if (item_id != null) {
      curAuction.setIdentifier(item_id);
    }
    curAuction.setContent(sb, false);
    String error = null;
    if (curAuction.preParseAuction()) {
      if (curAuction.parseAuction(ae)) {
        curAuction.save();
      } else error = "Bad Parse!";
    } else error = "Bad pre-parse!";

    if(error != null) {
      ErrorManagement.logMessage(error);
      checkLogError(ae);
    }
      return curAuction;
  }

  private void noteRetrieveError(AuctionEntry ae) {
    checkLogError(ae);
    //  Whoops!  Bad thing happened on the way to loading the auction!
    ErrorManagement.logDebug("Failed to parse auction!  Bad return result from auction server.");
    //  Only retry the login cookie once every ten minutes of these errors.
    if ((sLastUpdated + Constants.ONE_MINUTE * 10) > System.currentTimeMillis()) {
      sLastUpdated = System.currentTimeMillis();
      MQFactory.getConcrete(getName()).enqueue(new AuctionQObject(AuctionQObject.MENU_CMD, UPDATE_LOGIN_COOKIE, null)); //$NON-NLS-1$ //$NON-NLS-2$
    }
  }

  private void checkLogError(AuctionEntry ae) {
    if (ae != null) {
      ae.logError();
    } else {
      markCommunicationError(ae);
    }
  }

  private boolean setTimeDifference() {
    return getOfficialTime() != null;
  }
}
