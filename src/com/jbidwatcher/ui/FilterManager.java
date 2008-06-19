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
  private static FilterManager _instance = null;

  private AuctionListHolder _main = null;
  private final List<AuctionListHolder> _allLists;
  private Map<AuctionEntry, Auctions> _allOrderedAuctionEntries;

  private FilterManager() {
    //  Sorted by the 'natural order' of AuctionEntries.
    _allOrderedAuctionEntries = new TreeMap<AuctionEntry, Auctions>();

    _allLists = Collections.synchronizedList(new ArrayList<AuctionListHolder>(3));
  }

  public boolean toggleField(String tabName, String field) {
    synchronized(_allLists) {
      for (int i = _allLists.size() - 1; i >= 0; i--) {
        AuctionListHolder step = _allLists.get(i);
        if (step.getList().getName().equals(tabName)) {
          boolean visible = step.getUI().toggleField(field);
          if (!visible) JConfig.killDisplay(tabName + '.' + field);
          return visible;
        }
      }
    }
    return false;
  }

  public List<String> getColumns(String tabName) {
    synchronized (_allLists) {
      for (int i = _allLists.size() - 1; i >= 0; i--) {
        AuctionListHolder step = _allLists.get(i);
        if (step.getList().getName().equals(tabName)) {
          return step.getUI().getColumns();
        }
      }
    }
    return null;
  }

  public boolean exportTab(String tabName, String fname) {
    synchronized (_allLists) {
      for(int i=_allLists.size()-1; i>=0; i--) {
        AuctionListHolder step = _allLists.get(i);
        if (step.getList().getName().equals(tabName)) {
          return step.getUI().export(fname);
        }
      }
    }
    return false;
  }

  private class AuctionListHolder {
    private Auctions auctionList;
    private AuctionsUIModel auctionUI;
    private boolean mDeletable = true;

    public boolean isDeletable() {
      return mDeletable;
    }

    public void setDeletable(boolean deletable) {
      mDeletable = deletable;
    }

    AuctionListHolder(String name) {
      this(name, false, false, true);
    }

    AuctionListHolder(String name, Color presetBackground) {
      this(name, false, false, true);
      auctionUI.setBackground(presetBackground);
    }

    AuctionListHolder(String name, boolean _completed, boolean _selling, boolean deletable) {
      auctionList = new Auctions(name);
      if(_completed) auctionList.setComplete();
      if(_selling) auctionList.setSelling();
      auctionUI = new AuctionsUIModel(auctionList);
      mDeletable = deletable;
    }

    public Auctions getList() { return auctionList; }
    public AuctionsUIModel getUI() { return auctionUI; }
  }

  public void loadFilters() {
    //  BUGBUG -- Hardcoded for now, make dynamic later (post 0.8 release).
    _main = new AuctionListHolder("current");
    _main.setDeletable(false);
    synchronized (_allLists) {
      _allLists.add(_main);

      _allLists.add(new AuctionListHolder("complete", true, false, false));
      _allLists.add(new AuctionListHolder("selling", false, true, false));

      String tabName;
      int i = 1;

      do {
        tabName = JConfig.queryDisplayProperty("tabs.name." + i++);
        if (tabName != null) {
          _allLists.add(new AuctionListHolder(tabName));
        }
      } while (tabName != null);
    }
  }

  public AuctionListHolder addTab(String newTab) {
    Color mainBackground = _main.getUI().getBackground();
    Properties dispProps = new Properties();
    _main.getUI().getColumnWidthsToProperties(dispProps, newTab);
    JConfig.addAllToDisplay(dispProps);
    AuctionListHolder newList = new AuctionListHolder(newTab, mainBackground);
    synchronized (_allLists) {
      _allLists.add(newList);
    }
    return newList;
  }

  public boolean printTab(String tabName) {
    synchronized (_allLists) {
      for(int i=_allLists.size()-1; i>=0; i--) {
        AuctionListHolder step = _allLists.get(i);
        if (step.getList().getName().equals(tabName)) {
          step.getUI().print();
          return true;
        }
      }
    }
    return false;
  }

  public boolean deleteTab(String oldTab, boolean deleteFirst) {
    boolean didRemove = false;

    synchronized (_allLists) {
      for(int i=_allLists.size()-1; i>=0; i--) {
        AuctionListHolder step = _allLists.get(i);
        if (step.getList().getName().equals(oldTab)) {
          if (!step.isDeletable()) return false;
          _allLists.remove(i);
          removeAuctionsFromTab(deleteFirst, step);
          AuctionsUIModel.getTabManager().getTabs().remove(i);
          didRemove = true;
        }
      }
    }
    return didRemove;
  }

  private void removeAuctionsFromTab(boolean deleteFirst, AuctionListHolder step) {
    List<AuctionEntry> auctionList = step.getList().getAuctions();
    for (AuctionEntry ae : auctionList) {
      if (deleteFirst) {
        step.getUI().delEntry(ae);
      } else {
        ae.setCategory(null);
        refilterAuction(ae);
      }
    }
  }

  /**  This is a singleton class, it needs an accessor.
   *
   * @return - The singleton instance of this class.
   */
  public synchronized static FilterManager getInstance() {
    if(_instance == null) {
      _instance = new FilterManager();
      MQFactory.getConcrete("redraw").registerListener(_instance);
    }
    return _instance;
  }

  public void messageAction(Object deQ) {
    if(deQ instanceof AuctionEntry) {
      AuctionEntry ae = (AuctionEntry) deQ;
      Auctions newAuction = refilterAuction(ae);
      if (newAuction != null) {
        MQFactory.getConcrete("Swing").enqueue("Moved to " + newAuction.getName() + " " + Auctions.getTitleAndComment(ae));
        matchUI(newAuction).redrawAll();
      } else {
        redrawEntry(ae);
      }
    } else if(deQ instanceof Auctions) {
      matchUI((Auctions)deQ).sort();
    }
  }

  public void redrawEntry(AuctionEntry ae) {
    synchronized (_allLists) {
      for (AuctionListHolder step : _allLists) {
        if (step.getUI().redrawEntry(ae)) return;
      }
    }
  }

  public void check() {
    synchronized (_allLists) {
      for (AuctionListHolder step : _allLists) {
        if (!step.getList().isCompleted()) {
          step.getUI().redraw();
        }
      }
    }
  }

  /** Delete an auction from the Auctions list that it's in.
   *
   * @param ae - The auction to delete.
   */
  public void deleteAuction(AuctionEntry ae) {
    Auctions whichAuctionCollection = _allOrderedAuctionEntries.get(ae);

    if(whichAuctionCollection == null) whichAuctionCollection = whereIsAuction(ae);

    if(whichAuctionCollection != null) {
      matchUI(whichAuctionCollection).delEntry(ae);
    }
    _allOrderedAuctionEntries.remove(ae);
  }

  private AuctionsUIModel matchUI(Auctions list) {
    synchronized (_allLists) {
      for (AuctionListHolder step : _allLists) {
        if (step.getList() == list) return step.getUI();
      }
    }
    return null;
  }

  /**
  * Adds an auction entry to the appropriate Auctions list, based on
  * the loaded filters.
  *
  * @param ae - The auction to add.
  */
  public void addAuction(AuctionEntry ae) {
    Auctions whichAuctionCollection = _allOrderedAuctionEntries.get(ae);

    if(whichAuctionCollection == null) {
      whichAuctionCollection = whereIsAuction(ae);
      if(whichAuctionCollection != null) {
        _allOrderedAuctionEntries.put(ae, whichAuctionCollection);
      }
    }

    if(whichAuctionCollection != null) {
      //  If it's already sorted into a Auctions list, tell that list
      //  to handle it.
      if(whichAuctionCollection.allowAddEntry(ae)) {
        matchUI(whichAuctionCollection).addEntry(ae);
      }
    } else {
      Auctions newAuctionCollection = matchAuction(ae);

      //  If we have no auction collections, then this isn't relevant.
      if(newAuctionCollection != null) {
        if (newAuctionCollection.allowAddEntry(ae)) {
          matchUI(newAuctionCollection).addEntry(ae);
          _allOrderedAuctionEntries.put(ae, newAuctionCollection);
        }
      }
    }
  }

  public Iterator<AuctionEntry> getAuctionIterator() {
    return _allOrderedAuctionEntries.keySet().iterator();
  }

  private Auctions findSellerList() {
    synchronized (_allLists) {
      for (AuctionListHolder step : _allLists) {
        if (step.getList().isSelling()) return step.getList();
      }
    }
    return null;
  }

  private Auctions findCompletedList() {
    synchronized (_allLists) {
      for (AuctionListHolder step : _allLists) {
        if (step.getList().isCompleted()) return step.getList();
      }
    }
    return null;
  }

  /** 
   * @brief Find what category (i.e. tab) an auction entry belongs to.
   * 
   * @param categoryName - The category name / tab name to look for.
   * 
   * @return - An Auctions list that matches the category name.
   */
  public Auctions findCategory(String categoryName) {
    if(categoryName == null) return null;

    synchronized (_allLists) {
      for (AuctionListHolder step : _allLists) {
        if (categoryName.equals(step.getList().getName())) return step.getList();
      }
    }
    return null;
  }

  public List<String> allCategories() {
    List<String> resultSet = null;

    synchronized (_allLists) {
      for (AuctionListHolder step : _allLists) {
        if (resultSet == null) resultSet = new ArrayList<String>();
        resultSet.add(step.getList().getName());
      }
    }

    return resultSet;
  }

  /**
   * Currently auction entries can only be in one Auctions collection
   * at a time.  There MUST be a default auction being returned by
   * matchAuction.  It cannot return null right now.  BUGBUG.
   *
   * @param ae - The auction to locate the collection for.
   *
   * @return - The collection currently holding the provided auction.
   */
  public Auctions matchAuction(AuctionEntry ae) {
    if(!ae.isSticky() || ae.getCategory() == null) {
      //  Hardcode seller and ended checks.
      if(ae.isSeller()) {
        return findSellerList();
      }
      if(ae.isComplete()) {
        return findCompletedList();
      }
    }
    String category = ae.getCategory();

    //  Now iterate over the auction Lists, looking for one named the
    //  same as the AuctionEntry's 'category'.
    Auctions rval = findCategory(category);
    if(rval != null) {
      return rval;
    }

    if(category != null && !category.startsWith("New Search")) {
      return addTab(category).getList();
    }

    return _main.getList();
  }

  /** 
   * Sets the background color for all tabs to the passed in color.
   * 
   * @param bgColor - The color to set the background to.
   */
  public void setBackground(Color bgColor) {
    synchronized (_allLists) {
      for (AuctionListHolder step : _allLists) {
        step.getUI().setBackground(bgColor);
      }
    }
  }

  public Auctions whereIsAuction(AuctionEntry ae) {
    synchronized (_allLists) {
      for (AuctionListHolder step : _allLists) {
        if (step.getList().verifyEntry(ae)) return step.getList();
      }
    }

    return null;
  }

  public Auctions whereIsAuction(String aucId) {
    synchronized (_allLists) {
      for (AuctionListHolder step : _allLists) {
        if (step.getList().verifyEntry(aucId)) return step.getList();
      }
    }

    return null;
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
    Auctions oldAuctions = _allOrderedAuctionEntries.get(ae);

    if(oldAuctions == null) {
      oldAuctions = whereIsAuction(ae);
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
          newAuctions = findCategory(destination);
          if(newAuctions == null) {
            AuctionListHolder alh = addTab(destination);
            newAuctions = alh.getList();
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

    AuctionsUIModel oldUI = matchUI(oldAuctions);
    AuctionsUIModel newUI = matchUI(newAuctions);
    if(oldUI != null) oldUI.delEntry(ae);
    if(newUI != null) newUI.addEntry(ae);

    _allOrderedAuctionEntries.put(ae,newAuctions);
    return newAuctions;
  }

  public Properties extractProperties(Properties outProps) {
    synchronized (_allLists) {
      for(int i = 0; i<_allLists.size(); i++) {
        AuctionListHolder step = _allLists.get(i);

        // getSortProperties must be called first in order to restore original column names
        step.getUI().getTableSorter().getSortProperties(step.getList().getName(), outProps);
        step.getUI().getColumnWidthsToProperties(outProps);

        String tab = step.getList().getName();
        if(i > 2) {
          outProps.setProperty("tabs.name." + (i-2), tab);
        }

        String KEEP_ENDED = tab + ".end.keep";
        String DELETE_NOT_MY_BID = tab + ".end.delete.notmybid";
        String DELETE_NO_BIDS = tab + ".end.delete.nobids";
        String ARCHIVE = tab + ".archive";

        outProps.setProperty(KEEP_ENDED, JConfig.queryDisplayProperty(KEEP_ENDED, "unset"));
        outProps.setProperty(DELETE_NOT_MY_BID, JConfig.queryDisplayProperty(DELETE_NOT_MY_BID, "unset"));
        outProps.setProperty(DELETE_NO_BIDS, JConfig.queryDisplayProperty(DELETE_NO_BIDS, "unset"));
        outProps.setProperty(ARCHIVE, JConfig.queryDisplayProperty(ARCHIVE, "unset"));
      }
    }

    return outProps;
  }

  public int listLength() {
    synchronized (_allLists) {
      return _allLists.size();
    }
  }

  public Auctions getList(int index) {
    AuctionListHolder ret;
    synchronized (_allLists) {
      ret = _allLists.get(index);
    }
    return ret.getList();
  }
}
