package com.jbidwatcher.auction;
/*
 * Copyright (c) 2000-2007, CyberFOX Software, Inc. All Rights Reserved.
 *
 * Developed by mrs (Morgan Schweers)
 */

import com.jbidwatcher.util.Constants;
import com.jbidwatcher.util.Currency;
import com.jbidwatcher.util.StringTools;
import com.jbidwatcher.auction.event.EventLogger;
import com.jbidwatcher.auction.event.EventStatus;
import com.jbidwatcher.util.config.*;
import com.jbidwatcher.util.queue.MQFactory;
import com.jbidwatcher.util.db.ActiveRecord;
import com.jbidwatcher.util.db.Table;
import com.jbidwatcher.util.xml.XMLElement;
import com.jbidwatcher.util.xml.XMLInterface;

import java.io.File;
import java.text.MessageFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * @brief Contains all the methods to examine, control, and command a
 * specific auction.
 *
 * Where the AuctionInfo class contains information which is purely
 * retrieved from the server, the AuctionEntry class decorates that
 * with things like when it was last updated, whether to snipe, any
 * comment the user might have made on it, etc.
 *
 * I.e. AuctionEntry keeps track of things that the PROGRAM needs to
 * know about the auction, not things that are inherent to auctions.
 *
 * This is not descended from AuctionInfo because the actual type of
 * AuctionInfo varies per server.
 *
 * @author Morgan Schweers
 * @see AuctionInfo
 * @see SpecificAuction
 */
public class AuctionEntry extends ActiveRecord implements Comparable<AuctionEntry>, EntryInterface {
  private Category mCategory;
  private static Resolver sResolver = null;

  public boolean equals(Object o) {
    return o instanceof AuctionEntry && compareTo((AuctionEntry) o) == 0;
  }

  /**
   * @brief Set a status message, and mark that the connection is currently invalid.
   */
  public void logError() {
    setLastStatus("Communications failure talking to the server.");
    setInvalid();
  }

  public Currency bestValue() {
    if (isSniped()) {
      return getSnipe().getAmount();
    }

    return isBidOn() && !isComplete() ? getBid() : getCurrentPrice();
  }

  public Currency getSnipeAmount() {
    return isSniped() ? getSnipe().getAmount() : Currency.NoValue();
  }

  public int getSnipeQuantity() {
    return isSniped() ? getSnipe().getQuantity() : 0;
  }

  private AuctionSnipe getSnipe() {
    if(mSnipe == null) {
      if(get("snipe_id") != null) {
        mSnipe = AuctionSnipe.find(get("snipe_id"));
        if(mSnipe == null) {
          //  Couldn't find the snipe in the database.
          setInteger("snipe_id", null);
          saveDB();
        }
      }
    }
    return mSnipe;
  }

  /** All the auction-independent information like high bidder's name,
   * seller's name, etc...  This is directly queried when this object
   * is queried about any of those fields.
   *
   */
  private AuctionInfo mAuction = null;

  /**
   * A logging class for keeping track of events.
   *
   * @see com.jbidwatcher.auction.event.EventLogger
   */
  private EventLogger mEntryEvents = null;

  /**
   * Have we ever obtained this auction data from the server?
   */
  private boolean mLoaded =false;

  /**
   * If this auction is part of a multiple-snipe, this value will not
   * be null, and will point to a MultiSnipe object.
   */
  private MultiSnipe mMultiSnipe =null;

  private AuctionSnipe mSnipe = null;

  /**
   * How much was a cancelled snipe for?  (Recordkeeping)
   */
  private Currency mCancelSnipeBid = null;

  /**
   * What AuctionServer is responsible for handling this
   * AuctionEntry's actions?
   */
  private AuctionServerInterface mServer =null;

  /**
   * The last time this auction was bid on.  Not presently used,
   * although set, saved, and loaded consistently.
   */
  private long mBidAt =0;

  /**
   * Delta in time from the end of the auction that sniping will
   * occur at.  It's possible to set a different snipe time for each
   * auction, although it's not presently implemented through any UI.
   */
  private long mSnipeAt = -1;

  /**
   * Default delta in time from the end of the auction that sniping
   * will occur at.  This valus can be read and modified by
   * getDefaultSnipeTime() & setDefaultSnipeTime().
   */
  private static long sDefaultSnipeAt = Constants.THIRTY_SECONDS;

  private StringBuffer mLastErrorPage = null;

  /**
   * Does all the jobs of the constructors, so that the constructors
   * become simple calls to this function.  Presets up all the
   * necessary variables, loads any data in, sets the lastUpdated
   * flag, all the timers, retrieves the auction if necessary.
   *
   * @param auctionIdentifier - Each auction site has an identifier that
   *                            is used to key the auction.
   */
  private synchronized void prepareAuctionEntry(String auctionIdentifier) {
    if (mServer != null) {
      mAuction = mServer.create(auctionIdentifier);
    }

    mLoaded = mAuction != null;

    /**
     * Note that a bad auction (couldn't get an auction server, or a
     * specific auction info object) doesn't have an identifier, and
     * isn't loaded.  This will fail out the init process, and this
     * will never be added to the items list.
     */
    if (mLoaded) {
      if(mAuction.getServer() != null) setServer((AuctionServerInterface)mAuction.getServer());
      Currency currentPrice = mAuction.getCurBid();
      if(currentPrice == null || currentPrice.isNull()) {
        currentPrice = mAuction.getBuyNow();
      }
      setDate("last_updated_at", new Date());
      setDefaultCurrency(currentPrice);
      updateHighBid();
      checkHighBidder();
      checkEnded();
    }
  }

  ///////////////
  //  Constructor

  /** Construct an AuctionEntry from just the ID, loading all necessary info
   * from the server.
   *
   * @param auctionIdentifier The auction ID, from which the entire
   *     AuctionEntry is built by loading data from the server.
   * @param server - The auction server for this entry.
   */
  private AuctionEntry(String auctionIdentifier, AuctionServerInterface server) {
    mServer = server;
    checkConfigurationSnipeTime();
    prepareAuctionEntry(auctionIdentifier);
  }

  /**
   * Create a new auction entry for the ID passed in.  If it is in the deleted list, or already exists in
   * the database, it will return null.
   *
   * @param identifier - The auction identifier to create an auction for.
   *
   * @return - null if the auction is in the deleted entry table, or the existing auction
   * entry table, otherwise returns a valid AuctionEntry for the auction identifier provided.
   */
  public static AuctionEntry construct(String identifier) {
    AuctionServerInterface server = sResolver.getServer();
    String strippedId = server.stripId(identifier);

    if (!DeletedEntry.exists(strippedId) && findByIdentifier(strippedId) == null) {
      AuctionEntry ae = new AuctionEntry(strippedId, server);
      if(ae.isLoaded()) {
        String id = ae.saveDB();
        if (id != null) {
          JConfig.increment("stats.auctions");
          return ae;
        }
      }
    }
    return null;
  }

  /**
   * A constructor that does almost nothing.  This is to be used
   * for loading from XML data later on, where the fromXML function
   * will fill out all the internal information.  Similarly, ActiveRecord
   * fills this out when pulling from a database record.
   *
   * Uses the default server.
   */
  public AuctionEntry() {
    mServer = sResolver.getServer();
    checkConfigurationSnipeTime();
  }

  /**
   * @brief Look up to see if the auction is ended yet, just sets
   * mComplete if it is.
   */
  private void checkEnded() {
    if(!isComplete()) {
      Date serverTime = new Date(System.currentTimeMillis() +
                                 getServer().getServerTimeDelta());

      //  If we're past the end time, update once, and never again.
      if(serverTime.after(getEndDate())) {
        setComplete(true);
        setNeedsUpdate();
      }
    }
  }

  /////////////
  //  Accessors

  /**
   * @brief Return the server associated with this entry.
   *
   * TODO mrs -- This needs to be set in the data record.
   *
   * @return The server that this auction entry is associated with.
   */
  public AuctionServerInterface getServer() {
    if(mServer == null) {
      mServer = sResolver.getServer();
    }
    return(mServer);
  }

  /**
   * @brief Set the auction server for this entry.
   *
   * First, if there are any snipes in the 'old' server, cancel them.
   * Then set the server to the passed in value.
   * Then re-set up any snipes associated with the listing.
   *
   * @param newServer - The server to associate with this auction entry.
   */
  public void setServer(AuctionServerInterface newServer) {
    if(newServer != mServer) {
      //  "CANCEL_SNIPE #{id}"
      if(isSniped()) getServer().cancelSnipe(getIdentifier());
      mServer = newServer;
      if(isSniped()) getServer().setSnipe(this);
    }
  }

