package com.jbidwatcher.ui;
/*
 * Copyright (c) 2000-2007, CyberFOX Software, Inc. All Rights Reserved.
 *
 * Developed by mrs (Morgan Schweers)
 */

import com.jbidwatcher.util.config.JConfig;
import com.jbidwatcher.util.queue.MessageQueue;
import com.jbidwatcher.util.queue.MQFactory;
import com.jbidwatcher.util.config.ErrorManagement;
import com.jbidwatcher.auction.AuctionEntry;
import com.jbidwatcher.auction.Auctions;

import java.util.*;
import java.awt.Color;

public class FilterManager implements MessageQueue.Listener {
  private static FilterManager sInstance = null;
  private static final ListManager mList = ListManager.getInstance();
  private AuctionListHolder _main = null;
  private Map<AuctionEntry, Auctions> mAllOrderedAuctionEntries;

  private FilterManager() {
    //  Sorted by the 'natural order' of AuctionEntries.
    mAllOrderedAuctionEntries = new TreeMap<AuctionEntry, Auctions>();

    MQFactory.getConcrete("redraw").registerListener(this);

    MQFactory.getConcrete("delete").registerListener(new MessageQueue.Listener() {
      public void messageAction(Object deQ) {
        AuctionEntry ae = (AuctionEntry) deQ;
        deleteAuction(ae);
      }
    });
  }

  public void loadFilters() {
    //  BUGBUG -- Hardcoded for now, make dynamic later (post 0.8 release).
    mList.add(_main = new AuctionListHolder("current", false, false, false));
    mList.add(new AuctionListHolder("complete", true, false, false));
    mList.add(new AuctionListHolder("selling", false, true, false));

    String tabName;
    int i = 1;

    do {
      tabName = JConfig.queryDisplayProperty("tabs.name." + i++);
      if (tabName != null) {
        mList.add(new AuctionListHolder(tabName));
      }
    } while (tabName != null);
  }

  public Auctions addTab(String newTab) {
    Color mainBackground = _main.getUI().getBackground();
    Properties dispProps = new Properties();
    _main.getUI().getColumnWidthsToProperties(dispProps, newTab);
    JConfig.addAllToDisplay(dispProps);
    AuctionListHolder newList = new AuctionListHolder(newTab, mainBackground);
    mList.add(newList);
    return newList.getList();
  }

  /**  This is a singleton class, it needs an accessor.
   *
   * @return - The singleton instance of this class.
   */
  public synchronized static FilterManager getInstance() {
    if(sInstance == null) {
      sInstance = new FilterManager();
    }
    return sInstance;
  }

  public void messageAction(Object deQ) {
    if(deQ instanceof AuctionEntry) {
      AuctionEntry ae = (AuctionEntry) deQ;
      Auctions newAuction = refilterAuction(ae);
      if (newAuction != null) {
        MQFactory.getConcrete("Swing").enqueue("Moved to " + newAuction.getName() + " " + Auctions.getTitleAndComment(ae));
        mList.matchUI(newAuction).redrawAll();
      } else {
        mList.redrawEntry(ae);
      }
    } else if(deQ instanceof Auctions) {
      mList.matchUI((Auctions)deQ).sort();
    } else if(deQ instanceof Color) {
      mList.setBackground((Color)deQ);
    }
  }

  /** Delete an auction from the Auctions list that it's in.
   *
   * @param ae - The auction to delete.
   */
  public void deleteAuction(AuctionEntry ae) {
    Auctions whichAuctionCollection = mAllOrderedAuctionEntries.get(ae);

    if(whichAuctionCollection == null) whichAuctionCollection = mList.whereIsAuction(ae);

    if(whichAuctionCollection != null) {
      mList.matchUI(whichAuctionCollection).delEntry(ae);
    }
    mAllOrderedAuctionEntries.remove(ae);
  }

