package com.jbidwatcher.ui;
/*
 * Copyright (c) 2000-2007, CyberFOX Software, Inc. All Rights Reserved.
 *
 * Developed by mrs (Morgan Schweers)
 */

import com.cyberfox.util.platform.Platform;
import com.jbidwatcher.auction.*;
import com.jbidwatcher.ui.table.auctionTableModel;
import com.jbidwatcher.util.config.*;
import com.jbidwatcher.util.Currency;
import com.jbidwatcher.util.Constants;
import com.jbidwatcher.util.queue.MQFactory;
import com.jbidwatcher.ui.util.*;
import com.jbidwatcher.ui.table.TableColumnController;
import com.jbidwatcher.ui.table.CSVExporter;
import com.jbidwatcher.ui.table.AuctionTable;
import com.jbidwatcher.util.queue.PlainMessageQueue;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.event.TableModelEvent;
import javax.swing.table.TableColumn;
import javax.swing.table.TableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.dnd.DropTarget;
import java.io.IOException;
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
  private JPanel mPanel;

  private final myTableCellRenderer _myRenderer;
  private TableModel model;
  private final TableRowSorter<TableModel> sorter;

  /**
   * @brief Construct a new UI model for a provided auction list.
   * @param newAuctionList - The auction list to use as a 'backing
   *        store' for displaying lists of auctions.
   * @param tableContextMenu - The context menu to present for this table.
   * @param frameContextMenu - The context menu to present for whitespace outside the table.
   * @param cornerButtonListener - The button to sit above the scrollbar.
   */
  public AuctionsUIModel(Auctions newAuctionList, myTableCellRenderer cellRenderer, MultiSnipeManager multiManager, JContext tableContextMenu, final JContext frameContextMenu, ActionListener cornerButtonListener) {
    _myRenderer = cellRenderer;
    _dataModel = newAuctionList;

    _targets = new DropTarget[2];

    model = new auctionTableModel(multiManager, _dataModel.getList());

    _table = new AuctionTable(_dataModel.getName(), model);
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
    adjustRowHeight();

    sorter = new TableRowSorter<TableModel>(_table.getModel());
    _table.setRowSorter(sorter);
    _table.addMouseListener(tableContextMenu);
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
    _scroller.setViewport(new JViewport() {
      private Image image;

      {
        setBackground(_bgColor);
        addMouseListener(frameContextMenu);
        setView(_table);

        try {
          image = ImageIO.read(JConfig.getResource("/jbidwatch.jpg"));
        } catch (IOException e) {
          image = null;
        }
      }

      public void paintComponent(Graphics g) {
        super.paintComponent(g);
        if(image != null && _table.getRowCount() == 0) {
          int imageW = image.getWidth(null);
          int imageH = image.getHeight(null);

          Graphics2D g2d = (Graphics2D) g;
          AlphaComposite comp = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.5f);
          Composite oldComp = g2d.getComposite();
          g2d.setComposite(comp);
          int xloc = getWidth()/2 - imageW/2 - 2;
          int yloc = getHeight()/2 - imageH/2 - 2;

          g2d.drawImage(image, xloc, yloc, this);

          g2d.setComposite(oldComp);
        }
      }
    });

    JDropListener _dropEar;
    if(newAuctionList.isCompleted()) {
      _dropEar = new JDropListener(new TargetDrop());
    } else {
      _dropEar = new JDropListener(new TargetDrop(_dataModel.getName(), new ImageDropResolver() {
        public void handle(String imgUrl, Point location) {
          int rowPoint = _table.rowAtPoint(location);
          AuctionEntry whichAuction = (AuctionEntry)_table.getValueAt(rowPoint, -1);
          DeletedEntry.deleteThumbnails(whichAuction.getIdentifier());
          whichAuction.getAuction().setThumbnailURL(imgUrl);
          ((PlainMessageQueue)MQFactory.getConcrete("thumbnail")).enqueueObject(whichAuction.getAuction());
        }
      }));
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
    addSumMonitor(_table);
    JPanel statusPanel = new TabStatusPanel(_dataModel.getName());
    mPanel.add(statusPanel, BorderLayout.NORTH);
  }

  public JPanel getPanel() {
    return mPanel;
  }

  private void addSumMonitor(JTable table) {
    table.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
      public void valueChanged(ListSelectionEvent event) {
        updateSum();
      }
    });
  }

  private void updateSum() {
    int[] rowList = _table.getSelectedRows();
    String total = sum(rowList);

    if(total == null) {
      MQFactory.getConcrete("Swing").enqueue("PRICE  "); // A blank space to clear the price
    } else {
      MQFactory.getConcrete("Swing").enqueue("PRICE " + rowList.length + " / " + total);
    }
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

  public TableRowSorter<TableModel> getTableSorter() { return sorter; }
  public JTable getTable() { return _table; }

  private static Currency addUSD(Currency inCurr, AuctionEntry ae) {
    boolean newCurrency = (inCurr == null || inCurr.isNull());
    Currency currentUSPrice = ae.getCurrentUSPrice();
    try {
      if(ae.getShippingWithInsurance().isNull()) {
        if(newCurrency) {
          return currentUSPrice;
        }
        return inCurr.add(currentUSPrice);
      }

      if(newCurrency) {
        inCurr = currentUSPrice.add(Currency.convertToUSD(currentUSPrice, ae.getCurrentPrice(), ae.getShippingWithInsurance()));
      } else {
        inCurr = inCurr.add(currentUSPrice.add(Currency.convertToUSD(currentUSPrice, ae.getCurrentPrice(), ae.getShippingWithInsurance())));
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
          return getBestBidValue(ae);
        }
        return inCurr.add(getBestBidValue(ae));
      }

      if(newCurrency) {
        inCurr = getBestBidValue(ae).add(ae.getShippingWithInsurance());
      } else {
        inCurr = inCurr.add(getBestBidValue(ae).add(ae.getShippingWithInsurance()));
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
        } catch (ClassCastException cce) {
          ae2 = null;
        } catch (IndexOutOfBoundsException bounds) {
          ae2 = null;
          approx = true;
        }
        if (ae2 != null) {
          Currency currentUSPrice = ae2.getCurrentUSPrice();

          if (accum == null) {
            accum = currentUSPrice;
            realAccum = getBestBidValue(ae2);
            withShipping = addUSD(withShipping, ae2);
            withRealShipping = addNonUSD(withRealShipping, ae2);
          } else {
            if (!currentUSPrice.isNull() && !accum.isNull() && currentUSPrice.getCurrencyType() != Currency.NONE) {
              accum = accum.add(currentUSPrice);
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
          if (ae2.getCurrentPrice().getCurrencyType() != Currency.US_DOLLAR) approx = true;
        }
      }
    } catch(Currency.CurrencyTypeException e) {
      JConfig.log().handleException("Sum currency exception!", e);
      return null;
    } catch(ArrayIndexOutOfBoundsException ignored) {
      JConfig.log().logDebug("Selection of " + rowList.length + " items changed out from under 'sum'.");
      return null;
    } catch(NullPointerException npe) {
      JConfig.log().logDebug("sum got NPE - this is common during delete operations");
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
      StringBuilder result = new StringBuilder(realAccum.toString());
      if(withRealShipping != null && !realAccum.equals(withRealShipping)) {
        result.append(" (").append(withRealShipping).append(" with ").append(sAndH).append(')');
      }
      return result.toString();
    }

    if(approx) {
      String result;
      if(withShipping != null && !accum.equals(withShipping)) {
        result = "About " + accum.toString() + " (" + withShipping + " with " + sAndH + ')';
      } else {
        result = "About " + accum.toString();
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
    ((auctionTableModel)model).delete(inEntry);
  }

  /**
   * Add an AuctionEntry that has already been created, denying
   * duplicates, but allowing duplicates where both have useful
   * information that is not the same.
   *
   * @param aeNew - The new auction entry to add to the tables.
   */
  public void addEntry(EntryInterface aeNew) {
    if (aeNew != null && ((auctionTableModel)model).insert(aeNew) == -1) {
      JConfig.log().logMessage("JBidWatch: Bad auction entry, cannot add!");
    }
  }

  public boolean toggleField(String field) {
    boolean rval;
    int modelColumn = TableColumnController.getInstance().getColumnNumber(field);
    if(_table.convertColumnIndexToView(modelColumn) == -1) {
      TableColumn newColumn = new TableColumn(modelColumn, Constants.DEFAULT_COLUMN_WIDTH, _myRenderer, null);
      if(modelColumn == TableColumnController.THUMBNAIL) newColumn.setMinWidth(75);
      _table.addColumn(newColumn);
      rval = true;
    } else {
      _table.removeColumn(_table.getColumn(field));
      rval = false;
    }

    adjustRowHeight();

    return rval;
  }

  // hack and a half - but adding a row height attribute for columns seems like overkill
  public void adjustRowHeight() {
    Font def = _myRenderer.getDefaultFont();
    Graphics g = _table.getGraphics();
    int defaultHeight;

    if(def == null || g == null) {
      defaultHeight = Constants.DEFAULT_ROW_HEIGHT;
    } else {
      FontMetrics metrics = g.getFontMetrics(def);
      defaultHeight = metrics.getMaxAscent() + metrics.getMaxDescent() + metrics.getLeading()+4;
    }

    int thumbnailIndex = _table.convertColumnIndexToView(TableColumnController.THUMBNAIL);
    if (thumbnailIndex != -1) {
      defaultHeight = Math.max(Constants.MICROTHUMBNAIL_ROW_HEIGHT, defaultHeight);
    }
    _table.setRowHeight(Math.max(defaultHeight, Constants.DEFAULT_ROW_HEIGHT));
    if(def != null) {
      _table.getTableHeader().setFont(def);
    }
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
    sorter.sort();
  }

  public void redrawAll() {
    _table.tableChanged(new TableModelEvent(model));
  }
}
