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
import com.jbidwatcher.ui.util.JMouseAdapter;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.table.TableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
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
  private Map<String, JTable> mNameTableMap = new TreeMap<String, JTable>();
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
        JTable ts = getCurrentTable();
        if(ts != null) ((TableRowSorter<TableModel>)ts.getRowSorter()).sort();
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

  public void add(String tabName, JComponent tabComponent, JTable inTable) {
    mAuctionTypes.add(tabName, tabComponent);
    mNameTableMap.put(tabName, inTable);
  }

  public JTable getCurrentTable() {
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
    JTable curTable = getCurrentTable();
    curTable.clearSelection();
  }

  public void showEntry(AuctionEntry found) {
    setTab(found.getCategory());
    filterBySearch("~n" + found.getIdentifier());
    int rowCount = getCurrentTable().getRowCount();
    if(rowCount == 0) {
      mFilter.addAuction(found);
      setTab(found.getCategory());
      filterBySearch("~n" + found.getIdentifier());
    }
    getCurrentTable().requestFocus();
  }

  private class myFilter implements Selector {
    private String _search;

    myFilter(String s) {
      _search = s;
    }

    public boolean select(JTable inTable) {
      String trueSearch = _search;
      if(_search.isEmpty()) {
        ((TableRowSorter)inTable.getRowSorter()).setRowFilter(null);
      } else {
        final boolean invert;

        if (trueSearch.startsWith("~!")) {
          invert = true;
          trueSearch = trueSearch.substring(2);
          if (trueSearch.startsWith(" ")) trueSearch = trueSearch.substring(1);
        } else {
          invert = false;
        }

        final boolean comment_t;
        final boolean seller_t;
        final boolean buyer_t;
        final boolean all_t;
        final boolean number_t;

        all_t = trueSearch.startsWith("~a");

        number_t = trueSearch.startsWith("~n");
        comment_t = all_t || trueSearch.startsWith("~c");
        seller_t = all_t || trueSearch.startsWith("~s") || trueSearch.startsWith("~u");
        buyer_t = all_t || trueSearch.startsWith("~b") || trueSearch.startsWith("~u");

        if (seller_t || buyer_t || comment_t || number_t) trueSearch = trueSearch.substring(2);
        if (trueSearch.startsWith(" ")) trueSearch = trueSearch.substring(1);

        if (trueSearch.length() != 0) trueSearch = "(?i).*" + trueSearch + ".*";

        final String finalSearch = trueSearch;
        ((TableRowSorter) inTable.getRowSorter()).setRowFilter(new RowFilter() {
          @Override
          public boolean include(Entry entry) {
            AuctionEntry ae = (AuctionEntry) entry.getValue(-1);
            return isMatch(finalSearch, invert, comment_t, seller_t, buyer_t, all_t, number_t, ae);
          }
        });
      }

      return true;
    }

    private boolean isMatch(String trueSearch, boolean invert, boolean comment_t, boolean seller_t, boolean buyer_t, boolean all_t, boolean number_t, AuctionEntry ae) {
      boolean match = false;
      if (          seller_t) match = ae.getSellerName().matches(trueSearch);
      if (!match && buyer_t && ae.getHighBidder() != null) match = ae.getHighBidder().matches(trueSearch);
      if (!match && comment_t && ae.getComment() != null) match = ae.getComment().matches(trueSearch);
      if (!match && number_t) match = ae.getIdentifier().matches(trueSearch);
      //  If seller or buyer search was set, ignore the title / comments.
      if (!match && (all_t || (!seller_t && !buyer_t && !comment_t))) {
        match = ae.getTitle().matches(trueSearch);
      }
      if (invert) match = !match;
      return match;
    }
  }

  public void filterBySearch(String srch) {
    JTable curTable = getCurrentTable();
    Selector mySelector = new myFilter(srch);
    if(!mySelector.select(curTable)) {
      java.awt.Toolkit.getDefaultToolkit().beep();
      MQFactory.getConcrete("Swing").enqueue("No entries matched!");
    }
  }

  public Object getIndexedEntry(int i) {
    return getCurrentTable().getValueAt(i, -1);
  }

  public Object getObjectAt(JTable _table, int x, int y) {
    if (_table != null) {
      int rowPoint = _table.rowAtPoint(new Point(x, y));

      //  A menu item has been selected, instead of a context menu.
      //  This is NOT a valid test, because the popup locations aren't
      //  reset!
      if (x == 0 && y == 0) {
        rowPoint = _table.getSelectedRow();
      }

      if (rowPoint != -1) {
        return _table.getValueAt(rowPoint, -1);
      }
    }
    return null;
  }

  public void actionPerformed(ActionEvent event) {
    AuctionEntry whichAuction = null;
    String actionString = event.getActionCommand();
    JTable chosenTable = getCurrentTable();
    boolean isButton = false;

    if(actionString.startsWith("BT-")) {
      actionString = actionString.substring(3);
      isButton = true;
    }

    if(chosenTable != null) {
      if(!isButton) {
        whichAuction = (AuctionEntry)getObjectAt(chosenTable, this.getPopupX(), this.getPopupY());
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
    JTable ts = getCurrentTable();
    if (ts != null) {
      ((TableRowSorter)ts.getRowSorter()).sort();
    }
  }
}