  /**
  * Adds an auction entry to the appropriate Auctions list, based on
  * the loaded filters.
  *
  * @param ae - The auction to add.
  */
  public void addAuction(AuctionEntry ae) {
    Auctions whichAuctionCollection = mAllOrderedAuctionEntries.get(ae);

    if(whichAuctionCollection == null) {
      whichAuctionCollection = mList.whereIsAuction(ae);
      if(whichAuctionCollection != null) {
        mAllOrderedAuctionEntries.put(ae, whichAuctionCollection);
      }
    }

    if(whichAuctionCollection != null) {
      //  If it's already sorted into a Auctions list, tell that list
      //  to handle it.
      if(whichAuctionCollection.allowAddEntry(ae)) {
        mList.matchUI(whichAuctionCollection).addEntry(ae);
      }
    } else {
      Auctions newAuctionCollection = matchAuction(ae);

      //  If we have no auction collections, then this isn't relevant.
      if(newAuctionCollection != null) {
        if (newAuctionCollection.allowAddEntry(ae)) {
          mList.matchUI(newAuctionCollection).addEntry(ae);
          mAllOrderedAuctionEntries.put(ae, newAuctionCollection);
        }
      }
    }
  }

  public Iterator<AuctionEntry> getAuctionIterator() {
    return mAllOrderedAuctionEntries.keySet().iterator();
  }

  /**
   * Currently auction entries can only be in one Auctions collection
   * at a time.  There MUST be a default auction being returned by
   * matchAuction.  It cannot return null right now.  BUGBUG.
   *
   * @param ae - The auction to locate the collection for.
   * @return - The collection currently holding the provided auction.
   */
  public Auctions matchAuction(AuctionEntry ae) {
    if (!ae.isSticky() || ae.getCategory() == null) {
      //  Hardcode seller and ended checks.
      if (ae.isSeller()) {
        return mList.findSellerList();
      }
      if (ae.isComplete()) {
        return mList.findCompletedList();
      }
    }
    String category = ae.getCategory();

    //  Now iterate over the auction Lists, looking for one named the
    //  same as the AuctionEntry's 'category'.
    Auctions rval = mList.findCategory(category);
    if (rval != null) {
      return rval;
    }

    if (category != null && !category.startsWith("New Search")) {
      return addTab(category);
    }

    return _main.getList();
  }

  /** Currently auction entries can only be in one Auctions collection
   * at a time.  This still searches all, just in case.  In truth, it
   * should keep a list of Auctions that each AuctionEntry is part of.
   * There MUST be a default auction being returned by matchAuction.
   * The list of auctions should be a Map, mapping AuctionEntry values
   * to a List of Auctions that it's part of.
   *
   * @param ae - The auction entry to refilter.
   *
   * @return an Auctions entry if it moved the auction somewhere else,
   * and null if it didn't find the auction, or it was in the same
   * filter as it was before.
   */
  public Auctions refilterAuction(AuctionEntry ae) {
    Auctions newAuctions = matchAuction(ae);
    Auctions oldAuctions = mAllOrderedAuctionEntries.get(ae);

    if(oldAuctions == null) {
      oldAuctions = mList.whereIsAuction(ae);
    }
    if(oldAuctions != null) {
      String tabName = oldAuctions.getName();
      if(newAuctions.isCompleted()) {
        String destination;
        if(ae.isBidOn() || ae.isSniped()) {
          if(ae.isHighBidder()) {
            destination = JConfig.queryConfiguration(tabName + ".won_target");
          } else {
            destination = JConfig.queryConfiguration(tabName + ".lost_target");
          }
        } else {
          destination = JConfig.queryConfiguration(tabName + ".other_target");
        }

        if(destination != null) {
          if(destination.equals("<delete>")) {
            deleteAuction(ae);
            return null;
          }

          ae.setSticky(true);
          newAuctions = mList.findCategory(destination);
          if(newAuctions == null) {
            newAuctions = addTab(destination);
          }
        }
      }
    }

    if(oldAuctions == newAuctions || oldAuctions == null) {
      if(oldAuctions == null) {
        ErrorManagement.logMessage("For some reason oldAuctions is null, and nobody acknowledges owning it, for auction entry " + ae.getTitle());
      }
      return null;
    }

    AuctionsUIModel oldUI = mList.matchUI(oldAuctions);
    AuctionsUIModel newUI = mList.matchUI(newAuctions);
    if(oldUI != null) oldUI.delEntry(ae);
    if(newUI != null) newUI.addEntry(ae);

    mAllOrderedAuctionEntries.put(ae,newAuctions);
    return newAuctions;
  }
}
