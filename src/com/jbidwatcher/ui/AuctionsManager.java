package com.jbidwatcher.ui;
/*
 * Copyright (c) 2000-2007, CyberFOX Software, Inc. All Rights Reserved.
 *
 * Developed by mrs (Morgan Schweers)
 */

/*!@class AuctionsManager
 * @brief AuctionsManager abstracts group functionality for all
 * managed groups of auctions
 *
 * So, for example, it supports searching all groups of auctions for
 * outstanding snipes, for snipes that need to fire, for removing,
 * verifying, adding, and retrieving auctions, and similar features
 */

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import com.jbidwatcher.util.PauseManager;
import com.jbidwatcher.util.Record;
import com.jbidwatcher.util.config.*;
import com.jbidwatcher.util.queue.*;
import com.jbidwatcher.util.Constants;
import com.jbidwatcher.auction.server.AuctionServerManager;
import com.jbidwatcher.auction.server.AuctionStats;
import com.jbidwatcher.auction.server.AuctionServer;
import com.jbidwatcher.auction.*;
import org.json.simple.JSONObject;

import java.util.*;

@Singleton
public class AuctionsManager implements TimerHandler.WakeupProcess, EntryManager, JConfig.ConfigListener, MessageQueue.Listener {
  private FilterManager mFilter;
  private final PauseManager mPauseManager;
  private final EntryCorral entryCorral;
  private final Provider<AuctionServerManager> serverManagerProvider;

  //  Checkpoint (save) every N minutes where N is configurable.
  private long mCheckpointFrequency;
  private long mLastCheckpointed = 0;
  private static final int AUCTIONCOUNT = 100;
  private static final int MAX_PERCENT = AUCTIONCOUNT;
  private static TimerHandler sTimer = null;

  /**
   * @brief AuctionsManager is a singleton, there should only be one
   * in the system.
   */
  @Inject
  private AuctionsManager(FilterManager filterManager, PauseManager pauseManager, EntryCorral corral, Provider<AuctionServerManager> serverManagerProvider) {
    //  This should be loaded from the configuration settings.
    mCheckpointFrequency = 10 * Constants.ONE_MINUTE;
    mLastCheckpointed = System.currentTimeMillis();

    mPauseManager = pauseManager;
    mFilter = filterManager;
    entryCorral = corral;
    this.serverManagerProvider = serverManagerProvider;

    MQFactory.getConcrete("manager").registerListener(this);
  }

  public void messageAction(Object deQ) {
    String identifier = (String)deQ;

    AuctionEntry ae = entryCorral.takeForRead(identifier);
    addEntry(ae);
  }

  public FilterManager getFilters() {
    return mFilter;
  }

  /////////////////////////////////////////////////////////
  //  Mass-equivalents for Auction-list specific operations

  /**
   * @brief Check if it's time to save the auctions out yet.
   */
  private void checkSnapshot() {
    if( (mLastCheckpointed + mCheckpointFrequency) < System.currentTimeMillis() ) {
      mLastCheckpointed = System.currentTimeMillis();
//      saveAuctions();
      System.gc();
    }
  }

  private List<AuctionEntry> normalizeEntries(List<AuctionEntry> entries) {
    List<AuctionEntry> output = new ArrayList<AuctionEntry>();
    for(AuctionEntry ae : entries) {
      output.add(entryCorral.takeForRead(ae.getIdentifier()));
    }
    return output;
  }

  /**
   * @brief Check all the auctions for active events, and check if we
   * should snapshot the auctions off to disk.
   * 
   * @return True if any auctions updated.
   */
  public boolean check() throws InterruptedException {
    boolean neededUpdate = false;
    List<AuctionEntry> needUpdate;
    if(!mPauseManager.isPaused()) {
      needUpdate = normalizeEntries(EntryCorral.findAllNeedingUpdates(Constants.ONE_MINUTE * 69)); // TODO: Simplify to load just identifiers?
      updateList(needUpdate);
      neededUpdate = !needUpdate.isEmpty();

      //  These could be two separate threads, doing slow and fast updates.
      needUpdate = normalizeEntries(EntryCorral.findEndingNeedingUpdates(Constants.ONE_MINUTE));
      updateList(needUpdate);
      neededUpdate |= !needUpdate.isEmpty();
    }

    //  Or three, doing slow, fast, and manual...
    needUpdate = normalizeEntries(EntryCorral.findManualUpdates());
    updateList(needUpdate);
    neededUpdate |= !needUpdate.isEmpty();

    checkSnapshot();

    return neededUpdate;
  }

