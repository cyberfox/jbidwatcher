package com.jbidwatcher.ui;

import com.jbidwatcher.util.config.JConfig;
import com.jbidwatcher.util.queue.MQFactory;
import com.jbidwatcher.auction.AuctionEntry;
import com.jbidwatcher.auction.Auctions;

import java.util.*;
import java.awt.Color;
import java.awt.Component;

/**
 * Created by IntelliJ IDEA.
 * User: Morgan
 * Date: Jun 30, 2008
 * Time: 1:29:56 AM
 *
 * Trying to split up the list management code from FilterManager's other duties.
 */
public class ListManager {
  private final List<AuctionListHolder> mAllLists;
  private final Map<String, AuctionListHolder> mCategoryMap;
  private static ListManager sInstance = null;

  private ListManager() {
    mAllLists = Collections.synchronizedList(new ArrayList<AuctionListHolder>(3));
    mCategoryMap = new HashMap<String, AuctionListHolder>();
  }

  public boolean toggleField(String tabName, String field) {
    boolean visible = false;
    AuctionListHolder result = mCategoryMap.get(tabName);
    if(result != null) {
      visible = result.getUI().toggleField(field);
      if (!visible) JConfig.killDisplay(tabName + '.' + field);
    }
    return visible;
  }

  public List<String> getColumns(String tabName) {
    AuctionListHolder result = mCategoryMap.get(tabName);

    return result == null ? null : result.getUI().getColumns();
  }

  public boolean exportTab(String tabName, String fname) {
    AuctionListHolder result = mCategoryMap.get(tabName);

    return result != null && result.getUI().export(fname);
  }

  /**
   * @param categoryName - The category name / tab name to look for.
   * @return - An Auctions list that matches the category name.
   * @brief Find what category (i.e. tab) an auction entry belongs to.
   */
  AuctionListHolder findCategory(String categoryName) {
    return mCategoryMap.get(categoryName);
  }

  public List<String> allCategories() {
    return new ArrayList<String>(mCategoryMap.keySet());
  }

  public boolean printTab(String tabName) {
    AuctionListHolder result = mCategoryMap.get(tabName);

    if(result != null) result.getUI().print();
    return result != null;
  }

  public Component deleteTab(String tabName, boolean deleteFirst) {
    AuctionListHolder result = mCategoryMap.get(tabName);
    if (result != null && result.isDeletable()) {
      mAllLists.remove(result);
      mCategoryMap.remove(tabName);
      removeAuctionsFromTab(deleteFirst, result);
      return result.getUI().getPanel();
    }

    return null;
  }

  private void removeAuctionsFromTab(boolean deleteFirst, AuctionListHolder holder) {
    List<AuctionEntry> auctionList = holder.getList().getAuctions();
    for (AuctionEntry ae : auctionList) {
      if (deleteFirst) {
        holder.getUI().delEntry(ae);
      } else {
        ae.setCategory(null);
        MQFactory.getConcrete("redraw").enqueue(ae);
      }
    }
  }

  public Properties extractProperties(Properties outProps) {
    synchronized (mAllLists) {
      for (int i = 0; i < mAllLists.size(); i++) {
        AuctionListHolder step = mAllLists.get(i);

        // getSortProperties must be called first in order to restore original column names
        step.getUI().getTableSorter().getSortProperties(step.getList().getName(), outProps);
        step.getUI().getColumnWidthsToProperties(outProps);

        String tab = step.getList().getName();
        if (i > 2) {
          outProps.setProperty("tabs.name." + (i - 2), tab);
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
    return mAllLists.size();
  }

  AuctionListHolder add(AuctionListHolder newList) {
    mAllLists.add(newList);
    mCategoryMap.put(newList.getList().getName(), newList);
    return newList;
  }

  public static ListManager getInstance() {
    if (sInstance == null) {
      sInstance = new ListManager();
    }
    return sInstance;
  }

  /**
   * Sets the background color for all tabs to the passed in color.
   *
   * @param bgColor - The color to set the background to.
   */
  public void setBackground(Color bgColor) {
    synchronized (mAllLists) {
      for (AuctionListHolder step : mAllLists) {
        step.getUI().setBackground(bgColor);
      }
    }
  }

  public void redrawAll() {
    synchronized (mAllLists) {
      for (AuctionListHolder step : mAllLists) {
        if (!step.getList().isCompleted()) {
          step.getUI().redraw();
        }
      }
    }
  }

  public void redrawEntry(AuctionEntry ae) {
    synchronized (mAllLists) {
      for (AuctionListHolder step : mAllLists) {
        if (step.getUI().redrawEntry(ae)) return;
      }
    }
  }

  AuctionListHolder whereIsAuction(AuctionEntry ae) {
    synchronized (mAllLists) {
      for (AuctionListHolder step : mAllLists) {
        if (step.getList().verifyEntry(ae)) return step;
      }
    }

    return null;
  }

  public Auctions whereIsAuction(String aucId) {
    synchronized (mAllLists) {
      for (AuctionListHolder step : mAllLists) {
        if (step.getList().verifyEntry(aucId)) return step.getList();
      }
    }

    return null;
  }

  public boolean checkEachList() {
    boolean retval = false;

    synchronized(mAllLists) {
      for(AuctionListHolder list : mAllLists) {
        if(list.getList().check()) retval = true;
      }
    }

    return retval;
  }
}
