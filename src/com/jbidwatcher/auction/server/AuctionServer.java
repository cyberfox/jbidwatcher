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
import com.jbidwatcher.util.http.CookieJar;
import com.jbidwatcher.util.http.Http;
import com.jbidwatcher.util.*;
import com.jbidwatcher.search.SearchManagerInterface;
import com.jbidwatcher.*;
import com.jbidwatcher.auction.AuctionEntry;
import com.jbidwatcher.auction.SpecificAuction;
import com.jbidwatcher.auction.AuctionInfo;

import java.util.*;
import java.net.*;
import java.io.*;

public abstract class AuctionServer implements AuctionServerInterface {
  public enum ParseErrors {
    SUCCESS,
    NOT_ADULT,
    BAD_TITLE,
    SELLER_AWAY,
    ERROR
  }
  private static long sLastUpdated = 0;

  //  Note: JBidProxy
  public abstract CookieJar getNecessaryCookie(boolean force);

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

  /**
   * @brief Fills an XMLElement's attributes with the username and password
   * for the current auction server user.
   *
   * @param auth - The XMLElement to have user-auth related attributes added to.
   */
  public abstract void storeAuthorization(XMLElement auth);

  /**
   * @brief Loads authorization information from an XMLElement's attributes
   * for the current auction server user.
   *
   * @param auth - The XMLElement to load user-auth related attributes from.
   */
  public abstract void loadAuthorization(XMLElement auth);

  /**
   * @brief Get the official 'right now' time from the server.
   *
   * @return - The current time as reported by the auction site's 'official time' mechanism.
   */
  protected abstract Date getOfficialTime();

  /**
   * @brief Get a URL that points to the item on the auction site's server.
   *
   * @param itemID - The item to get the URL for.
   * @return - A URL that refers to the item at the auction site.
   */
  protected abstract URL getURLFromItem(String itemID);

  /**
   * Get the full text of an auction from the auction server.
   *
   * @param id - The item id for the item to retrieve from the server.
   *
   * @return - The full text of the auction from the server, or null if it wasn't found.
   */
  public abstract StringBuffer getAuction(String id);

  /**
   * @brief Get the current time inline with the current thread.  This will
   * block until it's done getting the time.
   */
  public void reloadTime() {
    if (getOfficialTime() != null) {
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
      CookieJar curCook = getNecessaryCookie(false);
      URLConnection uc;
      if(curCook != null) {
        uc = curCook.getAllCookiesFromPage(auctionURL.toString(), null, false, null);
      } else {
        uc = Http.makeRequest(auctionURL, null);
      }
      loadedPage = Http.receivePage(uc);
      if(loadedPage != null && loadedPage.length() == 0) loadedPage = null;
    } catch(FileNotFoundException fnfe) {
      ErrorManagement.logDebug("Item not found: " + auctionURL.toString());
      throw fnfe;
    } catch(IOException e) {
      ErrorManagement.handleException("Error loading URL (" + auctionURL.toString() + ')', e);
      loadedPage = null;
    }
    return loadedPage;
  }

  /**
   * @brief Given an auction entry, reload/update the core auction information from the server.
   *
   * Note: AuctionEntry
   *
   * @param inEntry - The auction to update.
   *
   * @return - The core auction information that has been set into the
   * auction entry, or null if the update failed.
   */
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
      if(!inEntry.getLastStatus().equals("Seller away - item unavailable.")) {
        inEntry.setLastStatus("Failed to load from server!");
      }
      inEntry.setInvalid();
    }

    return (curAuction);
  }

  /**
   * @brief Load an auction, given its URL, its item id, and its 'AuctionEntry'.
   *
   * BUGBUG - This function should not require either of the last two entries.
   *
   * @param auctionURL - The URL to load the auction from.
   * @param item_id - The item # to associate this returned info with.
   * @param ae - An object to notify when an error occurs.
   *
   * @return - An object containing the information extracted from the auction.
   */
  private AuctionInfo loadAuction(URL auctionURL, String item_id, AuctionEntry ae) {
    StringBuffer sb = getAuction(item_id);

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
      ParseErrors result = curAuction.parseAuction(ae);
      if (result == ParseErrors.SUCCESS) {
        curAuction.save();
      } else {
        switch(result) {
          case NOT_ADULT: {
            boolean isAdult = JConfig.queryConfiguration(getName() + ".adult", "false").equals("true");
            if (isAdult) {
              getNecessaryCookie(true);
            } else {
              ErrorManagement.logDebug("Failed to load adult item, user possibly not marked for Mature Items access.  Check your eBay configuration.");
            }
          }
          case BAD_TITLE: {
            //  ?
          }
        }
        error = "Bad Parse!";
      }
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

  //  TODO -- Make sense out of this.
  private void checkLogError(AuctionEntry ae) {
    if (ae != null) {
      ae.logError();
    } else {
      markCommunicationError(ae);
    }
  }

  private void markCommunicationError(AuctionEntry ae) {
    if (ae != null) {
      MQFactory.getConcrete("Swing").enqueue("LINK DOWN Communications failure talking to the server during item #" + ae.getIdentifier() + "( " + ae.getTitle() + " )");
    } else {
      MQFactory.getConcrete("Swing").enqueue("LINK DOWN Communications failure talking to the server");
    }
  }

  public int getCount() {
    return AuctionServerManager.getInstance().getDB().count();
  }
}
