package com.jbidwatcher.auction;

/*
 * Copyright (c) 2000-2007, CyberFOX Software, Inc. All Rights Reserved.
 *
 * Developed by mrs (Morgan Schweers)
 */

import com.jbidwatcher.queue.MQFactory;
import com.jbidwatcher.ui.auctionTableModel;
import com.jbidwatcher.ui.TableSorter;
import com.jbidwatcher.TimerHandler;
import com.jbidwatcher.util.Comparison;
import com.jbidwatcher.FilterManager;
import com.jbidwatcher.auction.server.AuctionServerManager;
import com.jbidwatcher.util.ErrorManagement;

/**
 *  This class shouldn't have a 'TableSorter', it should defer to some
 *  sort of AuctionUI model class, which would have the sorter.  That
 *  would move the 'change notification' out of the hands of this
 *  class into a class more familiar with Swing notifications and
 *  such.  To prove this, you can note that only the UI class for this
 *  actually calls getTableSorter(), which means it should be moved
 *  into that class instead of here.  So should the auctionTableModel,
 *  in fact.  We'd need to export the auctionVector, so a UI-specific
 *  class could build it's own atm and tablesorter.  --  BUGBUG
 */
public class Auctions implements TimerHandler.WakeupProcess {
  boolean _selling = false;
  boolean _complete = false;
  private volatile TableSorter _tSort;
  private String _name;
  private static volatile boolean isBlocked =false;
  private static final int LINE_LENGTH = 80;

  public Auctions(String inName) {
    _name = inName;
    _tSort = new TableSorter(inName, "Time left", new auctionTableModel());
  }

  //  Used to get the displayable name for this Auctions collection
  public String getName() {
    return _name;
  }

  public static void startBlocking() { isBlocked = true; }
  public static void endBlocking() { isBlocked = false; }
  public static boolean isBlocked() { return isBlocked; }

  //  A single accessor...
  public TableSorter getTableSorter() { return _tSort; }

  public void setSelling() { _selling = true; }
  public boolean isSelling() { return _selling; }
  public void setComplete() { _complete = true; }
  public boolean isCompleted() { return _complete; }

  //  Encapsulate the TableSorter calls that are needed
  public int getColumnCount() { return(_tSort.getColumnCount()); }
  public int getRowCount() { return(_tSort.getRowCount()); }
  public String getColumnName(int i) { return _tSort.getColumnName(i); }
  public int getColumnNumber(String colName) { return _tSort.getColumnNumber(colName); }

  /** 
   * Search for an AuctionEntry in our tables, given it's identifier.
   * 
   * @param whatIdentifier - The identifier to search for.
   * 
   * @return - The AuctionEntry, if it's found, or null if none was found.
   */
  public AuctionEntry getEntry(final String whatIdentifier) {
    Object o = _tSort.find(new Comparison() {
      public boolean match(Object o) {
        if (o instanceof AuctionEntry) {
          AuctionEntry ae = (AuctionEntry) o;
          if (whatIdentifier.equals(ae.getIdentifier())) {
            return true;
          }
        }
        return false;
      }
    });
    return (AuctionEntry) o;
  }

  public boolean update(AuctionEntry ae) {
    return _tSort.update(ae);
  }

  public void refilterAll(boolean clearCurrent) {
    for(int i=_tSort.getRowCount()-1; i>=0; i--) {
      AuctionEntry ae = (AuctionEntry) _tSort.getValueAt(i, -1);
      if (clearCurrent) {
        delEntry(ae);
      } else {
        ae.setCategory(null);
        FilterManager.getInstance().refilterAuction(ae, false);
      }
    }
  }

  //  Actual manipulation of the auction list is entirely handled here.

  /** 
   * Delete an auction entry, using that auction entry to match against.
   * This also tells the auction entry to unregister itself!
   * 
   * @param inEntry - The auction entry to delete.
   */
  public void delEntry(AuctionEntry inEntry) {
    boolean removedAny = _tSort.delete(inEntry);

    if(removedAny) {
      AuctionServerManager.getInstance().deleteEntry(inEntry);
    }
  }

  private static boolean manageDeleted(AuctionEntry ae) {
    if(AuctionsManager.getInstance().isDeleted(ae.getIdentifier())) {
      ErrorManagement.logDebug("Skipping previously deleted auction (" + ae.getIdentifier() + ").");
      return true;
    }
    return false;
  }

