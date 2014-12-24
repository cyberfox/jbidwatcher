package com.jbidwatcher.auction;
/*
 * Copyright (c) 2000-2007, CyberFOX Software, Inc. All Rights Reserved.
 *
 * Developed by mrs (Morgan Schweers)
 */

import com.jbidwatcher.util.*;
import com.jbidwatcher.auction.event.EventLogger;
import com.jbidwatcher.auction.event.EventStatus;
import com.jbidwatcher.util.Currency;
import com.jbidwatcher.util.Observer;
import com.jbidwatcher.util.config.*;
import com.jbidwatcher.util.queue.MQFactory;
import com.jbidwatcher.util.db.ActiveRecord;
import com.jbidwatcher.util.db.Table;

import java.text.MessageFormat;
import java.text.SimpleDateFormat;
import java.util.*;

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
public class AuctionEntry extends AuctionCore implements Comparable<AuctionEntry>, EntryInterface {
  private Category mCategory;
  private Presenter mAuctionEntryPresenter = null;

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

  private AuctionSnipe mSnipe = null;

  /**
   * How much was a cancelled snipe for?  (Recordkeeping)
   */
  private Currency mCancelSnipeBid = null;

  /**
   * What AuctionServer is responsible for handling this
   * AuctionEntry's actions?
   */
  private AuctionServerInterface mServer = null;

  /**
   * The last time this auction was bid on.  Not presently used,
   * although set, saved, and loaded consistently.
   */
  private long mBidAt = 0;

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
    AuctionInfo info = mServer.create(auctionIdentifier);
    mLoaded = info != null;
    if(mLoaded) {
      setString("identifier", auctionIdentifier);
      info.saveDB();
      setInteger("auction_id", info.getId());
    }