  /**
   * It's time to update, so show that we're updating this auction,
   * update it, filter it to see if it needs to move (i.e. is
   * completed), and then let the user know we finished.
   *
   * @param ae - The auction to update.
   */
  public void doUpdate(AuctionEntry ae) {
    String titleWithComment = ae.getTitleAndComment();

    if (!ae.isComplete() || ae.isUpdateRequired()) {
      MQFactory.getConcrete("Swing").enqueue("Updating " + titleWithComment);
      MQFactory.getConcrete("redraw").enqueue(ae.getIdentifier());
      Thread.yield();
      Record before = ae.getBacking();
      ae.update();
      Record after = ae.getBacking();

      //  TODO(mschweers) - This probably detects too much, like timestamp changes, etc...  Need to test.
      boolean same = JSONObject.toJSONString(after).equals(JSONObject.toJSONString(before));

      MQFactory.getConcrete("my").enqueue("UPDATE " + ae.getIdentifier() + "," + Boolean.toString(!same));
      if (!same) {
        //  Forget any cached info we have; the on-disk version has changed.
        String category = ae.getCategory();
        MQFactory.getConcrete("redraw").enqueue(category);
      }

      ae = (AuctionEntry) entryCorral.takeForWrite(ae.getIdentifier());  //  Lock the item
      entryCorral.erase(ae.getIdentifier());
      MQFactory.getConcrete("redraw").enqueue(ae.getIdentifier());
      MQFactory.getConcrete("Swing").enqueue("Done updating " + ae.getTitleAndComment());
    }
  }

  private void updateList(List<AuctionEntry> needUpdate) throws InterruptedException {
    for(AuctionEntry ae : needUpdate) {
      if (Thread.interrupted()) throw new InterruptedException();
      // It's likely that we've pulled a big list of stuff to update before realizing the
      // networking is down; pause updating for a little bit until it's likely to have come
      // back.
      if (!mPauseManager.isPaused()) {
        boolean forced = ae.isUpdateRequired();

        MQFactory.getConcrete("update " + ae.getCategory()).enqueue("start " + ae.getIdentifier());

        doUpdate(ae);
        entryCorral.putWeakly(ae);

        MQFactory.getConcrete("update " + ae.getCategory()).enqueue("stop " + ae.getIdentifier());

        if (forced) MQFactory.getConcrete("redraw").enqueue(ae.getCategory()); // Redraw a tab that has a forced update.
      }
    }
  }

  /**
   * @brief Add a new auction entry to the set.
   *
   * @param ae - The auction entry to add.
   */
  public void addEntry(AuctionEntry ae) {
    mFilter.addAuction(ae);
  }

  /**
   * @brief Delete from ALL auction lists!
   *
   * The FilterManager does this, as it needs to be internally
   * self-consistent.
   * 
   * @param ae - The auction entry to delete.
   */
  public void delEntry(AuctionEntry ae) {
    String id = ae.getIdentifier();
    DeletedEntry.create(id);
    ae.cancelSnipe(false);
    mFilter.deleteAuction(ae);
    ae.delete();
  }

  public int loadAuctionsFromDatabase() {
    int totalCount = AuctionInfo.count();
    int activeCount = EntryCorral.activeCount();

    MQFactory.getConcrete("splash").enqueue("WIDTH " + activeCount);
    MQFactory.getConcrete("splash").enqueue("SET 0");

    AuctionServer newServer = serverManagerProvider.get().getServer();
    if (totalCount == 0) {
      if(JConfig.queryConfiguration("stats.auctions") == null) JConfig.setConfiguration("stats.auctions", "0");
      return 0;
    }

    serverManagerProvider.get().loadAuctionsFromDB(newServer);
    AuctionStats as = serverManagerProvider.get().getStats();

    int savedCount = Integer.parseInt(JConfig.queryConfiguration("last.auctioncount", "-1"));
    if (as != null) {
      if (savedCount != -1 && as.getCount() != savedCount) {
        MQFactory.getConcrete("Swing").enqueue("NOTIFY Failed to load all auctions from database.");
      }
    }

    return activeCount;
  }

  public int clearDeleted() {
    int rval = DeletedEntry.clear();

    System.gc();

    return rval;
  }

  public void start() {
    if(sTimer == null) {
      sTimer = new TimerHandler(this);
      sTimer.setName("Updates");
      sTimer.start();
    }
    JConfig.registerListener(this);
  }

  public void updateConfiguration() {
    String newSnipeTime = JConfig.queryConfiguration("snipemilliseconds");
    if(newSnipeTime != null) {
      AuctionEntry.setDefaultSnipeTime(Long.parseLong(newSnipeTime));
    }
  }
}
