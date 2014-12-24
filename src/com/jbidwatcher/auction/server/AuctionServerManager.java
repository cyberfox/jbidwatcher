package com.jbidwatcher.auction.server;
/*
 * Copyright (c) 2000-2007, CyberFOX Software, Inc. All Rights Reserved.
 *
 * Developed by mrs (Morgan Schweers)
 */

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.jbidwatcher.util.Constants;
import com.jbidwatcher.util.PauseManager;
import com.jbidwatcher.util.queue.MQFactory;
import com.jbidwatcher.util.queue.MessageQueue;
import com.jbidwatcher.search.SearchManager;
import com.jbidwatcher.util.config.JConfig;
import com.jbidwatcher.util.StringTools;
import com.jbidwatcher.auction.*;

import java.util.*;

/**
 * Simplified on February 17, 2008 to be single-auction-site specific;
 * JBidwatcher is not used on any other auction sites than eBay, and hasn't
 * been for many years.
 */
@Singleton
public class AuctionServerManager implements MessageQueue.Listener, Resolver {
  private final EntryFactory entryFactory;
  private EntryManager entryManager = null;
  private AuctionServer mServer = null;
  private SearchManager searcher;
  private PauseManager pauseManager;

  @Inject
  private AuctionServerManager(EntryManager entryManager, SearchManager searcher, EntryFactory entryFactory, PauseManager pauseManager) {
    this.entryManager = entryManager;
    this.searcher = searcher;
    this.entryFactory = entryFactory;
    this.pauseManager = pauseManager;

    this.entryFactory.setResolver(this);
    MQFactory.getConcrete("auction_manager").registerListener(this);
  }

  private Map<String, Long> timingLog = new HashMap<String, Long>();
  private final Map<String, Long> startLog = new HashMap<String, Long>();
  private Map<String, Long> countLog = new HashMap<String, Long>();
  private Map<String, LinkedList<Long>> last10Log = new HashMap<String, LinkedList<Long>>();
  private void timeStart(String blockName) {
    synchronized(startLog) {
      startLog.put(blockName, System.currentTimeMillis());
    }
  }
  private void timeStop(String blockName) {
    synchronized (startLog) {
      long now = System.currentTimeMillis();
      long started = startLog.get(blockName);
      startLog.remove(blockName);
      long accum = timingLog.containsKey(blockName) ? timingLog.get(blockName) : 0;
      accum += (now - started);
      LinkedList<Long> last10 = last10Log.get(blockName);
      if (last10 == null) last10 = new LinkedList<Long>();
      last10.add(now - started);
      if (last10.size() > 10) last10.removeFirst();
      last10Log.put(blockName, last10);
      timingLog.put(blockName, accum);
      countLog.put(blockName, (countLog.containsKey(blockName) ? countLog.get(blockName) + 1 : 1));
    }
  }

  private void timeDump(String last10From) {
    synchronized(startLog) {
      for (Map.Entry<String, Long> segment : timingLog.entrySet()) {
        long accum = segment.getValue();
        long count = countLog.get(segment.getKey());
        Double avg = (accum * 1.0) / (count * 1.0);
        JConfig.log().logDebug(segment.getKey() + ": " + avg + " x " + count + "(" + segment.getValue() + ")");
      }
      JConfig.log().logDebug("Last 10 from " + last10From + ": " + StringTools.comma(last10Log.get(last10From)));
    }
  }

  private abstract class Report {
    public abstract void report(AuctionEntry ae, int count);
  }