  /**
   * @brief Query whether this entry has ever been loaded from the server.
   *
   * Really shouldn't be necessary, but is.  If we try to create an
   * AuctionEntry with a bad identifier, that doesn't match any
   * server, or isn't 'live' on the auction server, we need an error
   * of this sort, to identify that the load failed.  This is mainly
   * because constructors don't fail.
   *
   * @return Whether this entry has ever been loaded from the server.
   */
  public boolean isLoaded()    { return(mLoaded); }

  /**
   * @brief Check if the current snipe value would be a valid bid currently.
   *
   * @return true if the current snipe is at least one minimum bid
   * increment over the current high bid.  Returns false otherwise.
   */
  public boolean isSnipeValid() {
    if(getSnipe() == null) return false;

    Currency minIncrement = getServer().getMinimumBidIncrement(getCurBid(), getNumBidders());
    Currency nextBid = Currency.NoValue();
    boolean rval = false;

    try {
      nextBid = getCurBid().add(minIncrement);

      if(nextBid == null || getSnipe().getAmount().getValue() >= nextBid.getValue()) {
        rval = true;
      }
    } catch(Currency.CurrencyTypeException cte) {
      JConfig.log().handleException("This should never happen (" + nextBid + ", " + getSnipe().getAmount() + ")!", cte);
    }

    return rval;
  }

  /**
   * @brief Check if the user has an outstanding snipe on this auction.
   *
   * @return Whether there is a snipe waiting on this auction.
   */
  public boolean isSniped() {
    return getSnipe() != null;
  }

  /**
   * @brief Check if this auction is part of a snipe group.
   *
   * Multisnipes are snipes where each fires, and if one is successful
   * then it automatically cancels all the rest of the snipes.  This
   * lets users snipe on (say) five auctions, even though they only
   * want one of the items.
   *
   * @return Whether this auction is one of a multisnipe group, where
   * each auction is sniped on until one is won.
   */
  public boolean isMultiSniped()    { return(getMultiSnipe() != null); }

  /**
   * @brief Check if the user has ever placed a bid (or completed
   * snipe) on this auction.
   *
   * @return Whether the user has ever actually submitted a bid to the
   * server for this auction.
   */
  public boolean isBidOn() { return(getBid() != null && !getBid().isNull()); }

  /**
   * @brief Check if the current user is the high bidder on this
   * auction.
   *
   * This should eventually handle multiple users per server, so that
   * users can have multiple identities per auction site.
   *
   * @return Whether the current user is the high bidder.
   */
  public boolean isHighBidder() { return isWinning(); }

  public boolean isWinning() { return getBoolean("winning", false); }
  public void setWinning(boolean state) { setBoolean("winning", state); }

  /**
   * @brief Check if the current user is the seller for this auction.
   *
   * This should eventually handle multiple users per server, so that
   * users can have multiple identities per auction site.
   * FUTURE FEATURE -- mrs: 02-January-2003 01:25
   *
   * @return Whether the current user is the seller.
   */
  public boolean isSeller() { return getServer().isCurrentUser(getSeller()); }

  /**
   * @brief What was the highest amount actually submitted to the
   * server as a bid?
   *
   * With some auction servers, it might be possible to find out how
   * much the user bid, but in general presume this value is only set
   * by bidding through this program, or firing a snipe.
   *
   * @return The highest amount bid through this program.
   */
  public Currency getBid()  { return getMonetary("last_bid_amount"); }

  /**
   * @brief Set the highest amount actually submitted to the server as a bid.
   * What is the maximum amount the user bid on the last time they bid?
   *
   * @param highBid - The new high bid value to set for this auction.
   */
  public void setBid(Currency highBid)  {
    setMonetary("last_bid_amount", highBid == null? Currency.NoValue() : highBid);
    saveDB();
  }

  public void setBidQuantity(int quant) {
    setInteger("last_bid_quantity", quant);
    saveDB();
  }

  public int getWatchers() { Integer cnt = getInteger("watcher_count"); return (cnt == null) ? -1 : cnt; }

  public void setWatchers(int watcherCount) {
    setInteger("watcher_count", watcherCount);
    saveDB();
  }

  /**
   * @brief What was the most recent number of items actually
   * submitted to the server as part of a bid?
   * How many items were bid on the last time the user bid?
   *
   * @return The count of items bid on the last time a user bid.
   */
  public int getBidQuantity() {
    if(isBidOn()) {
      Integer i = getInteger("last_bid_quantity");
      return i != null ? i : 1;
    }
    return 0;
  }

  /**
   * @brief Set this auction as being part of a multi-snipe set,
   * change the multi-snipe group associated with it, or delete it
   * from it's current multi-snipe set.
   *
   * TODO -- Extract this out, create a SnipeInterface which would be
   * implemented by AuctionEntry.  Multisnipe then operates on
   * SnipeInterface objects, so we don't have the X calls Y, Y calls
   * X interrelationship.
   *
   * @param inMS - The multisnipe to set or change.  If it's 'null',
   * it clears the multisnipe for this entry.
   */
  public void setMultiSnipe(MultiSnipe inMS) {
    //  Shortcut: if no change, leave.
    if(mMultiSnipe != inMS) {
      //  If there was a different MultiSnipe before, remove this from it.
      if(mMultiSnipe != null) {
        mMultiSnipe.remove(getIdentifier());
        //  ...and cancel the current snipe, as long as we're not
        // cancelling this snipe entirely (in which case we cancel
        // it below).
        if(inMS != null) {
          prepareSnipe(Currency.NoValue(), 0);
        } else {
          setInteger("multisnipe_id", null);
        }
      }
      mMultiSnipe = inMS;
      //  If we weren't just deleting, then prepare the new snipe, and
      //  add to the multi-snipe group.
      if(mMultiSnipe != null) {
        if(!isSniped()) {
          prepareSnipe(mMultiSnipe.getSnipeValue(getShippingWithInsurance()));
        }
        mMultiSnipe.add(getIdentifier());
        addMulti(mMultiSnipe);
      }
    }

    if(inMS == null) {
      //  If the multisnipe was null, remove the snipe entirely.
      prepareSnipe(Currency.NoValue(), 0);
      setInteger("multisnipe_id", null);
    } else {
      setInteger("multisnipe_id", inMS.getId());
    }
    saveDB();
  }

  /**
   * @brief Get the default snipe time as configured.
   *
   * @return - The default snipe time from the configuration.  If it's
   * not set, return a standard 30 seconds.
   */
  private static long getGlobalSnipeTime() {
    long snipeTime;

    String strConfigSnipeAt = JConfig.queryConfiguration("snipemilliseconds");
    if(strConfigSnipeAt != null) {
      snipeTime = Long.parseLong(strConfigSnipeAt);
    } else {
      snipeTime = Constants.THIRTY_SECONDS;
    }

    return snipeTime;
  }

  /**
   * @brief Get the multi-snipe object associated with this auction, if it's set as a multi-snipe.
   *
   * @return - A multisnipe object or null if there isn't any multisnipe set.
   */
  public MultiSnipe getMultiSnipe() {
    if(mMultiSnipe != null) return mMultiSnipe;

    Integer id = getInteger("multisnipe_id");
    if(id == null) return null;

    mMultiSnipe = MultiSnipe.find(id);
    return mMultiSnipe;
  }

  /**
   * @brief Check if the configuration has a 'snipemilliseconds'
   * entry, and update the default if it does.
   */
  private static void checkConfigurationSnipeTime() {
    sDefaultSnipeAt = getGlobalSnipeTime();
  }

  /**
   * @brief Set how long before auctions are complete to fire snipes
   * for any auction using the default snipe timer.
   *
   * @param newSnipeAt - The number of milliseconds prior to the end
   * of auctions that the snipe timer will fire.  Can be overridden by
   * setSnipeTime() on a per-auction basis.
   */
  public static void setDefaultSnipeTime(long newSnipeAt) {
    sDefaultSnipeAt = newSnipeAt;
  }

  public long getSnipeTime() {
    return hasDefaultSnipeTime()? sDefaultSnipeAt : mSnipeAt;
  }

  public boolean hasDefaultSnipeTime() {
    return(mSnipeAt == -1);
  }

