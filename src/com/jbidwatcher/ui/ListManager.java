package com.jbidwatcher.ui;

import com.jbidwatcher.util.config.JConfig;
import com.jbidwatcher.util.queue.MQFactory;
import com.jbidwatcher.auction.AuctionEntry;
import com.jbidwatcher.auction.Auctions;

import java.util.List;
import java.util.Collections;
import java.util.ArrayList;
import java.util.Properties;
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
  private static ListManager sInstance;

  private ListManager() {
    mAllLists = Collections.synchronizedList(new ArrayList<AuctionListHolder>(3));
  }

  public boolean toggleField(String tabName, String field) {
    synchronized (mAllLists) {
      for (int i = mAllLists.size() - 1; i >= 0; i--) {
        AuctionListHolder step = mAllLists.get(i);
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
    synchronized (mAllLists) {
      for (int i = mAllLists.size() - 1; i >= 0; i--) {
        AuctionListHolder step = mAllLists.get(i);
        if (step.getList().getName().equals(tabName)) {
          return step.getUI().getColumns();
        }
      }
    }
    return null;
  }

  public boolean exportTab(String tabName, String fname) {
    synchronized (mAllLists) {
      for (int i = mAllLists.size() - 1; i >= 0; i--) {
        AuctionListHolder step = mAllLists.get(i);
        if (step.getList().getName().equals(tabName)) {
          return step.getUI().export(fname);
        }
      }
    }
    return false;
  }

  public void redrawEntry(AuctionEntry ae) {
    synchronized (mAllLists) {
      for (AuctionListHolder step : mAllLists) {
        if (step.getUI().redrawEntry(ae)) return;
      }
    }
  }

  public void check() {
    synchronized (mAllLists) {
      for (AuctionListHolder step : mAllLists) {
        if (!step.getList().isCompleted()) {
          step.getUI().redraw();
        }
      }
    }
  }

  AuctionsUIModel matchUI(Auctions list) {
    synchronized (mAllLists) {
      for (AuctionListHolder step : mAllLists) {
        if (step.getList() == list) return step.getUI();
      }
    }
    return null;
  }

  Auctions findSellerList() {
    synchronized (mAllLists) {
      for (AuctionListHolder step : mAllLists) {
        if (step.getList().isSelling()) return step.getList();
      }
    }
    return null;
  }

  Auctions findCompletedList() {
    synchronized (mAllLists) {
      for (AuctionListHolder step : mAllLists) {
        if (step.getList().isCompleted()) return step.getList();
      }
    }
    return null;
  }

  /**
   * @param categoryName - The category name / tab name to look for.
   * @return - An Auctions list that matches the category name.
   * @brief Find what category (i.e. tab) an auction entry belongs to.
   */
  public Auctions findCategory(String categoryName) {
    if (categoryName == null) return null;

    synchronized (mAllLists) {
      for (AuctionListHolder step : mAllLists) {
        if (categoryName.equals(step.getList().getName())) return step.getList();
      }
    }
    return null;
  }

  public List<String> allCategories() {
    List<String> resultSet = null;

    synchronized (mAllLists) {
      for (AuctionListHolder step : mAllLists) {
        if (resultSet == null) resultSet = new ArrayList<String>();
        resultSet.add(step.getList().getName());
      }
    }

    return resultSet;
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

  public Auctions whereIsAuction(AuctionEntry ae) {
    synchronized (mAllLists) {
      for (AuctionListHolder step : mAllLists) {
        if (step.getList().verifyEntry(ae)) return step.getList();
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

  public boolean printTab(String tabName) {
    synchronized (mAllLists) {
      for (int i = mAllLists.size() - 1; i >= 0; i--) {
        AuctionListHolder step = mAllLists.get(i);
        if (step.getList().getName().equals(tabName)) {
          step.getUI().print();
          return true;
        }
      }
    }
    return false;
  }

  public Component deleteTab(String oldTab, boolean deleteFirst) {
    synchronized (mAllLists) {
      for (int i = mAllLists.size() - 1; i >= 0; i--) {
        AuctionListHolder step = mAllLists.get(i);
        if (step.getList().getName().equals(oldTab)) {
          if (step.isDeletable()) {
            mAllLists.remove(i);
            removeAuctionsFromTab(deleteFirst, step);
            return step.getUI().getPanel();
          }
        }
      }
    }

    return null;
  }

  private void removeAuctionsFromTab(boolean deleteFirst, AuctionListHolder step) {
    List<AuctionEntry> auctionList = step.getList().getAuctions();
    for (AuctionEntry ae : auctionList) {
      if (deleteFirst) {
        step.getUI().delEntry(ae);
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
    synchronized (mAllLists) {
      return mAllLists.size();
    }
  }

  public Auctions getList(int index) {
    AuctionListHolder ret;
    synchronized (mAllLists) {
      ret = mAllLists.get(index);
    }
    return ret.getList();
  }

  public void add(AuctionListHolder newList) {
    synchronized (mAllLists) {
      mAllLists.add(newList);
    }
  }

  public static ListManager getInstance() {
    if (sInstance == null) {
      sInstance = new ListManager();
    }
    return sInstance;
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
