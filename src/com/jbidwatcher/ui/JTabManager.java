package com.jbidwatcher.ui;
/*
 * Copyright (c) 2000-2007, CyberFOX Software, Inc. All Rights Reserved.
 *
 * Developed by mrs (Morgan Schweers)
 */

import com.jbidwatcher.auction.AuctionEntry;
import com.jbidwatcher.util.queue.MQFactory;
import com.jbidwatcher.ui.table.Selector;
import com.jbidwatcher.ui.table.TableSorter;
import com.jbidwatcher.ui.util.JMouseAdapter;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.event.ActionEvent;
import java.util.Map;
import java.util.TreeMap;

public class JTabManager extends JMouseAdapter {
  private JTabbedPane mAuctionTypes;
  private Map<String, TableSorter> mNameTableMap = new TreeMap<String, TableSorter>();
  private static JTabManager sInstance;

  /**
   * @brief Retrieve the tab manager which controls ALL the tabs that
   * are displaying UI models.
   *
   * @return A JTabManager which handles all the tabs into which are
   * rendered UI models.
   */
  public static JTabManager getInstance() {
    if(sInstance == null) {
      sInstance = new JTabManager();
    }
    return sInstance;
  }

  private JTabManager() {
    mAuctionTypes = new JTabbedPane();
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

  public void add(String tabName, JComponent tabComponent, TableSorter inTS) {
    mAuctionTypes.add(tabName, tabComponent);
    mNameTableMap.put(tabName, inTS);
  }

  public TableSorter getCurrentTable() {
    int currentIndex = mAuctionTypes.getSelectedIndex();
    if(currentIndex == -1) return null;

    String currentTitle = mAuctionTypes.getTitleAt(currentIndex);

    return(mNameTableMap.get(currentTitle));
  }

  protected int[] getPossibleRows() {
    return(getCurrentTable().getSelectedRows());
  }

  public void deselect() {
    TableSorter curTable = getCurrentTable();
    curTable.select(new ClearSelector());
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
      if (trueSearch.startsWith("~")) {
        if (trueSearch.startsWith("~a")) {
          comment_t = true;
          seller_t = true;
          buyer_t = true;
          all_t = true;
        }
        if (trueSearch.startsWith("~b")) buyer_t = true;
        if (trueSearch.startsWith("~c")) comment_t = true;
        if (trueSearch.startsWith("~s")) seller_t = true;
        if (trueSearch.startsWith("~u")) {
          buyer_t = true;
          seller_t = true;
        }

        if (seller_t || buyer_t || comment_t) trueSearch = trueSearch.substring(2);
        if (trueSearch.startsWith(" ")) trueSearch = trueSearch.substring(1);
      }

      if(trueSearch.length() != 0) trueSearch = "(?i).*" + trueSearch + ".*";

      inTable.clearSelection();

      boolean foundOne = false;
      for (int i = 0; i < inTable.getRowCount(); i++) {
        boolean match = false;
        AuctionEntry ae = (AuctionEntry) inTable.getValueAt(i, -1);

        if (          seller_t) match = ae.getSeller().matches(trueSearch);
        if (!match && buyer_t && ae.getHighBidder() != null) match = ae.getHighBidder().matches(trueSearch);
        if (!match && comment_t && ae.getComment() != null) match = ae.getComment().matches(trueSearch);
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

  protected Object getIndexedEntry(int i) {
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

    MQFactory.getConcrete("user").enqueue(new ActionTriple(event.getSource(), actionString, whichAuction));
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
