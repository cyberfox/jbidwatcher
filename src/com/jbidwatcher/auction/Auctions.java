package com.jbidwatcher.auction;

/*
 * Copyright (c) 2000-2007, CyberFOX Software, Inc. All Rights Reserved.
 *
 * Developed by mrs (Morgan Schweers)
 */

import com.jbidwatcher.my.My;
import com.jbidwatcher.util.queue.MQFactory;
import com.jbidwatcher.util.queue.TimerHandler;
import com.jbidwatcher.util.Comparison;
import com.jbidwatcher.util.UpdateBlocker;
import com.jbidwatcher.util.Task;
import com.jbidwatcher.util.xml.XMLInterface;

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
  boolean _complete = false;
//  private volatile TableSorter _tSort;
  private AuctionList mList;
  private String _name;

  public Auctions(String inName) {
    _name = inName;
    mList = new AuctionList();
  }

  public AuctionList getList() { return mList; }

  //  Used to get the displayable name for this Auctions collection
  public String getName() {
    return _name;
  }

  public void setComplete() { _complete = true; }
  public boolean isCompleted() { return _complete; }

  /**
   * Add an AuctionEntry that has already been created, denying
   * previously deleted items.
   * 
   * @param aeNew - The new auction entry to add to the tables.
   * 
   * @return - true if the auction is okay to be added, false if not.
   */
  public boolean allowAddEntry(EntryInterface aeNew) {
    return aeNew != null && !DeletedEntry.exists(aeNew.getIdentifier());
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
  public static String getTitleAndComment(AuctionEntry ae) {
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
   */
  private void doUpdate(AuctionEntry ae) {
    String titleWithComment = getTitleAndComment(ae);

    if(!ae.isComplete() || ae.isUpdateForced()) {
      MQFactory.getConcrete("Swing").enqueue("Updating " + titleWithComment);
      ae.setUpdating();
      MQFactory.getConcrete("redraw").enqueue(ae.getIdentifier());
      Thread.yield();
      XMLInterface before = ae.toXML(false);
      ae.update();
      XMLInterface after = ae.toXML(false);
      ae.clearUpdating();

      boolean changed = !(after.toString().equals(before.toString()));

      My status = My.findByIdentifier(ae.getIdentifier());
      if (status == null || status.getDate("last_synced_at") == null || changed) {
        MQFactory.getConcrete("upload").enqueue(ae.getIdentifier());
      }

      if(changed) {
        String category = ae.getCategory();
        MQFactory.getConcrete("redraw").enqueue(category);
      }
      MQFactory.getConcrete("redraw").enqueue(ae.getIdentifier());
      MQFactory.getConcrete("Swing").enqueue("Done updating " + Auctions.getTitleAndComment(ae));
    }
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
    AuctionEntry result = mList.find(new Comparison() {
      public boolean match(Object o) { return o != null && ((AuctionEntry) o).checkUpdate();  }
    });
    if (result != null) {
      boolean forcedUpdate = result.isUpdateForced();

      doUpdate(result);
      if(forcedUpdate) {
        MQFactory.getConcrete("redraw").enqueue(getName());
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
    return !(UpdateBlocker.isBlocked() || !doNextUpdate());
  }

  public void each(Task task) {
    mList.each(task);
  }
}
