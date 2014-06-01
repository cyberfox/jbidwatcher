package com.jbidwatcher.auction.server;
/*
 * Copyright (c) 2000-2007, CyberFOX Software, Inc. All Rights Reserved.
 *
 * Developed by mrs (Morgan Schweers)
 */

/*
 * @file   AuctionServer.java
 * @author Morgan Schweers <cyberfox@jbidwatcher.com>
 * @date   Wed Oct  9 13:49:02 2002
 * @note   Library GPL'ed.
 * @brief  This is an interface description for the general auction Servers
 *
 * It allows abstracting the auction setup to a factory creator, so
 * the factory can identify which site (ebay, yahoo, amazon, etc.) it
 * is, and do the appropriate parsing for that site.
 */
import com.jbidwatcher.util.config.*;
import com.jbidwatcher.util.queue.MQFactory;
import com.jbidwatcher.util.queue.AuctionQObject;
import com.jbidwatcher.util.http.CookieJar;
import com.jbidwatcher.util.http.Http;
import com.jbidwatcher.util.Constants;
import com.jbidwatcher.util.StringTools;
import com.jbidwatcher.search.SearchManagerInterface;
import com.jbidwatcher.auction.*;

import java.util.*;
import java.net.*;
import java.io.*;

public abstract class AuctionServer implements AuctionServerInterface {
  private static long sLastUpdated = 0;

  public String stripId(String source) {
    String strippedId = source;

    if (source.startsWith("http")) {
      strippedId = extractIdentifierFromURLString(source);
    }

    return strippedId;
  }

  private static class ReloadItemException extends Exception { }

  //  Note: JBidProxy
  public abstract CookieJar getNecessaryCookie(boolean force);

  //  UI functions
  //  Note: AuctionServerManager
  public abstract ServerMenu establishMenu();

  //  Note: AuctionServerManager
  public abstract void cancelSearches();
  public abstract void addSearches(SearchManagerInterface searchManager);

    //  Exposed to AuctionEntry for checking high bidder status.
  public abstract void updateHighBid(String ae);

  public abstract void setSnipe(String auctionId);
  public abstract void cancelSnipe(String identifier);


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
   * @brief Get the official 'right now' time from the server.
   *
   * @return - The current time as reported by the auction site's 'official time' mechanism.
   */
  protected abstract Date getOfficialTime();

  /**
   * @brief Given a site-dependant item ID, get the URL for that item.
   *
   * @param itemID - The eBay item ID to get a net.URL for.
   *
   * @return - a URL to use to pull that item.
   */
  protected URL getURLFromItem(String itemID) {
    return StringTools.getURLFromString(getStringURLFromItem(itemID));
  }

  /**
   * Get the full text of an auction from the auction server.
   *
   * @param id - The item id for the item to retrieve from the server.
   *
   * @return - The full text of the auction from the server, or null if it wasn't found.
   * @throws java.io.FileNotFoundException - If the auction is gone from the server.
   */
  protected abstract StringBuffer getAuction(String id) throws FileNotFoundException;

  /**
   * @brief Get the current time inline with the current thread.  This will
   * block until it's done getting the time.
   */
  public void reloadTime() {
    if (getOfficialTime() != null) {
      MQFactory.getConcrete("Swing").enqueue("Successfully synchronized time with " + getFriendlyName() + '.');
      JConfig.log().logMessage("Time delta with " + getFriendlyName() + " is " + getServerTimeDelta());
    } else {
      MQFactory.getConcrete("Swing").enqueue("Failed to synchronize time with " + getFriendlyName() + '!');
    }
  }