  public void setSnipeTime(long newSnipeTime) {
    mSnipeAt = newSnipeTime;
  }

  /**
   * @brief Get the time when this entry will no longer be considered
   * 'newly added', or null if it's been cleared, or is already past.
   *
   * @return The time at which this entry is no longer new.
   */
  public boolean isJustAdded() {
    Date d = getDate("created_at");
    return (d != null) && (d.getTime() > (System.currentTimeMillis() - (Constants.ONE_MINUTE * 5)));
  }

  public String getIdentifier() {
    return getAuction() == null ? null : getAuction().getIdentifier();
  }

  ///////////////////////////
  //  Actual logic functions

  public void updateHighBid() {
    int numBidders = getNumBidders();

    if (numBidders > 0 || isFixed()) {
      getServer().updateHighBid(this);
    }
  }

  /**
   * @brief On update, we check if we're the high bidder.
   *
   * When you change user ID's, you should force a complete update, so
   * this is synchronized correctly.
   */
  private void checkHighBidder() {
    int numBidders = getNumBidders();

    if(numBidders > 0) {
      if(isBidOn() && isPrivate()) {
        Currency curBid = getCurBid();
        try {
          if(curBid.less(getBid())) setWinning(true);
        } catch(Currency.CurrencyTypeException cte) {
          /* Should never happen...?  */
          JConfig.log().handleException("This should never happen (bad Currency at this point!).", cte);
        }
        if(curBid.equals(getBid())) {
          setWinning(numBidders == 1);
          //  winning == false means that there are multiple bidders, and the price that
          //  two (this user, and one other) bid are exactly the same.  How
          //  do we know who's first, given that it's a private auction?
          //
          //  The only answer I have is to presume that we're NOT first.
          //  eBay knows the 'true' answer, but how to extract it from them...
        }
      } else {
        setWinning(getServer().isCurrentUser(getHighBidder()));
      }
    }
  }

  ////////////////////////////
  //  Periodic logic functions

  /**
   * @brief Mark this entry as being not-invalid.
   */
  public void clearInvalid() {
    setBoolean("invalid", false);
    saveDB();
  }

  /**
   * @brief Mark this entry as being invalid for some reason.
   */
  public void setInvalid() {
    setBoolean("invalid", true);
    saveDB();
  }

  /**
   * @brief Is this entry invalid for any reason?
   *
   * Is the data reasonably synchronized with the server?  (When the
   * site stops providing the data, or an error occurs when retrieving
   * this auction, this will be true.)
   *
   * @return - True if this auction is considered invalid, false if it's okay.
   */
  public boolean isInvalid() {
    return getBoolean("invalid", false);
  }

  /**
   * @brief Store a user-specified comment about this item.
   * Allow the user to add a personal comment about this auction.
   *
   * @param newComment - The comment to keep track of.  If it's empty,
   * we effectively delete the comment.
   */
  public void setComment(String newComment) {
    if(newComment.trim().length() == 0)
      setString("comment", null);
    else
      setString("comment", newComment.trim());
    saveDB();
  }

  /**
   * @brief Get any user-specified comment regarding this auction.
   *
   * @return Any comment the user may have stored about this item.
   */
  public String getComment() {
    return getString("comment");
  }

  /**
   * @brief Add an auction-specific status message into its own event log.
   *
   * @param inStatus - A string that explains what the event is.
   */
  public void setLastStatus(String inStatus) {
    getEvents().setLastStatus(inStatus);
  }

  public void setShipping(Currency newShipping) {
    setMonetary("shipping", newShipping);
    saveDB();
  }

  /**
   * @brief Get a plain version of the event list, where each line is
   * a seperate event, including the title and identifier.
   *
   * @return A string with all the event information included.
   */
  public String getLastStatus() { return getEvents().getLastStatus(); }

  /**
   * @brief Get either a plain version of the events, or a complex
   * (bulk) version which doesn't include the title and identifier,
   * since those are set by the AuctionEntry itself, and are based
   * on its own data.
   *
   * @return A string with all the event information included.
   */
  public String getStatusHistory() {
    return getEvents().getAllStatuses();
  }

  public int getStatusCount() {
    return getEvents().getStatusCount();
  }

  private EventLogger getEvents() {
    if(mEntryEvents == null) mEntryEvents = new EventLogger(getIdentifier(), getId(), getTitle());
    return mEntryEvents;
  }

  //////////////////////////
  //  XML Handling functions

  protected String[] infoTags = { "info", "bid", "snipe", "complete", "invalid", "comment", "log", "multisnipe", "shipping", "category", "winning" };
  protected String[] getTags() { return infoTags; }

  /**
   * @brief XML load-handling.  It would be really nice to be able to
   * abstract this for all the classes that serialize to XML.
   *
   * @param tagId - The index into 'entryTags' for the current tag.
   * @param curElement - The current XML element that we're loading from.
   */
  protected void handleTag(int tagId, XMLElement curElement) {
    switch(tagId) {
      case 0:  //  Get the general auction information
        //  TODO -- What if it's already in the database?
        mAuction.fromXML(curElement);
        mAuction.saveDB();
        break;
      case 1:  //  Get bid info
        Currency bidAmount = Currency.getCurrency(curElement.getProperty("CURRENCY"),
                                          curElement.getProperty("PRICE"));
        setBid(bidAmount);
        setBidQuantity(Integer.parseInt(curElement.getProperty("QUANTITY")));
        if(curElement.getProperty("WHEN", null) != null) {
          mBidAt = Long.parseLong(curElement.getProperty("WHEN"));
        }
        break;
      case 2:  //  Get the snipe info together
        Currency snipeAmount = Currency.getCurrency(curElement.getProperty("CURRENCY"),
                                            curElement.getProperty("PRICE"));
        prepareSnipe(snipeAmount, Integer.parseInt(curElement.getProperty("QUANTITY")));
        mSnipeAt = Long.parseLong(curElement.getProperty("SECONDSPRIOR"));
        break;
      case 3:
        setComplete(true);
        break;
      case 4:
        setInvalid();
        break;
      case 5:
        setComment(curElement.getContents());
        break;
      case 6:
        mEntryEvents = new EventLogger(getIdentifier(), getId(), getTitle());
        mEntryEvents.fromXML(curElement);
        break;
      case 7:
        setMultiSnipe(MultiSnipe.loadFromXML(curElement));
        break;
      case 8:
        Currency shipping = Currency.getCurrency(curElement.getProperty("CURRENCY"),
                                         curElement.getProperty("PRICE"));
        setShipping(shipping);
        break;
      case 9:
        setCategory(curElement.getContents());
        setSticky(curElement.getProperty("STICKY", "false").equals("true"));
        break;
      case 10:
        setWinning(true);
        break;
      default:
        break;
        // commented out for FORWARDS compatibility.
        //        throw new RuntimeException("Unexpected value when handling AuctionEntry tags!");
    }
  }

  public XMLElement toXML() {
    return toXML(true);
  }

  /**
   * @brief Check everything and build an XML element that contains as
   * children all of the values that need storing for this item.
   *
   * This would be so much more useful if it were 'standard'.
   *
   * @return An XMLElement containing as children, all of the key
   * values associated with this auction entry.
   */
  public XMLElement toXML(boolean includeEvents) {
    XMLElement xmlResult = new XMLElement("auction");

    xmlResult.setProperty("id", getIdentifier());
    xmlResult.addChild(getAuction().toXML());

    if(isBidOn()) {
      XMLElement xbid = new XMLElement("bid");
      xbid.setEmpty();
      xbid.setProperty("quantity", Integer.toString(getBidQuantity()));
      xbid.setProperty("currency", getBid().fullCurrencyName());
      xbid.setProperty("price", Double.toString(getBid().getValue()));
      if(mBidAt != 0) {
        xbid.setProperty("when", Long.toString(mBidAt));
      }
      xmlResult.addChild(xbid);
    }

    if(isSniped()) {
      XMLElement xsnipe = new XMLElement("snipe");
      xsnipe.setEmpty();
      xsnipe.setProperty("quantity", Integer.toString(getSnipe().getQuantity()));
      xsnipe.setProperty("currency", getSnipe().getAmount().fullCurrencyName());
      xsnipe.setProperty("price", Double.toString(getSnipe().getAmount().getValue()));
      xsnipe.setProperty("secondsprior", Long.toString(mSnipeAt));
      xmlResult.addChild(xsnipe);
    }

    if(isMultiSniped()) xmlResult.addChild(getMultiSnipe().toXML());

    if(isComplete()) addStatusXML(xmlResult, "complete");
    if(isInvalid()) addStatusXML(xmlResult, "invalid");
    if(isDeleted()) addStatusXML(xmlResult, "deleted");
    if(isWinning()) addStatusXML(xmlResult, "winning");

    if(getComment() != null) {
      XMLElement xcomment = new XMLElement("comment");
      xcomment.setContents(getComment());
      xmlResult.addChild(xcomment);
    }

    if(getCategory() != null) {
      XMLElement xcategory = new XMLElement("category");
      xcategory.setContents(getCategory());
      xcategory.setProperty("sticky", isSticky() ?"true":"false");
      xmlResult.addChild(xcategory);
    }

    if(getShipping() != null) {
      XMLElement xshipping = new XMLElement("shipping");
      xshipping.setEmpty();
      xshipping.setProperty("currency", getShipping().fullCurrencyName());
      xshipping.setProperty("price", Double.toString(getShipping().getValue()));
      xmlResult.addChild(xshipping);
    }

    if(includeEvents && mEntryEvents != null) {
      XMLElement xlog = mEntryEvents.toXML();
      if (xlog != null) {
        xmlResult.addChild(xlog);
      }
    }
    return xmlResult;
  }

