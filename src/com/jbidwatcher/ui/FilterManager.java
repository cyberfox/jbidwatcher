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
  private Map<AuctionEntry, AuctionListHolder> mAllOrderedAuctionEntries;
  private AuctionListHolder mMainTab = null;
  private AuctionListHolder mDefaultCompleteTab = null;
  private AuctionListHolder mDefaultSellingTab = null;

  private FilterManager() {
    //  Sorted by the 'natural order' of AuctionEntries.
    mAllOrderedAuctionEntries = new TreeMap<AuctionEntry, AuctionListHolder>();

    MQFactory.getConcrete("redraw").registerListener(this);

    MQFactory.getConcrete("delete").registerListener(new MessageQueue.Listener() {
      public void messageAction(Object deQ) {
        AuctionEntry ae = (AuctionEntry) deQ;
        deleteAuction(ae);
      }
    });
  }

  public void loadFilters() {
    mMainTab = mList.add(new AuctionListHolder("current", false, false, false));
    mDefaultCompleteTab = mList.add(new AuctionListHolder("complete", true, false, false));
    mDefaultSellingTab = mList.add(new AuctionListHolder("selling", false, true, false));

    String tabName;
    int i = 1;

    do {
      tabName = JConfig.queryDisplayProperty("tabs.name." + i++);
      if (tabName != null) {
        mList.add(new AuctionListHolder(tabName));
      }
    } while (tabName != null);
  }

  AuctionListHolder addTab(String newTab) {
    Color mainBackground = mMainTab.getUI().getBackground();
    Properties dispProps = new Properties();
    mMainTab.getUI().getColumnWidthsToProperties(dispProps, newTab);
    JConfig.addAllToDisplay(dispProps);
    AuctionListHolder newList = new AuctionListHolder(newTab, mainBackground);
    mList.add(newList);
    return newList;
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
      AuctionListHolder old = mAllOrderedAuctionEntries.get(ae);
      AuctionListHolder newAuction = refilterAuction(ae);
      if (newAuction != null) {
        MQFactory.getConcrete("Swing").enqueue("Moved to " + newAuction.getList().getName() + " " + Auctions.getTitleAndComment(ae));
        if(old != null) old.getUI().redrawAll();
        newAuction.getUI().redrawAll();
      } else {
        JTabManager.getInstance().getCurrentTable().update(ae);
//        mList.redrawEntry(ae);
      }
    } else if(deQ instanceof String) {
      AuctionListHolder toSort = mList.findCategory((String)deQ);
      if(toSort != null) toSort.getUI().sort();
    } else if(deQ instanceof Color) {
      mList.setBackground((Color)deQ);
    }
  }

  /** Delete an auction from the Auctions list that it's in.
   *
   * @param ae - The auction to delete.
   */
  public void deleteAuction(AuctionEntry ae) {
    AuctionListHolder which = mAllOrderedAuctionEntries.get(ae);
    if(which == null) which = mList.whereIsAuction(ae);
    if(which != null) which.getUI().delEntry(ae);

    mAllOrderedAuctionEntries.remove(ae);
  }

  /**
  * Adds an auction entry to the appropriate Auctions list, based on
  * the loaded filters.
  *
  * @param ae - The auction to add.
  */
  public void addAuction(AuctionEntry ae) {
    AuctionListHolder which = mAllOrderedAuctionEntries.get(ae);

    if(which == null) {
      which = mList.whereIsAuction(ae);
      if(which != null) {
        mAllOrderedAuctionEntries.put(ae, which);
      }
    }

    if(which != null) {
      //  If it's already sorted into a Auctions list, tell that list
      //  to handle it.
      if(which.getList().allowAddEntry(ae)) {
        which.getUI().addEntry(ae);
      }
    } else {
      AuctionListHolder sendTo = matchAuction(ae);

      //  If we have no auction collections, then this isn't relevant.
      if(sendTo != null) {
        if (sendTo.getList().allowAddEntry(ae)) {
          sendTo.getUI().addEntry(ae);
          mAllOrderedAuctionEntries.put(ae, sendTo);
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
   * matchAuction.  It cannot return null right now.
   *
   * @param ae - The auction to locate the collection for.
   * @return - The collection currently holding the provided auction.
   */
  private AuctionListHolder matchAuction(AuctionEntry ae) {
    if (!ae.isSticky() || ae.getCategory() == null) {
      //  Hardcode seller and ended checks.
      if (ae.isSeller()) return mDefaultSellingTab;
      if (ae.isComplete()) return mDefaultCompleteTab;
    }
    String category = ae.getCategory();

    //  Now iterate over the auction Lists, looking for one named the
    //  same as the AuctionEntry's 'category'.
    AuctionListHolder rval = mList.findCategory(category);
    if (rval != null) return rval;

    if (category != null && !category.startsWith("New Search")) return addTab(category);

    return mMainTab;
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
  private AuctionListHolder refilterAuction(AuctionEntry ae) {
    AuctionListHolder sendTo = matchAuction(ae);
    AuctionListHolder old = mAllOrderedAuctionEntries.get(ae);

    if(old == null) old = mList.whereIsAuction(ae);

    if(old != null) {
      String tabName = old.getList().getName();
      if(sendTo.getList().isCompleted()) {
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
          sendTo = mList.findCategory(destination);
          if(sendTo == null) sendTo = addTab(destination);
        }
      }
    }

    if(old == sendTo || old == null) {
      if(old == null) {
        ErrorManagement.logMessage("For some reason oldAuctions is null, and nobody acknowledges owning it, for auction entry " + ae.getTitle());
      }
      return null;
    }

    AuctionsUIModel oldUI = old.getUI();
    AuctionsUIModel newUI = sendTo.getUI();
    if(oldUI != null) oldUI.delEntry(ae);
    if(newUI != null) newUI.addEntry(ae);

    mAllOrderedAuctionEntries.put(ae, sendTo);
    return sendTo;
  }
}