  //  Generalized logic
  //  -----------------
  //  Note: AuctionEntry
  public AuctionInfo create(String itemId) {
    return loadAuction(itemId, null);
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
        uc = curCook.connect(auctionURL.toString());
      } else {
        uc = Http.net().makeRequest(auctionURL, null);
      }
      loadedPage = Http.net().receivePage(uc);
      if(loadedPage != null && loadedPage.length() == 0) {
        loadedPage = null;
      }
    } catch(FileNotFoundException fnfe) {
      JConfig.log().logDebug("Item not found: " + auctionURL.toString());
      throw fnfe;
    } catch(IOException e) {
      JConfig.log().handleException("Error loading URL (" + auctionURL.toString() + ')', e);
      loadedPage = null;
    }
    return loadedPage;
  }

  /**
   * @brief Given an auction entry, reload/update the core auction information from the server.
   *
   * Note: AuctionEntry
   *
   * @param auctionId
   * @return - The core auction information that has been set into the
   * auction entry, or null if the update failed.
   */
  public void reload(String auctionId) {
    AuctionEntry ae = (AuctionEntry) EntryCorral.getInstance().takeForWrite(auctionId);
    SpecificAuction curAuction;
    try {
      curAuction = (SpecificAuction) loadAuction(auctionId, ae);

      if (curAuction != null) {
        curAuction.saveDB();
        ae.setAuctionInfo(curAuction);
        ae.clearInvalid();
        MQFactory.getConcrete("Swing").enqueue("LINK UP");
      } else {
        if(!ae.isDeleted() && !ae.getLastStatus().contains("Seller away - item unavailable.")) {
          ae.setLastStatus("Failed to load from server!");
          ae.setInvalid();
        }
      }
    } finally {
      EntryCorral.getInstance().release(auctionId);
    }
  }

  /**
   * @brief Load an auction, given its item id, and its 'AuctionEntry'.
   *
   * @param item_id - The item # to associate this returned info with.
   * @param ae - An object to notify when an error occurs.
   *
   * @return - An object containing the information extracted from the auction.
   */
  private AuctionInfo loadAuction(String item_id, AuctionEntry ae) {
    StringBuffer sb = null;
    SpecificAuction curAuction = null;
    int runCount = 0;

    // Retry loop
    while(sb == null && runCount < 2) {
      sb = retrieveAuction(item_id, ae);

      // If there was a failure retrieving the HTML itself, we probably got a 404.
      if(sb == null && (ae == null || ae.isDeleted())) {
        if(ae != null) ae.saveDB();
        return null;
      }

      try {
        curAuction = doParse(sb, ae, item_id);
      } catch (ReloadItemException e) {
        sb = null;
      }
      runCount++;
    }

    if (curAuction == null) {
      JConfig.log().logMessage("Multiple failures attempting to load item " + item_id + ", giving up.");

      if (ae != null && ae.getLastStatus().contains("Seller away - item unavailable.")) {
        ae.setInvalid();
      } else if (ae == null || !ae.isDeleted()) {
        noteRetrieveError(ae);
      }
    }
    return curAuction;
  }

  private StringBuffer retrieveAuction(String item_id, AuctionEntry ae) {
    StringBuffer sb = null;

    try {
      sb = getAuction(item_id);
      if(sb != null && ae != null) {
        ae.clearDeleted();
      }
    } catch (FileNotFoundException ignored) {
      //  Just get out.  The item no longer exists on the auction
      //  server, so we shouldn't be trying any of the rest.  The
      //  Error should have been logged at the lower level, so just
      //  punt.  It's not a communications error, either.
      markAuctionDeleted(ae);
    } catch (Exception catchall) {
      if (JConfig.debugging()) {
        JConfig.log().handleException("Some unexpected error occurred during loading the auction.", catchall);
      }
    }

    return sb;
  }

  public SpecificAuction doParse(StringBuffer sb) throws ReloadItemException {
    return doParse(sb, null, null);
  }

  private SpecificAuction doParse(StringBuffer sb, AuctionEntry ae, String item_id) throws ReloadItemException {
    SpecificAuction curAuction = getNewSpecificAuction();

    if (item_id != null) {
      curAuction.setIdentifier(item_id);
    } else if(ae != null && ae.getIdentifier() != null) {
      curAuction.setIdentifier(ae.getIdentifier());
    }
    curAuction.setContent(sb, false);
    String error = null;
    SpecificAuction.ParseErrors result = null;
    if (curAuction.preParseAuction()) {
      result = curAuction.parseAuction(ae);
      if (result != SpecificAuction.ParseErrors.SUCCESS) {
        switch(result) {
          case WRONG_SITE: {
            String rightURL = curAuction.getURL();
            JConfig.log().logDebug("Need to redirect to: " + rightURL);
            JConfig.log().logMessage("Attempted to read an auction that is not available on the default site; check eBay's non-US configuration.");
            return null;
          }
          case CAPTCHA: {
            JConfig.log().logDebug("Failed to load (likely adult) item, captcha intervened.");
            if(ae != null) {
              ae.setLastStatus("Couldn't access auction on server; captcha blocked.");
            }
            break;
          }
          case NOT_ADULT: {
            boolean isAdult = JConfig.queryConfiguration(getName() + ".mature", "false").equals("true");
            if (isAdult) {
              getNecessaryCookie(true);
              throw new ReloadItemException();
            } else {
              JConfig.log().logDebug("Failed to load adult item, user possibly not marked for Mature Items access.  Check your eBay configuration.");
            }
            break;
          }
          case DELETED: {
            error = markAuctionDeleted(ae);
            break;
          }
          case SELLER_AWAY: {
            error = "Seller away - item unavailable.";
            break;
          }
          case BAD_TITLE: {
            error = "There was a problem parsing the title.";
            break;
          }
        }
        if(result != SpecificAuction.ParseErrors.SUCCESS && error == null) {
          error = "Bad Parse!";
        }
      }
      if (result == SpecificAuction.ParseErrors.SUCCESS) {
        curAuction.save();
      }
    } else {
      error = "Bad pre-parse!";
    }

    if(error != null) {
      JConfig.log().logMessage(error);
      if(ae == null || !ae.isDeleted() && result != SpecificAuction.ParseErrors.SELLER_AWAY) {
        checkLogError(ae);
      }
      curAuction = null;
    }
    return curAuction;
  }

  private String markAuctionDeleted(AuctionEntry ae) {
    String error = "Auction appears to have been removed from the site.";
    if(ae != null) {
      ae.setDeleted();
      error = "Auction " + ae.getIdentifier() + " appears to have been removed from the site.";
      ae.setLastStatus(error);
    }
    return error;
  }

  private void noteRetrieveError(AuctionEntry ae) {
    checkLogError(ae);
    //  Whoops!  Bad thing happened on the way to loading the auction!
    JConfig.log().logDebug("Failed to parse auction!  Bad return result from auction server.");
    //  Only retry the login cookie once every ten minutes of these errors.
    if ((sLastUpdated + Constants.ONE_MINUTE * 10) > System.currentTimeMillis()) {
      sLastUpdated = System.currentTimeMillis();
      MQFactory.getConcrete(this.getFriendlyName()).enqueueBean(new AuctionQObject(AuctionQObject.MENU_CMD, UPDATE_LOGIN_COOKIE, null)); //$NON-NLS-1$ //$NON-NLS-2$
    }
  }

  /**
   * If we had an auction entry, note the failure on it's record.
   * Otherwise, note a general communications failure.
   *
   * @param ae - The optional auction entry.
   */
  private void checkLogError(AuctionEntry ae) {
    if (ae != null) {
      ae.logError();
    } else {
      MQFactory.getConcrete("Swing").enqueue("LINK DOWN Communications failure talking to the server");
    }
  }

  protected abstract String getUserId();

  /**
   * Check to see if the provided user name is the current app user.
   *
   * @param username - The username to check.
   * @return - false if username is null, or if the current user is the 'default' user, or if the username provided is different
   * than the current username.  True if the current app user is the same as the username passed in.
   */
  public boolean isCurrentUser(String username) {
    return !(username == null || isDefaultUser()) && getUserId().trim().equalsIgnoreCase(username.trim());
  }
}
