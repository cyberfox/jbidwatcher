package com.jbidwatcher.auction;

/*
 * Copyright (c) 2000-2007, CyberFOX Software, Inc. All Rights Reserved.
 *
 * Developed by mrs (Morgan Schweers)
 */

import com.jbidwatcher.util.queue.MQFactory;
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
public class Auctions {
  boolean _complete = false;
//  private volatile TableSorter _tSort;
  private AuctionList mList;
  private String _name;

  public Auctions(EntryCorral entryCorral, String inName) {
    _name = inName;
    mList = new AuctionList(entryCorral);
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

  public void each(Task task) {
    mList.each(task);
  }
}
