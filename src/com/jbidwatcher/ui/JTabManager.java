package com.jbidwatcher.ui;
/*
 * Copyright (c) 2000-2007, CyberFOX Software, Inc. All Rights Reserved.
 *
 * Developed by mrs (Morgan Schweers)
 */

import com.jbidwatcher.auction.AuctionEntry;
import com.jbidwatcher.queue.MQFactory;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.event.ActionEvent;
import java.util.Map;
import java.util.TreeMap;

public class JTabManager extends JBidMouse {
  private JTabbedPane mAuctionTypes;
  private Map<String, TableSorter> mNameTableMap = new TreeMap<String, TableSorter>();
  private JTabPopupMenu mPopupMenu;

  public JTabManager() {
    mAuctionTypes = new JTabbedPane();
    mPopupMenu = new JTabPopupMenu(mAuctionTypes);
    mAuctionTypes.addMouseListener(mPopupMenu);
    mAuctionTypes.addChangeListener(new ChangeListener() {
      // This method is called whenever the selected tab changes
      public void stateChanged(ChangeEvent evt) {
        // Get current tab
        TableSorter ts = getCurrentTable();
        if(ts != null) ts.sort();
      }
    });
  }

  public JMenu getCustomColumnMenu() {
    return mPopupMenu.getCustomizeMenu();
  }

  public JTabbedPane getTabs() {
    return mAuctionTypes;
  }

  public void add(String tabName, JComponent tabComponent, TableSorter inTS) {
    mAuctionTypes.add(tabName, tabComponent);
    mNameTableMap.put(tabName, inTS);
  }

  private TableSorter getCurrentTable() {
    int currentIndex = mAuctionTypes.getSelectedIndex();
    String currentTitle = mAuctionTypes.getTitleAt(currentIndex);

    return(mNameTableMap.get(currentTitle));
  }

  protected int[] getPossibleRows() {
    return(getCurrentTable().getSelectedRows());
  }

  private class mySelector implements Selector {
    private String _search;

    mySelector(String s) {
      _search = s;
    }

    public boolean select(JTable inTable) {
      String trueSearch = _search;
      boolean foundOne = false;
      boolean match, invert = false;
      boolean comment_t = false, seller_t = false, buyer_t = false, all_t = false;
      AuctionEntry ae;

      if (trueSearch.startsWith("~!")) {
        invert = true;
        trueSearch = trueSearch.substring(2);
        if (trueSearch.startsWith(" ")) trueSearch = trueSearch.substring(1);
      }

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

      for (int i = 0; i < inTable.getRowCount(); i++) {
        match = false;
        ae = (AuctionEntry) inTable.getValueAt(i, -1);

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

  public void actionPerformed(ActionEvent ae) {
    AuctionEntry whichAuction = null;
    String actionString = ae.getActionCommand();
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

    DoAction(ae.getSource(), actionString, whichAuction);
  }

  public void sortDefault() {
    TableSorter ts = getCurrentTable();
    if (ts != null) {
      ts.enableInsertionSorting();
      ts.sort();
    }
  }
}
