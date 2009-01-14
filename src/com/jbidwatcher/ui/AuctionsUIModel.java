package com.jbidwatcher.ui;
/*
 * Copyright (c) 2000-2007, CyberFOX Software, Inc. All Rights Reserved.
 *
 * Developed by mrs (Morgan Schweers)
 */

import com.jbidwatcher.util.config.*;
import com.jbidwatcher.util.Currency;
import com.jbidwatcher.util.Constants;
import com.jbidwatcher.auction.AuctionEntry;
import com.jbidwatcher.auction.Auctions;
import com.jbidwatcher.auction.EntryInterface;
import com.jbidwatcher.ui.util.*;
import com.jbidwatcher.ui.table.TableColumnController;
import com.jbidwatcher.ui.table.CSVExporter;
import com.jbidwatcher.ui.table.TableSorter;
import com.jbidwatcher.ui.table.AuctionTable;
import com.jbidwatcher.platform.Platform;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.event.TableModelEvent;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.dnd.DropTarget;
import java.util.*;
import java.util.List;

public class AuctionsUIModel {
  private Auctions _dataModel;
  private JTable _table;
  private JScrollPane _scroller;
  /** @noinspection FieldCanBeLocal*/
  private DropTarget[] _targets;  /* This can't be local, otherwise it gets GC'ed, which is bad. */
  private Color _bgColor;
  private JPrintable _print;
  private CSVExporter _export;
  private JLabel _prices;
  private JPanel mPanel;

  private static final myTableCellRenderer _myRenderer = new myTableCellRenderer();
  private TableSorter _tSort;

  /**
   * @brief Construct a new UI model for a provided auction list.
   * @param newAuctionList - The auction list to use as a 'backing
   * store' for displaying lists of auctions.
   *@param tableContextMenu - The context menu to present for this table.
   * @param frameContextMenu - The context menu to present for whitespace outside the table.
   * @param cornerButtonListener - The button to sit above the scrollbar.
   */
  public AuctionsUIModel(Auctions newAuctionList, JContext tableContextMenu, JContext frameContextMenu, ActionListener cornerButtonListener) {
    _dataModel = newAuctionList;

    _targets = new DropTarget[2];

    _tSort = new TableSorter(_dataModel.getName(), "Time left", new auctionTableModel(_dataModel.getList()));

    _table = new AuctionTable(_dataModel.getName(), _tSort);
    if(newAuctionList.isCompleted()) {
      if(_table.convertColumnIndexToView(TableColumnController.END_DATE) == -1) {
        _table.addColumn(new TableColumn(TableColumnController.END_DATE, Constants.DEFAULT_COLUMN_WIDTH, _myRenderer, null));
      }
    }
    if(JConfig.queryConfiguration("show_shipping", "false").equals("true")) {
      if(_table.convertColumnIndexToView(TableColumnController.SHIPPING_INSURANCE) == -1) {
        _table.addColumn(new TableColumn(TableColumnController.SHIPPING_INSURANCE));
      }
      JConfig.killAll("show_shipping");
    }
    // provide sufficient vertical height in the rows for micro-thumbnails list view
    if (_table.convertColumnIndexToView(TableColumnController.THUMBNAIL) != -1) {
      _table.setRowHeight(Constants.MICROTHUMBNAIL_ROW_HEIGHT);
    }
    _table.addMouseListener(tableContextMenu);
    _tSort.addMouseListenerToHeaderInTable(_table);
    if(Platform.isMac() || JConfig.queryConfiguration("ui.useCornerButton", "true").equals("true")) {
      _scroller = new JScrollPane(_table, ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
    } else {
      _scroller = new JScrollPane(_table, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
    }

    //  This is a button to manage the custom columns for the current tab.
    if(JConfig.queryConfiguration("ui.useCornerButton", "true").equals("true")) {
      JButton cornerButton = new JButton("*");
      cornerButton.addActionListener(cornerButtonListener);
      _scroller.setCorner(ScrollPaneConstants.UPPER_RIGHT_CORNER, cornerButton);
    }

    _bgColor = UIManager.getColor("window");
    _scroller.getViewport().setBackground(_bgColor);
    _scroller.getViewport().addMouseListener(frameContextMenu);

    JDropListener _dropEar;
    if(newAuctionList.isCompleted()) {
      _dropEar = new JDropListener(new TargetDrop());
    } else {
      _dropEar = new JDropListener(new TargetDrop(_dataModel.getName()));
    }
    _targets[0] = new DropTarget(_scroller.getViewport(), _dropEar);
    _targets[1] = new DropTarget(_table, _dropEar);

    _targets[0].setActive(true);
    _targets[1].setActive(true);

    _print = new JPrintable(_table);
    _export = new CSVExporter(_table);
    _table.setDefaultRenderer(String.class, _myRenderer);
    _table.setDefaultRenderer(Icon.class, _myRenderer);

    mPanel = new JPanel();
    mPanel.setLayout(new BorderLayout());
    mPanel.add(_scroller, BorderLayout.CENTER);
    JPanel jp2 = buildBottomPanel(tableContextMenu);
    mPanel.add(jp2, BorderLayout.SOUTH);
  }

  public JPanel getPanel() {
    return mPanel;
  }

  private JPanel buildBottomPanel(JContext tableContextMenu) {
    JPanel jp2 = new JPanel();
    jp2.setLayout(new BorderLayout());
    _prices = new JLabel(" ");
    jp2.add(_prices, BorderLayout.EAST);
    jp2.add(ButtonMaker.makeButton("icons/xml.png", "Show RSS feed information", "RSS", tableContextMenu, true), BorderLayout.WEST);
    _table.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
      public void valueChanged(ListSelectionEvent event) {
        int[] rowList = _table.getSelectedRows();
        if(rowList.length == 0) {
          _prices.setText(" ");
        } else {
          String total = sum(rowList);
          if(total != null) {
            _prices.setText(rowList.length + " items, price total: " + total);
          } else {
            _prices.setText(" ");
          }
        }
      }
    });
    return jp2;
  }