  /**
   * @brief Load auction entries from an XML element.
   *
   * @param inXML - The XMLElement that contains the items to load.
   */
  public void fromXML(XMLInterface inXML) {
    String inID = inXML.getProperty("ID", null);
    if(inID != null) {
      mAuction = new AuctionInfo();
      mAuction.setIdentifier(inID);

      super.fromXML(inXML);

      mLoaded = false;

      if(!isComplete()) setNeedsUpdate();

      saveDB();
      if(mEntryEvents == null) {
        getEvents();
      }
      checkHighBidder();
      saveDB();
    }
  }

  ////////////////////////////////
  //  Multisnipe utility functions

  private static Map<Long, MultiSnipe> allMultiSnipes = new TreeMap<Long, MultiSnipe>();

  /**
   * @brief Add a new multisnipe to the AuctionEntry class's list of
   * multisnipes.
   *
   * This keeps track of ALL multisnipes, so that they can be
   * loaded/saved okay, as well as checked to remove.
   *
   * @param newMS - The newly created multisnipe to add.
   */
  private static void addMulti(MultiSnipe newMS) {
    long newId = newMS.getIdentifier();

    if(!allMultiSnipes.containsKey(newId)) {
      allMultiSnipes.put(newId, newMS);
    }
  }

  /////////////////////
  //  Sniping functions

  /**
   * @brief Return whether this entry ever had a snipe cancelled or not.
   *
   * @return - true if a snipe was cancelled, false otherwise.
   */
  public boolean snipeCancelled() { return mCancelSnipeBid != null; }

  /**
   * @brief Return the amount that the snipe bid was for, before it
   * was cancelled.
   *
   * @return - A currency amount that was set to snipe, but cancelled.
   */
  public Currency getCancelledSnipe() { return mCancelSnipeBid; }

  /**
   * Cancel the snipe and clear the multisnipe setting.  This is used for
   * user-driven snipe cancellations, and errors like the listing going away.
   *
   * @param after_end - Is this auction already completed?
   */
  public void cancelSnipe(boolean after_end) {
    handleCancel(after_end);

    setMultiSnipe(null);
  }

  private void handleCancel(boolean after_end) {
    if(isSniped()) {
      JConfig.log().logDebug("Cancelling Snipe for: " + getTitle() + '(' + getIdentifier() + ')');
      setLastStatus("Cancelling snipe.");
      if(after_end) {
        mCancelSnipeBid = getSnipe().getAmount();
      }
    }
  }

  public void snipeCompleted() {
    setBid(getSnipe().getAmount());
    setBidQuantity(getSnipe().getQuantity());
    getSnipe().delete();
    setInteger("snipe_id", null);
    mSnipe = null;
    setDirty();
    setNeedsUpdate();
    saveDB();
  }

  /**
   * In this case, the snipe failed, and we want to cancel the snipe, but we
   * don't want to remove the listing from the multisnipe group, in case you
   * still win it.  (For example, if you have a bid on it already.)
   */
  public void snipeFailed() {
    handleCancel(true);
    setDirty();
    setNeedsUpdate();
    saveDB();
  }

  /**
   * @brief Completely update auction info from the server for this auction.
   */
  public void update() {
    setDate("last_updated_at", new Date());

    // We REALLY don't want to leave an auction in the 'updating'
    // state.  It does bad things.
    try {
      getServer().reload(this);
    } catch(Exception e) {
      JConfig.log().handleException("Unexpected exception during auction reload/update.", e);
    }
    try {
//      getServer().updateWatchers(this);
      updateHighBid();
      checkHighBidder();
    } catch(Exception e) {
      JConfig.log().handleException("Unexpected exception during high bidder check.", e);
    }
    //  TODO Move all this to 'setComplete' on 'true'...
    if (isComplete()) {
      onComplete();
    } else {
      long now = System.currentTimeMillis() + getServer().getServerTimeDelta();
      Date serverTime = new Date(now);

      if(now > getEndDate().getTime())
      //  If we're past the end time, update once, and never again.
      if (serverTime.after(getEndDate())) {
        setComplete(true);
        setNeedsUpdate();
      }
    }
    saveDB();
  }

  private void onComplete() {//  If the auction is really completed now, and it was part of a
    //  multisnipe group, let's check if it's been won.  If it has,
    //  tell the MultiSnipe object that one has been won, so it can
    //  clear out the others!
    boolean won = isHighBidder() && (!isReserve() || isReserveMet());
    if (isMultiSniped()) {
      MultiSnipe ms = getMultiSnipe();
      if (won) {
        ms.setWonAuction(/* this */);
      } else {
        ms.remove(getIdentifier());
      }
    }
    if(won) {
      JConfig.increment("stats.won");
    }
    if (isSniped()) {
      //  It's okay to cancel the snipe here; if the auction was won, it would be caught above.
      setLastStatus("Cancelling snipe, auction is reported as ended.");
      cancelSnipe(true);
    }
  }

  public void prepareSnipe(Currency snipe) { prepareSnipe(snipe, 1); }

  /**
   * @brief Set up the fields necessary for a future snipe.
   *
   * This needs to be enhanced to work with multiple items, and
   * different snipe times.
   *
   * @param snipe The amount of money the user wishes to bid at the last moment.
   * @param quantity The number of items they want to snipe for.
   */
  public void prepareSnipe(Currency snipe, int quantity) {
    if(snipe == null || snipe.isNull()) {
      if(getSnipe() != null) {
        getSnipe().delete();
      }
      setInteger("snipe_id", null);
      mSnipe = null;
      getServer().cancelSnipe(getIdentifier());
    } else {
      mSnipe = AuctionSnipe.create(snipe, quantity, 0);
      getServer().setSnipe(this);
    }
    setDirty();
    saveDB();
    MQFactory.getConcrete("Swing").enqueue("SNIPECHANGED");
  }

  /**
   * @brief Refresh the snipe, so it picks up a potentially changed end time, or when initially loading items.
   */
  public void refreshSnipe() {
    getServer().setSnipe(this);
  }

  /**
   * @brief Bid a given price on an arbitrary number of a particular item.
   *
   * @param bid - The amount of money being bid.
   * @param bidQuantity - The number of items being bid on.
   *
   * @return The result of the bid attempt.
   */
  public int bid(Currency bid, int bidQuantity) {
    setBid(bid);
    setBidQuantity(bidQuantity);
    mBidAt = System.currentTimeMillis();

    JConfig.log().logDebug("Bidding " + bid + " on " + bidQuantity + " item[s] of (" + getIdentifier() + ")-" + getTitle());

    int rval = getServer().bid(this, bid, bidQuantity);
    saveDB();
    return rval;
  }

  /**
   * @brief Buy an item directly.
   *
   * @param quant - The number of them to buy.
   *
   * @return The result of the 'Buy' attempt.
   */
  public int buy(int quant) {
    int rval = AuctionServerInterface.BID_ERROR_NOT_BIN;
    Currency bin = getBuyNow();
    if(bin != null && !bin.isNull()) {
      setBid(getBuyNow());
      setBidQuantity(quant);
      mBidAt = System.currentTimeMillis();
      JConfig.log().logDebug("Buying " + quant + " item[s] of (" + getIdentifier() + ")-" + getTitle());
      rval = getServer().buy(this, quant);
      saveDB();
    }
    return rval;
  }