    /**
     * Note that a bad auction (couldn't get an auction server, or a
     * specific auction info object) doesn't have an identifier, and
     * isn't loaded.  This will fail out the init process, and this
     * will never be added to the items list.
     */
    if (mLoaded) {
      Currency currentPrice = info.getBestPrice();
      setDate("last_updated_at", new Date());
      setDefaultCurrency(currentPrice);
      saveDB();
      notifyObservers(ObserverMode.AFTER_CREATE);
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
   * A constructor that does almost nothing.  This is to be used
   * for ActiveRecord, which fills this out when pulling from a database record.
   * <p/>
   * Uses the default server.
   */
  public AuctionEntry() {
    checkConfigurationSnipeTime();
    notifyObservers(ObserverMode.AFTER_CREATE);
  }

  public boolean hasAuction() {
    AuctionInfo ai = findByIdOrIdentifier(getAuctionId(), getIdentifier());
    return (ai != null);
  }

  public enum ObserverMode { AFTER_CREATE, AFTER_SAVE }
  private static List<Observer<AuctionEntry>> allObservers = new ArrayList<Observer<AuctionEntry>>();

  private void notifyObservers(ObserverMode event) {
    for(Observer<AuctionEntry> toNotify : allObservers) {
      switch (event) {
        case AFTER_CREATE: {
          toNotify.afterCreate(this);
          break;
        }
        case AFTER_SAVE: {
          toNotify.afterSave(this);
        }
      }
    }
  }

  public static void addObserver(Observer<AuctionEntry> observer) {
    allObservers.add(observer);
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
  static AuctionEntry construct(String identifier, AuctionServerInterface server) {
    if (!DeletedEntry.exists(identifier) && findByIdentifier(identifier) == null) {
      AuctionEntry ae = new AuctionEntry(identifier, server);
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

  static AuctionEntry construct(AuctionServerInterface server) {
    AuctionEntry ae = new AuctionEntry();
    ae.setServer(server);
    return ae;
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
   * @return The server that this auction entry is associated with.
   */
  public AuctionServerInterface getServer() {
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
    //noinspection ObjectEquality
    if(newServer != mServer) {
      //  "CANCEL_SNIPE #{id}"
      if(isSniped()) getServer().cancelSnipe(getIdentifier());
      mServer = newServer;
      if(isSniped()) getServer().setSnipe(getIdentifier());
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
  private boolean isLoaded()    { return(mLoaded); }

  /**
   * @brief Check if the current snipe value would be a valid bid currently.
   *
   * @return true if the current snipe is at least one minimum bid
   * increment over the current high bid.  Returns false otherwise.
   */
  public boolean isSnipeValid() {
    if(getSnipe() == null) return false;

    Currency minIncrement = getServer().getMinimumBidIncrement(getCurBid(), getNumBidders());
    boolean rval = false;

    try {
      if(getSnipe().getAmount().getValue() >= getCurBid().add(minIncrement).getValue()) {
        rval = true;
      }
    } catch(Currency.CurrencyTypeException cte) {
      JConfig.log().handleException("This should never happen (" + getCurBid() + ", " + minIncrement + ", " + getSnipe().getAmount() + ")!", cte);
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
  public boolean isSeller() { return getServer().isCurrentUser(getSellerName()); }

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
    setMonetary("last_bid_amount", highBid == null ? Currency.NoValue() : highBid);
    saveDB();
  }

  public void setBidQuantity(int quant) {
    setInteger("last_bid_quantity", quant);
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

  ///////////////////////////
  //  Actual logic functions

  public void updateHighBid() {
    int numBidders = getNumBidders();

    if (numBidders > 0 || isFixed()) {
      getServer().updateHighBid(getIdentifier());
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

    //  If the multisnipe was null, remove the snipe entirely.
    prepareSnipe(Currency.NoValue(), 0);
    setInteger("multisnipe_id", null);
    saveDB();
  }

  private void handleCancel(boolean after_end) {
    if(isSniped()) {
      JConfig.log().logDebug("Cancelling Snipe for: " + getTitle() + '(' + getIdentifier() + ')');
      setLastStatus("Cancelling snipe.");
      if(after_end) {
        setBoolean("auto_canceled", true);
        mCancelSnipeBid = getSnipe().getAmount();
      }
    }
  }

  public void snipeCompleted() {
    setSnipedAmount(getSnipe().getAmount());
    setBid(getSnipe().getAmount());
    setBidQuantity(getSnipe().getQuantity());
    getSnipe().delete();
    setInteger("snipe_id", null);
    mSnipe = null;
    setDirty();
    setNeedsUpdate();
    saveDB();
  }

  private void setSnipedAmount(Currency amount) {
    setMonetary("sniped_amount", amount == null ? Currency.NoValue() : amount);
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
      AuctionInfo ai = getServer().reload(getIdentifier());
    } catch(Exception e) {
      JConfig.log().handleException("Unexpected exception during auction reload/update.", e);
    }
    try {
      updateHighBid();
      checkHighBidder();
    } catch(Exception e) {
      JConfig.log().handleException("Unexpected exception during high bidder check.", e);
    }

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

  private void onComplete() {
    boolean won = isHighBidder() && (!isReserve() || isReserveMet());
    if (won) {
      JConfig.increment("stats.won");
      MQFactory.getConcrete("won").enqueue(getIdentifier());
      // Metrics
      if(getBoolean("was_sniped")) {
        JConfig.getMetrics().trackEvent("snipe", "won");
      } else {
        JConfig.getMetrics().trackEvent("auction", "won");
      }
    } else {
      MQFactory.getConcrete("notwon").enqueue(getIdentifier());
      // Metrics
      if (getBoolean("was_sniped")) {
        JConfig.getMetrics().trackEvent("snipe", "lost");
      } else {
        if(isBidOn()) {
          JConfig.getMetrics().trackEvent("auction", "lost");
        }
      }
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
      getServer().setSnipe(getIdentifier());
    }
    setDirty();
    saveDB();
    MQFactory.getConcrete("Swing").enqueue("SNIPECHANGED");
  }

  /**
   * @brief Refresh the snipe, so it picks up a potentially changed end time, or when initially loading items.
   */
  public void refreshSnipe() {
    getServer().setSnipe(getIdentifier());
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

    int rval = getServer().bid(getIdentifier(), bid, bidQuantity);
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
      rval = getServer().buy(getIdentifier(), quant);
      // Metrics
      if(rval == AuctionServerInterface.BID_BOUGHT_ITEM) {
        JConfig.getMetrics().trackEvent("buy", "success");
      } else {
        JConfig.getMetrics().trackEventValue("buy", "fail", Integer.toString(rval));
      }
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
  public void setSticky(boolean beSticky) {
    if(beSticky != getBoolean("sticky")) {
      setBoolean("sticky", beSticky);
      saveDB();
    }
  }

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

  @SuppressWarnings({"FeatureEnvy"})
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
   * For display during updates, we want the title and potentially the
   * comment, to display all that in the status bar while we're
   * updating.
   *
   * @return - A string containing the title alone, if no comment, or
   * in the format: "title (comment)" otherwise.
   */
  public String getTitleAndComment() {
    String curComment = getComment();
    if (curComment == null) return getTitle();

    return getTitle() + " (" + curComment + ')';
  }

  @Override
  public int hashCode() {
    return getIdentifier().hashCode() ^ getEndDate().hashCode();
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
    //noinspection ObjectEquality
    if(other == this) return 0;

    String identifier = getIdentifier();

    //  If the identifiers are the same, we're equal.
    if(identifier != null && identifier.equals(other.getIdentifier())) return 0;

    final Date myEndDate = getEndDate();
    final Date otherEndDate = other.getEndDate();
    if(myEndDate == null && otherEndDate != null) return 1;
    if(myEndDate != null) {
      if(otherEndDate == null) return -1;

      //  If this ends later than the passed in object, then we are 'greater'.
      if(myEndDate.after(otherEndDate)) return 1;
      if(otherEndDate.after(myEndDate)) return -1;
    }

    //  Whoops!  Dates are equal, down to the second probably, or both null...

    //  If this has a null identifier, we're lower.
    if (identifier == null) {
      if (other.getIdentifier() != null) return -1;
      return 0;
    }
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
  @SuppressWarnings({"ObjectEquality"})
  public boolean isNullAuction() { return get("auction_id") == null; }
  private boolean deleting = false;
  public AuctionInfo getAuction() {
    String identifier = getString("identifier");
    String auctionId = getString("auction_id");

    AuctionInfo info = findByIdOrIdentifier(auctionId, identifier);

    if(info == null) {
      if(!deleting) {
        deleting = true;
        this.delete();
      }
      return sAuction;
    }

    boolean dirty = false;
    if (!getDefaultCurrency().equals(info.getDefaultCurrency())) {
      setDefaultCurrency(info.getDefaultCurrency());
      dirty = true;
    }

    if (getString("identifier") == null) {
      setString("identifier", info.getIdentifier());
      dirty = true;
    }

    if (auctionId == null || !auctionId.equals(info.get("id"))) {
      setInteger("auction_id", info.getId());
      dirty = true;
    }
    if (dirty) {
      saveDB();
    }

    return info;
  }

  protected void loadSecondary() {
    AuctionInfo ai = findByIdOrIdentifier(getAuctionId(), getIdentifier());
    if(ai != null) setAuctionInfo(ai);
  }

  /**
   * @brief Force this auction to use a particular set of auction
   * information for it's core data (like seller's name, current high
   * bid, etc.).
   *
   * @param inAI - The AuctionInfo object to make the new core data.  Must not be null.
   */
  public void setAuctionInfo(AuctionInfo inAI) {
    if (inAI.getId() != null) {
      setSecondary(inAI.getBacking());

      setDefaultCurrency(inAI.getDefaultCurrency());
      setInteger("auction_id", inAI.getId());
      setString("identifier", inAI.getIdentifier()); //?

      checkHighBidder();
      checkEnded();
      saveDB();
    }
  }

  ////////////////////////////////////////
  //  Passthrough functions to AuctionInfo

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
    if (curPrice == null || curPrice.isNull()) return getBuyNowUS();
    return getUSCurBid();
  }

  /**
   * @return - Shipping amount, overrides AuctionInfo shipping amount if present.
   */
  public String getSellerName() { return getAuction().getSellerName(); }

  public Date getStartDate() {
    Date start = super.getStartDate();
    if(start != null) {
      return start;
    }

    return Constants.LONG_AGO;
  }

  public Date getSnipeDate() { return new Date(getEndDate().getTime() - getSnipeTime()); }

  public String getBrowseableURL() { return getServer().getBrowsableURLFromItem(getIdentifier()); }

  public void setErrorPage(StringBuffer page) { mLastErrorPage = page; }
  public StringBuffer getErrorPage() { return mLastErrorPage; }

  public Currency getShippingWithInsurance() {
    Currency ship = getShipping();
    if(ship == null || ship.isNull())
      return Currency.NoValue();
    else {
      ship = addInsurance(ship);
    }
    return ship;
  }

  private Currency addInsurance(Currency ship) {
    if(getInsurance() != null &&
       !getInsurance().isNull() &&
       !isInsuranceOptional()) {
      try {
        ship = ship.add(getInsurance());
      } catch(Currency.CurrencyTypeException cte) {
        JConfig.log().handleException("Insurance is somehow a different type than shipping?!?", cte);
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

  public String getAuctionId() { return get("auction_id"); }

  /**
   * @return - Has this auction already ended?  We keep track of this, so we
   * don't waste time on it afterwards, even as much as creating a
   * Date object, and comparing.
   */
  public void setComplete(boolean complete) { setBoolean("ended", complete); saveDB(); }

  /*************************/
  /* Database access stuff */
  /*************************/

  public String saveDB() {
    if(isNullAuction()) return null;

    String auctionId = getAuctionId();
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
    notifyObservers(ObserverMode.AFTER_SAVE);
    return id;
  }

  public boolean reload() {
    try {
      AuctionEntry ae = AuctionEntry.findFirstBy("id", get("id"));
      if (ae != null) {
        setBacking(ae.getBacking());

        AuctionInfo ai = findByIdOrIdentifier(getAuctionId(), getIdentifier());
        setAuctionInfo(ai);

        ae.getCategory();
        mCategory = ae.mCategory;
        mSnipe = ae.getSnipe();
        mEntryEvents = ae.getEvents();
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

  /** Already corralled... **/
  @SuppressWarnings({"unchecked"})
  public static List<AuctionEntry> findAllSniped() {
    return (List<AuctionEntry>) findAllBySQL(AuctionEntry.class, "SELECT * FROM " + getTableName() + " WHERE (snipe_id IS NOT NULL OR multisnipe_id IS NOT NULL)");
  }

  private static Date updateSince = new Date();
  private static Date endingSoon = new Date();
  private static Date hourAgo = new Date();
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
    hourAgo.setTime(System.currentTimeMillis() - Constants.ONE_HOUR);

    return (List<AuctionEntry>)findAllByPrepared(AuctionEntry.class,
        "SELECT e.* FROM entries e JOIN auctions a ON a.id = e.auction_id" +
        "  WHERE (e.last_updated_at IS NULL OR e.last_updated_at < ?)" +
        "    AND (e.ended != 1 OR e.ended IS NULL)" +
        "    AND a.ending_at < ? AND a.ending_at > ?" +
        "  ORDER BY a.ending_at ASC", mDateFormat.format(updateSince),
        mDateFormat.format(endingSoon), mDateFormat.format(hourAgo));
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

  private static final String snipeFinder = "(snipe_id IS NOT NULL OR multisnipe_id IS NOT NULL) AND (entries.ended != 1 OR entries.ended IS NULL)";

  public static int snipedCount() {
    return getRealDatabase().countBy(snipeFinder);
  }

  public static AuctionEntry nextSniped() {
    String sql = "SELECT entries.* FROM entries, auctions WHERE " + snipeFinder + 
        " AND (entries.auction_id = auctions.id) ORDER BY auctions.ending_at ASC";
    return (AuctionEntry) findFirstBySQL(AuctionEntry.class, sql);
  }

  private static AuctionInfo findByIdOrIdentifier(String id, String identifier) {
    AuctionInfo ai = null;
    if(id != null) {
      ai = AuctionInfo.find(id);
    }

    if (ai == null && identifier != null) {
      ai = AuctionInfo.findByIdentifier(identifier);
    }
    return ai;
  }

  /**
   * Locate an AuctionEntry by first finding an AuctionInfo with the passed
   * in auction identifier, and then looking for an AuctionEntry which
   * refers to that AuctionInfo row.
   *
   * TODO EntryCorral callers? (Probably!)
   *
   * @param identifier - The auction identifier to search for.
   * @return - null indicates that the auction isn't in the database yet,
   * otherwise an AuctionEntry will be loaded and returned.
   */
  public static AuctionEntry findByIdentifier(String identifier) {
    AuctionEntry ae = findFirstBy("identifier", identifier);
    AuctionInfo ai;

    if(ae != null) {
      ai = findByIdOrIdentifier(ae.getAuctionId(), identifier);
      if(ai == null) {
        JConfig.log().logMessage("Error loading auction #" + identifier + ", entry found, auction missing.");
        ae = null;
      }
    }

    if(ae == null) {
      ai = findByIdOrIdentifier(null, identifier);

      if(ai != null) {
        ae = AuctionEntry.findFirstBy("auction_id", ai.getString("id"));
        if (ae != null) ae.setAuctionInfo(ai);
      }
    }

    return ae;
  }

  /**
   * TODO: Clear from the entry corral?
   * @param toDelete
   * @return
   */
  public static boolean deleteAll(List<AuctionEntry> toDelete) {
    if(toDelete.isEmpty()) return true;

    String entries = makeCommaList(toDelete);
    List<Integer> auctions = new ArrayList<Integer>();
    List<AuctionSnipe> snipes = new ArrayList<AuctionSnipe>();

    for(AuctionEntry entry : toDelete) {
      auctions.add(entry.getInteger("auction_id"));
      if(entry.isSniped()) snipes.add(entry.getSnipe());
    }

    boolean success = new EventStatus().deleteAllEntries(entries);
    if(!snipes.isEmpty()) success &= AuctionSnipe.deleteAll(snipes);
    success &= AuctionInfo.deleteAll(auctions);
    success &= getRealDatabase().deleteBy("id IN (" + entries + ")");

    return success;
  }

  public boolean delete() {
    AuctionInfo ai = findByIdOrIdentifier(getAuctionId(), getIdentifier());
    if(ai != null) ai.delete();
    if(getSnipe() != null) getSnipe().delete();
    return super.delete();
  }

  public Presenter getPresenter() {
    return mAuctionEntryPresenter;
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
    AuctionInfo info = findByIdOrIdentifier(getAuctionId(), getIdentifier());
    info.setNumBids(bidCount);
    info.saveDB();
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

  public static void forceUpdateActive() {
    getRealDatabase().execute("UPDATE entries SET last_updated_at=NULL WHERE ended != 1 OR ended IS NULL");
  }

  public static void trueUpEntries() {
    getRealDatabase().execute("UPDATE entries SET auction_id=(SELECT max(id) FROM auctions WHERE auctions.identifier=entries.identifier)");
    getRealDatabase().execute("DELETE FROM entries e WHERE id != (SELECT max(id) FROM entries e2 WHERE e2.auction_id = e.auction_id)");
  }

  public String getUnique() {
    return getIdentifier();
  }

  public void setPresenter(Presenter presenter) {
    mAuctionEntryPresenter = presenter;
  }
}