  public void loadAuctionsFromDB(final AuctionServer newServer) {
    MQFactory.getConcrete("splash").enqueue("SET 0");

    timeStart("counts");
    // True up the Auction Entries first.  I want this to not be necessary anymore.
    AuctionEntry.trueUpEntries();

    int entryCount = AuctionEntry.count();
    // Metrics
    JConfig.getMetrics().trackCustomData("categories", Integer.toString(Category.count(Category.class)));
    JConfig.getMetrics().trackCustomData("entries", Integer.toString(entryCount));
    int auctionCount = AuctionInfo.count();
    int uniqueEntries = AuctionEntry.uniqueCount();
    int activeEntries = AuctionEntry.activeCount();
    // Metrics
    JConfig.getMetrics().trackCustomData("active", Integer.toString(activeEntries));
    JConfig.getMetrics().trackCustomData("sniped", Integer.toString(AuctionEntry.snipedCount()));
    int uniqueCount = AuctionInfo.uniqueCount();
    timeStop("counts");

    if (JConfig.queryConfiguration("stats.auctions") == null) JConfig.setConfiguration("stats.auctions", Long.toString(uniqueEntries));

    JConfig.log().logMessage("Loading listings from the database (" + activeEntries + "/" + uniqueEntries + "/" + entryCount + " entries, " + uniqueCount + "/" + auctionCount + " auctions)");
    timeStart("findAll");
    List<AuctionEntry> entries = AuctionEntry.findActive(); //TODO EntryCorral these
    timeStop("findAll");
    timeStart("findAuctions");
    connectEntries(entries);
    timeStop("findAuctions");

    final List<AuctionEntry> sniped = new ArrayList<AuctionEntry>();
    JConfig.log().logMessage("Done with the initial load (got " + entries.size() + " active entries)");
    importListingsToUI(newServer, entries, new Report() {
      public void report(AuctionEntry ae, int count) {
        MQFactory.getConcrete("splash").enqueue("SET " + count);
        if (!ae.isComplete() && ae.isSniped()) {
          sniped.add(ae);
        }
      }
    });
    JConfig.log().logDebug("Auction Entries loaded");

    spinOffCompletedLoader(newServer);

    JConfig.log().logDebug("Completed loader spun off");
    for(AuctionEntry snipable:sniped) {
      timeStart("snipeSetup");
      if(!snipable.isComplete()) {
        snipable.refreshSnipe();
      }
      timeStop("snipeSetup");
    }
    JConfig.log().logDebug("Snipes processed");
    timeDump("addEntry");
  }

  private void connectEntries(List<AuctionEntry> entries) {
    List<String> auctionIDs = new ArrayList<String>(entries.size());
    for(AuctionEntry entry : entries) {
      auctionIDs.add(entry.getAuctionId());
    }
    List<AuctionInfo> auctions = AuctionInfo.findAllByIds(auctionIDs);
    Map<String, AuctionInfo> joining = new HashMap<String, AuctionInfo>(entries.size());
    for(AuctionInfo info : auctions) {
      joining.put(info.getId().toString(), info);
    }
    for (AuctionEntry entry : entries) {
      AuctionInfo ai = joining.get(entry.getAuctionId());
      entry.setAuctionInfo(ai);
    }
  }

  private void spinOffCompletedLoader(final AuctionServer newServer) {
    Thread completedHandler = new Thread() {
      public void run() {
        final MessageQueue tabQ = MQFactory.getConcrete("complete Tab");
        tabQ.enqueue("REPORT Importing completed listings");
        tabQ.enqueue("SHOW");

        timeStart("findEnded");
        List<AuctionEntry> entries = AuctionEntry.findEnded();//TODO EntryCorral these?
        timeStop("findEnded");

        int endedCount = entries.size();
        final double percentStep = ((double)endedCount) / 100.0;
        final double percentMultiple = 100.0 / ((double)endedCount);
        tabQ.enqueue("PROGRESS");
        tabQ.enqueue("PROGRESS Loading...");
        importListingsToUI(newServer, entries, new Report() {
          public void report(AuctionEntry ae, int count) {
            if(percentStep < 1.0) {
              tabQ.enqueue("PROGRESS " + Math.round(count * percentMultiple));
            } else {
              if(count % (Math.round(percentStep)) == 0) {
                tabQ.enqueue("PROGRESS " + Math.round(count / percentStep));
              }
            }
            try { Thread.sleep(50); } catch(InterruptedException ie) { /* ignore */ }
          }
        });
        tabQ.enqueue("HIDE");
        AuctionEntry.getRealDatabase().commit();
      }
    };
    Thread lostHandler = new Thread() {
      public void run() {
        List<AuctionInfo> lostAuctions = AuctionInfo.findLostAuctions();
        if(lostAuctions != null && !lostAuctions.isEmpty()) {
          JConfig.log().logMessage("Recovering " + lostAuctions.size() + " listings.");
          for (AuctionInfo ai : lostAuctions) {
            AuctionEntry ae = entryFactory.constructEntry();
            ae.setAuctionInfo(ai);
            ae.setCategory("recovered");
            ae.setSticky(true);
            ae.setNeedsUpdate();
            entryManager.addEntry(ae);
          }
          MQFactory.getConcrete("recovered Tab").enqueue("REPORT These auctions had lost their settings.");
          MQFactory.getConcrete("recovered Tab").enqueue("SHOW");
          AuctionEntry.getRealDatabase().commit();
        }
      }
    };
    completedHandler.start();
    lostHandler.start();
  }