  /**
   * @brief This auction entry needs to be updated.
   */
  public void setNeedsUpdate() { setDate("last_updated_at", null); saveDB(); }

  public Date getLastUpdated() { return getDate("last_updated_at"); }

  /**
   * @brief Get the category this belongs in, usually used for tab names, and fitting in search results.
   *
   * @return - A category, or null if none has been assigned.
   */
  public String getCategory() {
    if(mCategory == null) {
      String category_id = get("category_id");
      if(category_id != null) {
        mCategory = Category.findFirstBy("id", category_id);
      }
    }
    if(mCategory == null) {
      setCategory(!isComplete() ? (isSeller() ? "selling" : "current") : "complete");
    }

    return mCategory != null ? mCategory.getName() : null;
  }

  /**
   * @brief Set the category associated with the auction entry.  If the
   * auction is ended, this is automatically considered sticky.
   *
   * @param newCategory - The new category to associate this item with.
   */
  public void setCategory(String newCategory) {
    Category c = Category.findFirstByName(newCategory);
    if(c == null) {
      c = Category.findOrCreateByName(newCategory);
    }
    setInteger("category_id", c.getId());
    mCategory = c;
    if(isComplete()) setSticky(true);
    saveDB();
  }

  /**
   * @brief Returns whether or not this auction entry is 'sticky', i.e. sticks to any category it's set to.
   * Whether the 'category' information is sticky (i.e. overrides 'deleted', 'selling', etc.)
   *
   * @return true if the entry is sticky, false otherwise.
   */
  public boolean isSticky() { return getBoolean("sticky"); }

  /**
   * @brief Set the sticky flag on or off.
   *
   * This'll probably be exposed to the user through a right-click context menu, so that people
   * can make auctions not move from their sorted categories when they end.
   *
   * @param beSticky - Whether or not this entry should be sticky.
   */
  public void setSticky(boolean beSticky) { setBoolean("sticky", beSticky); saveDB(); }

  // TODO mrs -- Move this to a TimeLeftBuilder class.
  public static final String endedAuction = "Auction ended.";
  private static final String mf_min_sec = "{6}{2,number,##}m, {7}{3,number,##}s";
  private static final String mf_hrs_min = "{5}{1,number,##}h, {6}{2,number,##}m";
  private static final String mf_day_hrs = "{4}{0,number,##}d, {5}{1,number,##}h";

  private static final String mf_min_sec_detailed = "{6}{2,number,##} minute{2,choice,0#, |1#, |1<s,} {7}{3,number,##} second{3,choice,0#|1#|1<s}";
  private static final String mf_hrs_min_detailed = "{5}{1,number,##} hour{1,choice,0#, |1#, |1<s,} {6}{2,number,##} minute{2,choice,0#|1#|1<s}";
  private static final String mf_day_hrs_detailed = "{4}{0,number,##} day{0,choice,0#, |1#, |1<s,}  {5}{1,number,##} hour{1,choice,0#|1#|1<s}";

  //0,choice,0#are no files|1#is one file|1<are {0,number,integer} files}

  private static String convertToMsgFormat(String simpleFormat) {
    String msgFmt = simpleFormat.replaceAll("DD", "{4}{0,number,##}");
    msgFmt = msgFmt.replaceAll("HH", "{5}{1,number,##}");
    msgFmt = msgFmt.replaceAll("MM", "{6}{2,number,##}");
    msgFmt = msgFmt.replaceAll("SS", "{7}{3,number,##}");

    return msgFmt;
  }

  /**
   * @brief Determine the amount of time left, and format it prettily.
   *
   * @return A nicely formatted string showing how much time is left
   * in this auction.
   */
  public String getTimeLeft() {
    long rightNow = System.currentTimeMillis();
    long officialDelta = getServer().getServerTimeDelta();
    long pageReqTime = getServer().getPageRequestTime();

    if(!isComplete()) {
      long dateDiff;
      try {
        dateDiff = getEndDate().getTime() - ((rightNow + officialDelta) - pageReqTime);
      } catch(Exception endDateException) {
        JConfig.log().handleException("Error getting the end date.", endDateException);
        dateDiff = 0;
      }

      if(dateDiff > Constants.ONE_DAY * 60) return "N/A";

      if(dateDiff >= 0) {
        long days = dateDiff / (Constants.ONE_DAY);
        dateDiff -= days * (Constants.ONE_DAY);
        long hours = dateDiff / (Constants.ONE_HOUR);
        dateDiff -= hours * (Constants.ONE_HOUR);
        long minutes = dateDiff / (Constants.ONE_MINUTE);
        dateDiff -= minutes * (Constants.ONE_MINUTE);
        long seconds = dateDiff / Constants.ONE_SECOND;

        String mf = getTimeFormatter(days, hours);

        Object[] timeArgs = { days,           hours,      minutes,     seconds,
                              pad(days), pad(hours), pad(minutes), pad(seconds) };

        return(MessageFormat.format(mf, timeArgs));
      }
    }
    return endedAuction;
  }

  private static String getTimeFormatter(long days, long hours) {
    String mf;
    boolean use_detailed = JConfig.queryConfiguration("timeleft.detailed", "false").equals("true");
    String cfg;
    if(days == 0) {
      if(hours == 0) {
        mf = use_detailed?mf_min_sec_detailed:mf_min_sec;
        cfg = JConfig.queryConfiguration("timeleft.minutes");
        if(cfg != null) mf = convertToMsgFormat(cfg);
      } else {
        mf = use_detailed?mf_hrs_min_detailed:mf_hrs_min;
        cfg = JConfig.queryConfiguration("timeleft.hours");
        if (cfg != null) mf = convertToMsgFormat(cfg);
      }
    } else {
      mf = use_detailed?mf_day_hrs_detailed:mf_day_hrs;
      cfg = JConfig.queryConfiguration("timeleft.days");
      if (cfg != null) mf = convertToMsgFormat(cfg);
    }
    return mf;
  }

  private static String pad(long x) {
    return (x < 10) ? " " : "";
  }

  /**
   * @brief Do a 'standard' compare to another AuctionEntry object.
   *
   * The standard ordering is as follows:
   *    (if identifiers or pointers are equal, entries are equal)
   *    If this end date is after the passed in one, we are greater.
   *    If this end date is before, we are lesser.
   *    Otherwise (EXACTLY equal dates!), order by identifier.
   *
   * @param other - The AuctionEntry to compare to.
   *
   * @return - -1 for lesser, 0 for equal, 1 for greater.
   */
  public int compareTo(AuctionEntry other) {
    //  We are always greater than null
    if(other == null) return 1;
    //  We are always equal to ourselves
    if(other == this) return 0;

    String identifier = getIdentifier();

    //  If the identifiers are the same, we're equal.
    if(identifier != null && identifier.equals(other.getIdentifier())) return 0;

    if(getEndDate() == null && other.getEndDate() != null) return 1;
    if(getEndDate() != null && other.getEndDate() == null) return -1;
    if (getEndDate() != null && other.getEndDate() != null) {
      //  If this ends later than the passed in object, then we are 'greater'.
      if(getEndDate().after(other.getEndDate())) return 1;
      if(other.getEndDate().after(getEndDate())) return -1;
    }

    //  Whoops!  Dates are equal, down to the second probably, or both null...

    //  If this has a null identifier, we're lower.
    if(identifier == null && other.getIdentifier() != null) return -1;
    if(identifier == null && other.getIdentifier() == null) return 0;
    //  At this point, we know identifier != null, so if the compared entry
    //  has a null identifier, we sort higher.
    if(other.getIdentifier() == null) return 1;

    //  Since this ends exactly at the same time as another auction,
    //  check the identifiers (which *must* be different here.
    return getIdentifier().compareTo(other.getIdentifier());
  }

  /**
   * @brief Return a value that indicates the status via bitflags, so that sorted groups by status will show up grouped together.
   *
   * @return - An integer containing a bitfield of relevant status bits.
   */
  public int getFlags() {
    int r_flags = 1;

    if (isFixed()) r_flags = 0;
    if (getHighBidder() != null) {
      if (isHighBidder()) {
        r_flags = 2;
      } else if (isSeller() && getNumBidders() > 0 &&
                 (!isReserve() || isReserveMet())) {
        r_flags = 4;
      }
    }
    if (!getBuyNow().isNull()) {
      r_flags += 8;
    }
    if (isReserve()) {
      if (isReserveMet()) {
        r_flags += 16;
      } else {
        r_flags += 32;
      }
    }
    if(hasPaypal()) r_flags += 64;
    return r_flags;
  }

