package com.jbidwatcher.ui;
/*
 * Copyright (c) 2000-2007, CyberFOX Software, Inc. All Rights Reserved.
 *
 * Developed by mrs (Morgan Schweers)
 */

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.jbidwatcher.auction.AuctionEntry;
import com.jbidwatcher.util.queue.MQFactory;
import com.jbidwatcher.util.queue.PlainMessageQueue;
import com.jbidwatcher.ui.table.Selector;
import com.jbidwatcher.ui.table.TableSorter;
import com.jbidwatcher.ui.util.JMouseAdapter;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.event.ActionEvent;
import java.util.Map;
import java.util.TreeMap;

/**
 * A JTabManager which handles all the tabs into which are
 * rendered UI models.
 */
@Singleton
public class JTabManager extends JMouseAdapter {
  private JTabbedPane mAuctionTypes;
  private Map<String, TableSorter> mNameTableMap = new TreeMap<String, TableSorter>();
  private FilterInterface mFilter;

  /**
   * A little dependency injection; we use the filter manager to add in auction
   * entries which have gotten lost in the UI somehow, but we don't want to
   * directly reference it, or it becomes a dependency tangle. (A->B->C->D->A)
   *
   * @param filter - The filter manager for adding auction entries.
   */
  public void setFilterManager(FilterInterface filter) {
    mFilter = filter;
  }

  @Inject
  private JTabManager() {
    mAuctionTypes = new PlusTabbedPane();

    mAuctionTypes.addChangeListener(new ChangeListener() {
      // This method is called whenever the selected tab changes
      public void stateChanged(ChangeEvent evt) {
        // Get current tab
        TableSorter ts = getCurrentTable();
        if(ts != null) ts.sort();
      }
    });
  }

  public JTabbedPane getTabs() {
    return mAuctionTypes;
  }

  public void setTab(String tab) {
    int idx = mAuctionTypes.indexOfTab(tab);
    mAuctionTypes.setSelectedIndex(idx);
  }

  public void add(String tabName, JComponent tabComponent, TableSorter inTS) {
    mAuctionTypes.add(tabName, tabComponent);
    mNameTableMap.put(tabName, inTS);
  }

  public TableSorter getCurrentTable() {
    String title = getCurrentTableTitle();
    if(title == null) return null;
    return mNameTableMap.get(title);
  }

  public String getCurrentTableTitle() {
    int currentIndex = mAuctionTypes.getSelectedIndex();
    if (currentIndex == -1) return null;

    return mAuctionTypes.getTitleAt(currentIndex);
  }

  public int[] getPossibleRows() {
    return(getCurrentTable().getSelectedRows());
  }

  public void deselect() {
    TableSorter curTable = getCurrentTable();
    curTable.select(new ClearSelector());
  }

  public void showEntry(AuctionEntry found) {
    setTab(found.getCategory());
    selectBySearch("~n" + found.getIdentifier());
    int rows[] = getCurrentTable().getSelectedRows();
    if(rows.length == 0) {
      mFilter.addAuction(found);
      setTab(found.getCategory());
      selectBySearch("~n" + found.getIdentifier());
      rows = getCurrentTable().getSelectedRows();
    }
    getCurrentTable().getTable().scrollRectToVisible(getCurrentTable().getTable().getCellRect(rows[0], 1, true));
    getCurrentTable().getTable().requestFocus();
  }

  private static class ClearSelector implements Selector {
    public boolean select(JTable inTable) {
      inTable.clearSelection();
      return true;
    }
  }

  private class mySelector implements Selector
  {
    private String _search;

    mySelector(String s) {
      _search = s;
    }

    public boolean select(JTable inTable) {
      String trueSearch = _search;
      boolean invert = false;

      if (trueSearch.startsWith("~!")) {
        invert = true;
        trueSearch = trueSearch.substring(2);
        if (trueSearch.startsWith(" ")) trueSearch = trueSearch.substring(1);
      }

      boolean comment_t = false;
      boolean seller_t = false;
      boolean buyer_t = false;
      boolean all_t = false;
      boolean number_t = false;
      if (trueSearch.startsWith("~")) {
        if (trueSearch.startsWith("~a")) {
          comment_t = true;
          seller_t = true;
          buyer_t = true;
          all_t = true;
        }
        if (trueSearch.startsWith("~n")) number_t = true;
        if (trueSearch.startsWith("~b")) buyer_t = true;
        if (trueSearch.startsWith("~c")) comment_t = true;
        if (trueSearch.startsWith("~s")) seller_t = true;
        if (trueSearch.startsWith("~u")) {
          buyer_t = true;
          seller_t = true;
        }

        if (seller_t || buyer_t || comment_t || number_t) trueSearch = trueSearch.substring(2);
        if (trueSearch.startsWith(" ")) trueSearch = trueSearch.substring(1);
      }

      if(trueSearch.length() != 0) trueSearch = "(?i).*" + trueSearch + ".*";

      inTable.clearSelection();

      boolean foundOne = false;
      for (int i = 0; i < inTable.getRowCount(); i++) {
        boolean match = false;
        AuctionEntry ae = (AuctionEntry) inTable.getValueAt(i, -1);

        if (          seller_t) match = ae.getSellerName().matches(trueSearch);
        if (!match && buyer_t && ae.getHighBidder() != null) match = ae.getHighBidder().matches(trueSearch);
        if (!match && comment_t && ae.getComment() != null) match = ae.getComment().matches(trueSearch);
        if (!match && number_t) match = ae.getIdentifier().matches(trueSearch);
        //  If seller or buyer search was set, ignore the title / comments.
        if (!match && (all_t || (!seller_t && !buyer_t && !comment_t))) {
          match = ae.getTitle().matches(trueSearch);
        }
        if (invert) match = !match;
        if (match) {
          inTable.addRowSelectionInterval(i, i);
          foundOne = true;
        }
      }
      return foundOne;
    }
  }

  public void selectBySearch(String srch) {
    TableSorter curTable = getCurrentTable();
    Selector mySelector = new mySelector(srch);
    if(!curTable.select(mySelector)) {
      java.awt.Toolkit.getDefaultToolkit().beep();
      MQFactory.getConcrete("Swing").enqueue("No entries matched!");
    }
  }

  public Object getIndexedEntry(int i) {
    return getCurrentTable().getValueAt(i, -1);
  }

  public void actionPerformed(ActionEvent event) {
    AuctionEntry whichAuction = null;
    String actionString = event.getActionCommand();
    TableSorter chosenTable = getCurrentTable();
    boolean isButton = false;

    if(actionString.startsWith("BT-")) {
      actionString = actionString.substring(3);
      isButton = true;
    }

    if(chosenTable != null) {
      if(!isButton) {
        whichAuction = (AuctionEntry)chosenTable.getObjectAt(this.getPopupX(), this.getPopupY());
      } else {
        int temp[] = chosenTable.getSelectedRows();
        if(temp.length == 0) {
          whichAuction = null;
        } else {
          whichAuction = (AuctionEntry)chosenTable.getValueAt(temp[0], -1);
        }
      }
    }

    ((PlainMessageQueue)MQFactory.getConcrete("user")).enqueueObject(new ActionTriple(event.getSource(), actionString, whichAuction));
  }

  public void sortDefault() {
    TableSorter ts = getCurrentTable();
    if (ts != null) {
      ts.enableInsertionSorting();
      ts.sort();
    }
  }

  public void updateTime() {
    TableSorter ts = getCurrentTable();
    if(ts != null) ts.updateTime();
  }
}