  private void importListingsToUI(AuctionServer newServer, List<AuctionEntry> entries, Report r) {
    int count = 0;

    for(AuctionEntry ae : entries) {
      timeStart("setServer");
      ae.setServer(newServer);
      timeStop("setServer");

      if (!ae.hasAuction()) {
        JConfig.log().logMessage("We lost the underlying auction for: " + ae.dumpRecord());
        boolean recentlyUpdated = ae.getLastUpdated() != null && ae.getLastUpdated().after(new Date(System.currentTimeMillis() - Constants.ONE_DAY * 45));
        if(ae.getString("identifier") != null && recentlyUpdated) {
          JConfig.log().logMessage("Trying to reload auction via its auction identifier.");
          MQFactory.getConcrete("drop").enqueue(ae.getString("identifier"));
        } else {
          if(!recentlyUpdated) {
            JConfig.log().logMessage("Auction entry " + ae.getString("identifier") + " is too old to reload, deleting.");
          } else {
            JConfig.log().logMessage("Auction entry id " + ae.getId() + " doesn't have enough detail to reload; deleting.");
          }
          timeStart("delete");
          ae.delete();
          timeStop("delete");
        }
      } else {
        timeStart("addEntry");
        timeStart("addEntry-" + ae.getCategory());
        try {
          entryManager.addEntry(ae);
        } catch(Exception e) {
          String errorMessage = "Failed to add an auction entry";
          errorMessage += " for item " + ae.getIdentifier() + " (" + ae.getId() + ") ";
          JConfig.log().handleException(errorMessage, e);
        }
        timeStop("addEntry-" + ae.getCategory());
        timeStop("addEntry");
      }
      if(r != null) r.report(ae, count++);
    }
  }

  /**
   * @brief Serialize access to the time updating function, so that
   * everybody in the world doesn't try to update the time all at
   * once, like they used to.  Four threads trying to update the time
   * all together caused some nasty errors.
   */
  public void messageAction(Object deQ) {
    String cmd = (String)deQ;

    if(cmd.equals("TIMECHECK")) {
      if(pauseManager.isPaused()) {
        //  Punt, and let the time drift until the next update.
        return;
      }
      AuctionServerInterface defaultServer = getServer();

      defaultServer.reloadTime();

      long servTime = defaultServer.getServerTimeDelta();
      Date now = new Date(System.currentTimeMillis() + servTime);
      MQFactory.getConcrete("Swing").enqueue("Server time is now: " + now);
    }
  }

  public String getDefaultServerTime() {
    AuctionServerInterface defaultServer = getServer();
    return defaultServer.getTime();
  }

  public AuctionServer setServer(AuctionServer aucServ) {
    if(mServer != null) {
      //noinspection ThrowableInstanceNeverThrown
      RuntimeException here = new RuntimeException("Trying to add a server, when we've already got one!");
      JConfig.log().handleException("setServer error!", here);
      return mServer;
    }
    mServer = aucServ;

    mServer.addSearches(searcher);
    return(mServer);
  }

  public AuctionServer getServer() {
    return mServer;
  }

  public ServerMenu addAuctionServerMenus() {
    return mServer.establishMenu();
  }

  public void cancelSearches() {
    mServer.cancelSearches();
  }

  public AuctionStats getStats() {
    AuctionStats outStat = new AuctionStats();

    outStat._count = AuctionEntry.count();
    outStat._completed = AuctionEntry.completedCount();
    outStat._snipes = AuctionEntry.snipedCount();
    outStat._nextSnipe = AuctionEntry.nextSniped(); //TODO EntryCorral this?
    outStat._nextEnd = null;
    outStat._nextUpdate = null;

    return outStat;
  }
}