  private static AuctionInfo sAuction = new AuctionInfo();

  public AuctionInfo getAuction() {
    if(mAuction == null || mAuction == sAuction) {
      String aid = get("auction_id");
      if(aid != null && aid.length() != 0) {
        mAuction = AuctionInfo.findFirstBy("id", aid);
      }
      if((mAuction == null || mAuction == sAuction) && getString("identifier") != null) {
        mAuction = AuctionInfo.findByIdentifier(getString("identifier"));
      }

      //  If we successfully loaded an auction info object...
      if(mAuction != null) {
        setDefaultCurrency(mAuction.getDefaultCurrency());

        if(getString("identifier") == null) {
          setString("identifier", mAuction.getIdentifier());
          setInteger("auction_id", mAuction.getId());
          saveDB();
        }
      }
    }

    if(mAuction == null) mAuction = sAuction;

    return mAuction;
  }

  /**
   * @brief Force this auction to use a particular set of auction
   * information for it's core data (like seller's name, current high
   * bid, etc.).
   *
   * @param inAI - The AuctionInfo object to make the new core data.  Must not be null.
   */
  public void setAuctionInfo(AuctionInfo inAI) {
    if(mAuction == null || mAuction == sAuction) {
      setDefaultCurrency(inAI.getDefaultCurrency());
    }

    //  If the end date has changed, let's reschedule the snipes for the new end date...?
    boolean doRefresh = (mAuction != null && mAuction.getEndDate() != null &&
        !mAuction.getEndDate().equals(inAI.getEndDate()) && getSnipe() != null);

    AuctionInfo oldAuction = mAuction;
    mAuction = inAI;
    String newAuctionId = mAuction.saveDB();
    if(doRefresh) refreshSnipe();
    if(newAuctionId != null) {
      set("auction_id", newAuctionId);
      setString("identifier", mAuction.getIdentifier());
      //  If we had an old auction, and it's not the same as the new one,
      //  and the IDs are different, delete the old one.
      if (oldAuction != null &&
          oldAuction != mAuction &&
          mAuction.getId() != null &&
          oldAuction.getId() != null &&
          !mAuction.getId().equals(oldAuction.getId())) {
        oldAuction.delete();
      }
    }

    checkHighBidder();
    checkEnded();
    saveDB();
  }

  ////////////////////////////////////////
  //  Passthrough functions to AuctionInfo

  /* Accessor functions that are passed through directly down
   * to the internal AuctionInfo object.
   */
  public Currency getCurBid() { return getAuction().getCurBid(); }
  public Currency getUSCurBid() { return getAuction().getUSCurBid(); }
  public Currency getMinBid() { return getAuction().getMinBid(); }

  /**
   * Check current price, and fall back to buy-now price if 'current' isn't set.
   *
   * @return - The current price, or the buy now if current isn't set.
   */
  public Currency getCurrentPrice() {
    Currency curPrice = getCurBid();
    if (curPrice == null || curPrice.isNull()) return getBuyNow();
    return curPrice;
  }

  public Currency getCurrentUSPrice() {
    Currency curPrice = getCurBid();
    if (curPrice == null || curPrice.isNull()) return getAuction().getBuyNowUS();
    return getUSCurBid();
  }

  /**
   * @return - Shipping amount, overrides AuctionInfo shipping amount if present.
   */
  public Currency getShipping() {
    if(!getMonetary("shipping").isNull()) return getMonetary("shipping");
    return getAuction().getShipping();
  }
  public Currency getInsurance() { return getAuction().getInsurance(); }
  public boolean getInsuranceOptional() { return getAuction().isInsuranceOptional(); }
  public Currency getBuyNow() { return getAuction().getBuyNow(); }

  public int getQuantity() { return getAuction().getQuantity(); }
  public int getNumBidders() { return getAuction().getNumBidders(); }

  public String getSeller() { return getAuction().getSellerName(); }
  public String getHighBidder() { return getAuction().getHighBidder(); }
  public String getTitle() { return getAuction().getTitle(); }

  public Date getStartDate() {
    if (getAuction() != null && getAuction().getStartDate() != null) {
      Date start = getAuction().getStartDate();
      if(start != null) return start;
    }

    return Constants.LONG_AGO;
  }

  public Date getEndDate() {
    if(getAuction() != null && getAuction().getEndDate() != null) {
      return getAuction().getEndDate();
    }

    return Constants.FAR_FUTURE;
  }
  public Date getSnipeDate() { return new Date(getAuction().getEndDate().getTime() - getSnipeTime()); }

  public boolean isDutch() { return getAuction().isDutch(); }
  public boolean isReserve() { return getAuction().isReserve(); }
  public boolean isReserveMet() { return getAuction().isReserveMet(); }
  public boolean isPrivate() { return getAuction().isPrivate(); }
  public boolean isFixed() { return getAuction().isFixedPrice(); }

  public StringBuffer getContent() { return getAuction().getContent(); }
  public File getContentFile() { return getAuction().getContentFile(); }
  public String getThumbnail() { return getAuction().getThumbnail(); }
  public String getBrowseableURL() { return getServer().getBrowsableURLFromItem(getIdentifier()); }

  public boolean hasPaypal() { return getAuction().hasPaypal(); }
  public String getItemLocation() { return getAuction().getItemLocation(); }
  public String getPositiveFeedbackPercentage() { return getAuction().getPositiveFeedbackPercentage(); }
  public int getFeedbackScore() { return getAuction().getFeedbackScore(); }

  public void setErrorPage(StringBuffer page) { mLastErrorPage = page; }
  public StringBuffer getErrorPage() { return mLastErrorPage; }

  public Currency getShippingWithInsurance() {
    Currency ship = getShipping();
    if(ship == null || ship.isNull())
      return Currency.NoValue();
    else {
      if(getInsurance() != null &&
         !getInsurance().isNull() &&
         !getInsuranceOptional()) {
        try {
          ship = ship.add(getInsurance());
        } catch(Currency.CurrencyTypeException cte) {
          JConfig.log().handleException("Insurance is somehow a different type than shipping?!?", cte);
        }
      }
    }
    return ship;
  }

  public boolean isShippingOverridden() {
    Currency ship = getMonetary("shipping");
    return ship != null && !ship.isNull();
  }

  /**
   * Is the auction deleted on the server?
   *
   * @return - true if the auction has been removed from the server, as opposed to deleted locally.
   */
  public boolean isDeleted() {
    return getBoolean("deleted", false);
  }

  /**
   * Mark the auction as having been deleted by the auction server.
   *
   * Generally items are removed by the auction server because the listing is
   * too old, violates some terms of service, the seller has been suspended,
   * or the seller removed the listing themselves.
   */
  public void setDeleted() {
    if(!isDeleted()) {
      setBoolean("deleted", true);
      clearInvalid();
    } else {
      setComplete(true);
    }
    saveDB();
  }

  /**
   * Mark the auction as NOT having been deleted by the auction server.
   *
   * It's possible we mistakenly saw a server-error as a 404 (or they
   * presented it as such), so we need to be able to clear the deleted status.
   */
  public void clearDeleted() {
    if(isDeleted()) {
      setBoolean("deleted", false);
      saveDB();
    }
  }

  /**
   * @return - Has this auction already ended?  We keep track of this, so we
   * don't waste time on it afterwards, even as much as creating a
   * Date object, and comparing.
   */
  public boolean isComplete() { return getBoolean("ended"); }
  public void setComplete(boolean complete) { setBoolean("ended", complete); saveDB(); }

  /*************************/
  /* Database access stuff */
  /*************************/

  public String saveDB() {
    if(mAuction == null || mAuction == sAuction) return null;

    String auctionId = mAuction.saveDB();
    if(auctionId != null) set("auction_id", auctionId);

    //  This just makes sure we have a default category before saving.
    getCategory();
    if(mCategory != null) {
      String categoryId = mCategory.saveDB();
      if(categoryId != null) set("category_id", categoryId);
    }

    if(getSnipe() != null) {
      String snipeId = getSnipe().saveDB();
      if(snipeId != null) set("snipe_id", snipeId);
    }

    if(mEntryEvents != null) {
      mEntryEvents.save();
    }

    String id = super.saveDB();
    set("id", id);
    return id;
  }