  /**
   * Add an AuctionEntry that has already been created, denying
   * duplicates, but allowing duplicates where both have useful
   * information that is not the same.
   * 
   * @param aeNew - The new auction entry to add to the tables.
   * 
   * @return - true if the auction was added, false if not.
   */
  public boolean addEntry(AuctionEntry aeNew) {
    if(aeNew == null) return true;
    if(manageDeleted(aeNew)) return false;

    boolean inserted = (_tSort.insert(aeNew) != -1);

    if (inserted) {
      AuctionServerManager.getInstance().addEntry(aeNew);
      return true;
    }
    ErrorManagement.logMessage("JBidWatch: Bad auction entry, cannot add!");

    return false;
  }

  /**
   * Verify that the auction provided exists.
   * 
   * @param auctionId - The auction ID to search for.
   * 
   * @return - true if the auction is in the list, false otherwise.
   */
  public boolean verifyEntry(String auctionId) {
    AuctionEntry ae = getEntry(auctionId);

    return (ae != null);
  }

  /** 
   * Verify that the auction provided exists.
   * 
   * @param ae - The auction entry to search for.
   * 
   * @return - true if the auction is in the list, false otherwise.
   */
  public boolean verifyEntry(final AuctionEntry ae) {
    Object result = _tSort.find(new Comparison() {
      public boolean match(Object o) { //noinspection ObjectEquality
        return o == ae; }
    });
    return result != null;
  }

  /**
   * Determine if any snipes are pending for any of the auctions
   * handled by this instance.
   * 
   * @return - True if any of the auctions under this instance have a
   * pending snipe.
   */
  public boolean anySnipes() {
    Object result = _tSort.find(new Comparison() {
      public boolean match(Object o) {

        AuctionEntry ae = ((AuctionEntry) o);
        return ae.isSniped() && !ae.isComplete();
      }
    });
    return result != null;
  }

  /** 
   * For display during updates, we want the title and potentially the
   * comment, to display all that in the status bar while we're
   * updating.
   * 
   * @param ae - The auction to retrieve that display information from.
   * 
   * @return - A string containing the title alone, if no comment, or
   * in the format: "title (comment)" otherwise.
   */
  private static String getTitleAndComment(AuctionEntry ae) {
    String curComment = ae.getComment();
    if(curComment == null) return ae.getTitle();

    StringBuffer titleString = new StringBuffer(" (");
    titleString.append(ae.getTitle()).append(')');

    return titleString.toString();
  }

  /** 
   * It's time to update, so show that we're updating this auction,
   * update it, filter it to see if it needs to move (i.e. is
   * completed), and then let the user know we finished.
   * 
   * @param ae - The auction to update.
   * @return - true if the auction was moved to another category, false otherwise.
   */
  private boolean doUpdate(AuctionEntry ae) {
    String titleWithComment = getTitleAndComment(ae);

    if(!ae.isComplete() || ae.isUpdateForced()) {
      MQFactory.getConcrete("Swing").enqueue("Updating " + titleWithComment);
      ae.update();
      Auctions newAuction = FilterManager.getInstance().refilterAuction(ae, false);
      if(newAuction == null) {
        MQFactory.getConcrete("Swing").enqueue("Done updating " + titleWithComment);
      } else {
        MQFactory.getConcrete("Swing").enqueue(new StringBuffer(LINE_LENGTH).append("Moved to ").append(newAuction.getName()).append(' ').append(titleWithComment).toString());
        return true;
      }
    }
    return false;
  }

  /** 
   * Iterate over the auctions, starting from the last one we checked,
   * and check if it's time to update that auction.  If it's a forced
   * update, we will always change the display.  Basically, this
   * function checks each auction until it finds one it needs to
   * update, then it updates, and returns.
   * 
   * @return - True if any updating occured, false otherwise.
   */
  private boolean doNextUpdate() {
    AuctionEntry result = (AuctionEntry) _tSort.find(new Comparison() {
      public boolean match(Object o) { return ((AuctionEntry) o).checkUpdate(); }
    });
    if (result != null) {
      boolean forcedUpdate = result.isUpdateForced();

      if(doUpdate(result) || forcedUpdate) {
        _tSort.sort();
      }
    }
    return result != null;
  }

  /**
   * Check all snipes, then check up to one auction to update.  Snipes
   * are more important, and should be checked in toto every second.
   * Updates can wait a few seconds, based on the number of other
   * auctions that need updates at the exact same time.  Over time,
   * this will spread out auction update times, so they don't collide.
   * If NO auctions were updated, set back to the start of the list of
   * auctions to check.
   * 
   * @return - true if an update occurred or the display needs to be
   * refreshed, either because of a snipe or an auction update ocurred.
   * False if nothing has happened.
   */
  public boolean check() {
    //  Don't allow updates to interfere with sniping.
    if(isBlocked) return false;
    return doNextUpdate();
  }

  public void updateTime() {
    _tSort.updateTime();
  }
}
