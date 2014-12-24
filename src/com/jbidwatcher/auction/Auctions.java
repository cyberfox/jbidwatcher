package com.jbidwatcher.auction;

/*
 * Copyright (c) 2000-2007, CyberFOX Software, Inc. All Rights Reserved.
 *
 * Developed by mrs (Morgan Schweers)
 */

import com.jbidwatcher.util.Task;

public class Auctions {
  boolean _complete = false;
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