  public boolean reload() {
    try {
      AuctionEntry ae = AuctionEntry.findFirstBy("id", get("id"));
      if (ae != null) {
        setBacking(ae.getBacking());
        mAuction = ae.getAuction();
        ae.getCategory();
        mCategory = ae.mCategory;
        mSnipe = ae.getSnipe();
        mEntryEvents = ae.getEvents();
        mMultiSnipe = ae.getMultiSnipe();
        return true;
      }
    } catch (Exception e) {
      //  Ignored - the reload semi-silently fails.
      JConfig.log().logDebug("reload from the database failed for (" + getIdentifier() + ")");
    }
    return false;
  }

//  private static Table sDB = null;
  protected static String getTableName() { return "entries"; }
  protected Table getDatabase() {
    return getRealDatabase();
  }

  private static ThreadLocal<Table> tDB = new ThreadLocal<Table>() {
    protected synchronized Table initialValue() {
      return openDB(getTableName());
    }
  };

  public static Table getRealDatabase() {
    return tDB.get();
  }

  public static AuctionEntry findFirstBy(String key, String value) {
    return (AuctionEntry) ActiveRecord.findFirstBy(AuctionEntry.class, key, value);
  }

  @SuppressWarnings({"unchecked"})
  public static List<AuctionEntry> findActive() {
    String notEndedQuery = "SELECT e.* FROM entries e JOIN auctions a ON a.id = e.auction_id WHERE (e.ended != 1 OR e.ended IS NULL) ORDER BY a.ending_at ASC";
    return (List<AuctionEntry>) findAllBySQL(AuctionEntry.class, notEndedQuery);
  }

  @SuppressWarnings({"unchecked"})
  public static List<AuctionEntry> findEnded() {
    return (List<AuctionEntry>) findAllBy(AuctionEntry.class, "ended", "1");
  }

  @SuppressWarnings({"unchecked"})
  public static List<AuctionEntry> findAllSniped() {
    return (List<AuctionEntry>) findAllBySQL(AuctionEntry.class, "SELECT * FROM " + getTableName() + " WHERE (snipe_id IS NOT NULL OR multisnipe_id IS NOT NULL)");
  }

  private static Date updateSince = new Date();
  private static Date endingSoon = new Date();
  private static SimpleDateFormat mDateFormat = new SimpleDateFormat(DB_DATE_FORMAT);

  @SuppressWarnings({"unchecked"})
  public static List<AuctionEntry> findAllNeedingUpdates(long since) {
    long timeRange = System.currentTimeMillis() - since;
    updateSince.setTime(timeRange);
    return (List<AuctionEntry>) findAllByPrepared(AuctionEntry.class,
        "SELECT e.* FROM entries e" +
        "  JOIN auctions a ON a.id = e.auction_id" +
        "  WHERE (e.ended != 1 OR e.ended IS NULL)" +
        "    AND (e.last_updated_at IS NULL OR e.last_updated_at < ?)" +
        "  ORDER BY a.ending_at ASC", mDateFormat.format(updateSince));
  }

  @SuppressWarnings({"unchecked"})
  public static List<AuctionEntry> findEndingNeedingUpdates(long since) {
    long timeRange = System.currentTimeMillis() - since;
    updateSince.setTime(timeRange);

    //  Update more frequently in the last 25 minutes.
    endingSoon.setTime(System.currentTimeMillis() + 25 * Constants.ONE_MINUTE);

    /**
      SELECT e.* FROM entries e
        JOIN auctions a ON a.id = e.auction_id
        WHERE a.ending_at < '2010-12-26 01:45:54.0'
          AND (e.ended != 1 OR e.ended IS NULL)
          AND (e.last_updated_at IS NULL OR e.last_updated_at < '2010-11-25 20:31:49.0')
        ORDER BY a.ending_at ASC;
    **/
    return (List<AuctionEntry>)findAllByPrepared(AuctionEntry.class,
        "SELECT e.* FROM entries e JOIN auctions a ON a.id = e.auction_id" +
        "  WHERE a.ending_at < ?" +
        "    AND (e.ended != 1 OR e.ended IS NULL)" +
        "    AND (e.last_updated_at IS NULL OR e.last_updated_at < ?)" +
        "  ORDER BY a.ending_at ASC", mDateFormat.format(endingSoon), mDateFormat.format(updateSince));

//    return (List<AuctionEntry>)findAllBySQL(AuctionEntry.class, "SELECT e.* FROM entries e JOIN auctions a ON a.id = e.auction_id WHERE a.ending_at < '" + mDateFormat.format(endingSoon) + "' AND (e.ended != 1 OR e.ended IS NULL) AND (e.last_updated_at IS NULL OR e.last_updated_at < '" + mDateFormat.format(updateSince) + "') ORDER BY a.ending_at ASC");
  }

  @SuppressWarnings({"unchecked"})
  public static List<AuctionEntry> findAll() {
    return (List<AuctionEntry>) findAllBySQL(AuctionEntry.class, "SELECT * FROM entries");
  }

  public static int count() {
    return count(AuctionEntry.class);
  }

  public static int activeCount() {
    return getRealDatabase().countBy("(ended != 1 OR ended IS NULL)");
  }

  public static int completedCount() {
    return getRealDatabase().countBy("ended = 1");
  }

  public static int uniqueCount() {
    return getRealDatabase().countBySQL("SELECT COUNT(DISTINCT(identifier)) FROM entries WHERE identifier IS NOT NULL");
  }

  private static final String snipeFinder = "(snipe_id IS NOT NULL OR multisnipe_id IS NOT NULL) AND (ended != 1 OR ended IS NULL)";

  public static int snipedCount() {
    return getRealDatabase().countBy(snipeFinder);
  }

  public static AuctionEntry nextSniped() {
    String sql = "SELECT entries.* FROM entries, auctions WHERE " + snipeFinder + 
        " AND (entries.auction_id = auctions.id) ORDER BY auctions.ending_at ASC";
    return (AuctionEntry) findFirstBySQL(AuctionEntry.class, sql);
  }

  /**
   * Locate an AuctionEntry by first finding an AuctionInfo with the passed
   * in auction identifier, and then looking for an AuctionEntry which
   * refers to that AuctionInfo row.
   *
   * @param identifier - The auction identifier to search for.
   * @return - null indicates that the auction isn't in the database yet,
   * otherwise an AuctionEntry will be loaded and returned.
   */
  public static AuctionEntry findByIdentifier(String identifier) {
    AuctionEntry ae = findFirstBy("identifier", identifier);

    if(ae != null) {
      if(ae.getAuction() == null) {
        JConfig.log().logMessage("Error loading auction #" + identifier + ", entry found, auction missing.");
        ae = null;
      }
    }

    if(ae == null) {
      AuctionInfo ai = AuctionInfo.findByIdentifier(identifier);
      if(ai != null) {
        ae = AuctionEntry.findFirstBy("auction_id", ai.getString("id"));
        if (ae != null) ae.setAuctionInfo(ai);
      }
    }

    return ae;
  }

  public static boolean deleteAll(List<AuctionEntry> toDelete) {
    if(toDelete.isEmpty()) return true;

    String entries = makeCommaList(toDelete);
    List<AuctionInfo> auctions = new ArrayList<AuctionInfo>();
    List<MultiSnipe> multisnipes = new ArrayList<MultiSnipe>();
    List<AuctionSnipe> snipes = new ArrayList<AuctionSnipe>();

    for(AuctionEntry entry : toDelete) {
      auctions.add(entry.getAuction());
      if(entry.isSniped()) snipes.add(entry.getSnipe());
    }

    boolean success = new EventStatus().deleteAllEntries(entries);
    if(!snipes.isEmpty()) success &= AuctionSnipe.deleteAll(snipes);
    if(!multisnipes.isEmpty()) success &= MultiSnipe.deleteAll(multisnipes);
    success &= AuctionInfo.deleteAll(auctions);
    success &= toDelete.get(0).getDatabase().deleteBy("id IN (" + entries + ")");

    return success;
  }

  public boolean delete() {
    if(getAuction() != null) getAuction().delete();
    if(getSnipe() != null) getSnipe().delete();
    return super.delete();
  }

  //  TODO mrs -- Move this to an AuctionEntryBuilder class
  public static final String newRow = "<tr><td>";
  public static final String newCol = "</td><td>";
  public static final String endRow = "</td></tr>";