  /**
   * @brief Pick and return a value from the entry that best describes
   * how much COULD be spent on it by the buyer.
   *
   * For an item not bid on, it's the current bid price.  For an item
   * the user has bid on, it's their maximum bid.  For an item the
   * user has a snipe set for, it's the maximum of their snipe bid.
   * If the item is closed, it's just the current bid price.
   *
   * @param checkEntry - The AuctionEntry to operate on.
   *
   * @return - A currency value containing either the current bid, the
   * users high bid, or the users snipe bid.
   */
  private static Currency getBestBidValue(AuctionEntry checkEntry) {
    return checkEntry.bestValue();
  }

  //  A single accessor...
  public TableSorter getTableSorter() { return _tSort; }

  private static Currency addUSD(Currency inCurr, AuctionEntry ae) {
    boolean newCurrency = (inCurr == null || inCurr.isNull());
    try {
      if(ae.getShippingWithInsurance().isNull()) {
        if(newCurrency) {
          return ae.getUSCurBid();
        }
        return inCurr.add(ae.getUSCurBid());
      }

      if(newCurrency) {
        inCurr = ae.getUSCurBid().add(Currency.convertToUSD(ae.getUSCurBid(), ae.getCurBid(), ae.getShippingWithInsurance()));
      } else {
        inCurr = inCurr.add(ae.getUSCurBid().add(Currency.convertToUSD(ae.getUSCurBid(), ae.getCurBid(), ae.getShippingWithInsurance())));
      }
    } catch(Currency.CurrencyTypeException cte) {
      JConfig.log().handleException("This should have been cleaned up.", cte);
    }
    return inCurr;
  }