  // TODO -- Extract this crap out to a EntryHTMLBuilder class, which gets instantiated with an AuctionEntry object.
  public String buildInfoHTML(boolean includeEvents) {
    return buildInfoHTML(includeEvents, false);
  }

  public String buildInfoHTML(boolean includeEvents, boolean forRSS) {
    String prompt = "";

    if(forRSS) {
      prompt += "<b>" + StringTools.stripHigh(getTitle()) + "</b> (" + getIdentifier() + ")<br>";
    } else {
      prompt += "<b>" + getTitle() + "</b> (" + getIdentifier() + ")<br>";
    }
    prompt += "<table>";
    boolean addedThumbnail = false;
    if(getThumbnail() != null) {
      if (forRSS) {
        try {
          InetAddress thisIp = InetAddress.getLocalHost();
          prompt += newRow + "<img src=\"http://" + thisIp.getHostAddress() + ":" + JConfig.queryConfiguration("server.port", Constants.DEFAULT_SERVER_PORT_STRING) + "/" + getIdentifier() + ".jpg\">" + newCol + "<table>";
          addedThumbnail = true;
        } catch (UnknownHostException e) {
          //  Couldn't find THIS host?!?  Perhaps that means we're not online?
          JConfig.log().logMessage("Unknown host trying to look up the local host.  Is the network off?");
        }
      } else {
        prompt += newRow + "<img src=\"" + getThumbnail() + "\">" + newCol + "<table>";
        addedThumbnail = true;
      }
    }
    prompt = buildInfoBody(prompt, includeEvents, addedThumbnail);

  	return(prompt);
  }

  private String buildRow(String label, Object value) {
    return newRow + label + newCol + (value != null ? value.toString() : "null") + endRow;
  }

  private String buildInfoBody(String prompt, boolean includeEvents, boolean addedThumbnail) {
    if(!isFixed()) {
      prompt += buildRow("Currently", getCurrentPrice() + " (" + getNumBidders() + " Bids)");
      String bidder = getHighBidder();
      prompt += buildRow("High bidder", bidder == null ? "(n/a)" : bidder);
    } else {
      prompt += buildRow("Price", getCurrentPrice());
    }
    if(isDutch()) {
      prompt += buildRow("Quantity", getQuantity());
    }

    if(JConfig.debugging() && JConfig.scriptingEnabled()) {
      prompt += buildRow("Sticky", Boolean.toString(isSticky()));
      prompt += buildRow("Category", getCategory());
    }

    if(isBidOn()) {
      prompt += buildRow("Your max bid", getBid());
      if(getBidQuantity() != 1) {
        prompt += buildRow("Quantity of", getBidQuantity());
      }
    }

    if(isSniped()) {
      prompt += buildRow("Sniped for", getSnipeAmount());
      if(getSnipeQuantity() != 1) {
        prompt += buildRow("Quantity of", getSnipeQuantity());
      }
      prompt += newRow + "Sniping at " + (getSnipeTime() / 1000) + " seconds before the end." + endRow;
    }

    if(getShipping() != null && !getShipping().isNull()) {
      prompt += buildRow("Shipping", getShipping());
    }
    if(!getInsurance().isNull()) {
      prompt += buildRow("Insurance (" + (getInsuranceOptional()?"optional":"required") + ")", getInsurance());
    }
    prompt += buildRow("Seller", getSeller());
    if(isComplete()) {
      prompt += buildRow("Listing ended at ", getEndDate());
    } else {
      prompt += buildRow("Listing ends at", getEndDate());
    }
    if(getLastUpdated() != null) {
      prompt += buildRow("Last updated at", getLastUpdated());
    }

    if(addedThumbnail) {
      prompt += "</table>" + endRow;
    }
    prompt += "</table>";

    if(!isFixed() && !getBuyNow().isNull()) {
      if(isComplete()) {
        prompt += "<b>You could have used Buy It Now for " + getBuyNow() + "</b><br>";
      } else {
        prompt += "<b>Or you could buy it now, for " + getBuyNow() + ".</b><br>";
        prompt += "Note: <i>To 'Buy Now' through this program,<br>      select 'Buy' from the context menu.</i><br>";
      }
    }

    if(isComplete()) {
      prompt += "<i>Listing has ended.</i><br>";
    }

    if(getComment() != null) {
      prompt += "<br><u>Comment</u><br>";

      prompt += "<b>" + getComment() + "</b><br>";
    }

    if(includeEvents) prompt += "<b><u>Events</u></b><blockquote>" + getStatusHistory() + "</blockquote>";
    return prompt;
  }

  public String buildHTMLComment(boolean showThumbnail) {
    boolean hasComment = (getComment() != null);
    boolean hasThumb = showThumbnail && (getThumbnail() != null);

    if(JConfig.queryConfiguration("display.thumbnail", "true").equals("false")) hasThumb = false;
    if(!hasComment && !hasThumb) return null;

    StringBuffer wholeHTML = new StringBuffer("<html><body>");
    if(hasThumb && hasComment) {
      wholeHTML.append("<table><tr><td><img src=\"").append(getThumbnail()).append("\"></td><td>").append(getComment()).append("</td></tr></table>");
    } else {
      if(hasThumb) {
        wholeHTML.append("<img src=\"").append(getThumbnail()).append("\">");
      } else {
        wholeHTML.append(getComment());
      }
    }
    wholeHTML.append("</body></html>");

    return wholeHTML.toString();
  }

  public static void setResolver(Resolver resolver) {
    sResolver = resolver;
  }

  public static XMLElement retrieveAuctionXML(String identifier) {
    AuctionEntry ae = AuctionEntry.construct(identifier);
    if (ae != null) {
      return ae.toXML(); //  TODO -- Check high bidder (a separate request).
    }

    return null;
  }

  public static StringBuffer retrieveAuctionXMLString(String identifier) {
    XMLElement xe = retrieveAuctionXML(identifier);

    return xe != null ? xe.toStringBuffer() : null;
  }

  //  Debugging method, to test multisnipe cancelling.
  public void win() {
    MultiSnipe ms = getMultiSnipe();
    ms.setWonAuction(/* this */);
  }

  public static int countByCategory(Category c) {
    if(c == null) return 0;
    return getRealDatabase().countBySQL("SELECT COUNT(*) FROM entries WHERE category_id=" + c.getId());
  }

  @SuppressWarnings({"unchecked"})
  public static List<AuctionEntry> findAllBy(String column, String value) {
    return (List<AuctionEntry>)ActiveRecord.findAllBy(AuctionEntry.class, column, value);
  }

  public void setNumBids(int bidCount) {
    mAuction.setNumBids(bidCount);
  }

  @SuppressWarnings({"unchecked"})
  public static List<AuctionEntry> findManualUpdates() {
    return (List<AuctionEntry>) findAllBySQL(AuctionEntry.class, "SELECT e.* FROM entries e JOIN auctions a ON a.id = e.auction_id WHERE e.last_updated_at IS NULL ORDER BY a.ending_at ASC");
  }

  public boolean isUpdateRequired() {
    return getDate("last_updated_at") == null;
  }

  @SuppressWarnings({"unchecked"})
  public static List<AuctionEntry> findRecentlyEnded(int itemCount) {
    return (List<AuctionEntry>) findAllBySQL(AuctionEntry.class, "SELECT e.* FROM entries e JOIN auctions a ON a.id = e.auction_id WHERE e.ended = 1 ORDER BY a.ending_at DESC", itemCount);
  }

  @SuppressWarnings({"unchecked"})
  public static List<AuctionEntry> findEndingSoon(int itemCount) {
    return (List<AuctionEntry>) findAllBySQL(AuctionEntry.class, "SELECT e.* FROM entries e JOIN auctions a ON a.id = e.auction_id WHERE (e.ended != 1 OR e.ended IS NULL) ORDER BY a.ending_at ASC", itemCount);
  }

  @SuppressWarnings({"unchecked"})
  public static List<AuctionEntry> findBidOrSniped(int itemCount) {
    return (List<AuctionEntry>) findAllBySQL(AuctionEntry.class, "SELECT e.* FROM entries e JOIN auctions a ON a.id = e.auction_id WHERE (e.snipe_id IS NOT NULL OR e.multisnipe_id IS NOT NULL OR e.bid_amount IS NOT NULL) ORDER BY a.ending_at ASC", itemCount);
  }
}