  private static Currency addNonUSD(Currency inCurr, AuctionEntry ae) {
    boolean newCurrency = inCurr == null || inCurr.isNull();
    try {
      if(ae.getShippingWithInsurance().isNull()) {
        if(newCurrency) {
          return ae.getCurBid();
        }
        return inCurr.add(ae.getCurBid());
      }

      if(newCurrency) {
        inCurr = ae.getCurBid().add(ae.getShippingWithInsurance());
      } else {
        inCurr = inCurr.add(ae.getCurBid().add(ae.getShippingWithInsurance()));
      }
    } catch(Currency.CurrencyTypeException cte) {
      JConfig.log().handleException("This should have been cleaned up.", cte);
    }

    return inCurr;
  }

  protected String sum(int[] rowList) {
    boolean approx = false, i18n = true;
    Currency accum = null;
    Currency withShipping = null;
    Currency withRealShipping = null;
    Currency realAccum = null;

    try {
      for (int aRowList : rowList) {
        AuctionEntry ae2;
        try {
          ae2 = (AuctionEntry) _table.getValueAt(aRowList, -1);
        } catch (IndexOutOfBoundsException bounds) {
          ae2 = null;
          approx = true;
        }
        if (ae2 != null) {
          if (accum == null) {
            accum = ae2.getUSCurBid();
            realAccum = getBestBidValue(ae2);
            withShipping = addUSD(withShipping, ae2);
            withRealShipping = addNonUSD(withRealShipping, ae2);
          } else {
            Currency stepVal = ae2.getUSCurBid();
            if (!stepVal.isNull() && !accum.isNull() && stepVal.getCurrencyType() != Currency.NONE) {
              accum = accum.add(stepVal);
              withShipping = addUSD(withShipping, ae2);

              //  If we're still trying to do the internationalization
              //  thing, then try to keep track of the 'real' total.
              if (i18n) {
                //noinspection NestedTryStatement
                try {
                  realAccum = realAccum.add(getBestBidValue(ae2));
                  withRealShipping = addNonUSD(withRealShipping, ae2);
                } catch (Currency.CurrencyTypeException cte) {
                  //  We can't handle multiple non-USD currency types, so
                  //  we stop trying to do the internationalization thing.
                  i18n = false;
                }
              }
            }
          }
          if (ae2.getCurBid().getCurrencyType() != Currency.US_DOLLAR) approx = true;
        }
      }
    } catch(Currency.CurrencyTypeException e) {
      JConfig.log().handleException("Sum currency exception!", e);
      return null;
    } catch(ArrayIndexOutOfBoundsException ignored) {
      JConfig.log().logDebug("Selection of " + rowList.length + " items changed out from under 'sum'.");
      return null;
    } catch(Exception e) {
      JConfig.log().handleException("Sum serious exception!", e);
      return null;
    }

    if(accum == null || accum.isNull()) {
      return null;
    }

    String sAndH = "s/h";
    if(!Locale.getDefault().equals(Locale.US)) sAndH = "p/p";

    //  If we managed to do the i18n thing through it all, and we have
    //  some real values, return it.
    if(i18n && realAccum != null) {
      StringBuffer result = new StringBuffer(realAccum.toString());
      if(withRealShipping != null && !realAccum.equals(withRealShipping)) {
        result.append(" (").append(withRealShipping).append(" with ").append(sAndH).append(')');
      }
      return result.toString();
    }

    if(approx) {
      String result;
      if(withShipping != null && !accum.equals(withShipping)) {
        result = "Approximately " + accum.toString() + " (" + withShipping + " with " + sAndH + ')';
      } else {
        result = "Approximately " + accum.toString();
      }
      return result;
    }

    if(withShipping != null && !accum.equals(withShipping)) {
      return accum.toString() + " (" + withShipping + " with " + sAndH + ')';
    }

    return accum.toString();
  }

  /**
   * @brief Sets the background color for this tab to the passed in color.
   *
   * @param bgColor - The color to set the background to.
   */
  public void setBackground(Color bgColor) {
    _scroller.getViewport().setBackground(bgColor);
    _table.setBackground(bgColor);
    _bgColor = bgColor;
  }

  /**
   * @brief Return the background color this was set to.
   *
   * @return - The color, if any, this tab was set to.
   */
  public Color getBackground() {
    return _bgColor;
  }

  /**
   * Delete an auction entry, using that auction entry to match against.
   * This also tells the auction entry to unregister itself!
   *
   * @param inEntry - The auction entry to delete.
   */
  public void delEntry(EntryInterface inEntry) {
    _tSort.delete(inEntry);
  }

  /**
   * Add an AuctionEntry that has already been created, denying
   * duplicates, but allowing duplicates where both have useful
   * information that is not the same.
   *
   * @param aeNew - The new auction entry to add to the tables.
   * @return - true if the auction was added, false if not.
   */
  public void addEntry(EntryInterface aeNew) {
    if (aeNew != null) {
      if (_tSort.insert(aeNew) == -1) {
        JConfig.log().logMessage("JBidWatch: Bad auction entry, cannot add!");
      }
    }
  }

  public boolean toggleField(String field) {
    boolean rval;
    int modelColumn = TableColumnController.getInstance().getColumnNumber(field);
    if(_table.convertColumnIndexToView(modelColumn) == -1) {
      _table.addColumn(new TableColumn(modelColumn, Constants.DEFAULT_COLUMN_WIDTH, _myRenderer, null));
      rval = true;
    } else {
      _table.removeColumn(_table.getColumn(field));
      _tSort.removeColumn(field, _table);
      rval = false;
    }

    // hack and a half - but adding a row height attribute for columns seems like overkill
    if (_table.convertColumnIndexToView(TableColumnController.THUMBNAIL) != -1) {
      _table.setRowHeight(Constants.MICROTHUMBNAIL_ROW_HEIGHT);
    } else {
      _table.setRowHeight(Constants.DEFAULT_ROW_HEIGHT);
    }

    return rval;
  }

  public List<String> getColumns() {
    ArrayList<String> al = new ArrayList<String>();
    for(int i = 0; i<_table.getColumnCount(); i++) {
      al.add(_table.getColumnName(i));
    }

    return al;
  }

  public boolean export(String fname) {
    return _export.export(fname);
  }

  /*!@class JComponentCellRenderer
   *
   * @brief Allows components themselves to be added to a JTable, and
   * allows them to offer themselves as renderers.
   */
  static class JComponentCellRenderer implements TableCellRenderer {
    public Component getTableCellRendererComponent(JTable table, Object value,
                                                   boolean isSelected, boolean hasFocus, int row, int column) {
      return (Component) value;
    }
  }

  /**
   * @brief Print this table.
   *
   */
  public void print() {
    _print.doPrint();
  }

  /**
   * @brief Convert current column widths into display properties to
   * be saved for a future session.
   *
   * @param addToProps - The properties object to add the column widths to.
   * @param name - The category name to get the info from.
   */
  public void getColumnWidthsToProperties(Properties addToProps, String name) {
    for(int j = 0; j<_table.getColumnCount(); j++) {
      TableColumn ct;
      try {
        ct = _table.getColumn(_table.getColumnName(j));
      } catch(IllegalArgumentException iae) {
        JConfig.log().logMessage("Column can't be retrieved from the table: " + _table.getColumnName(j));
        ct = null;
      }
        //      ColumnProps cp = new ColumnProps(_dataModel.getColumnName(j), j, ct.getWidth());
      //noinspection StringContatenationInLoop
      if(ct != null) addToProps.setProperty(name + '.' + _table.getColumnName(j), Integer.toString(j) + '.' + Integer.toString(ct.getWidth()));
    }
  }

  public void getColumnWidthsToProperties(Properties addToProps) {
    getColumnWidthsToProperties(addToProps, _dataModel.getName());
  }

  public void sort() {
    _tSort.sort();
  }

  public void redrawAll() {
    _tSort.tableChanged(new TableModelEvent(_tSort));
  }
}
