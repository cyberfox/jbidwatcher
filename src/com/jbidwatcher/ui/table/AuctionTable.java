package com.jbidwatcher.ui.table;

import com.jbidwatcher.auction.AuctionEntry;
import com.jbidwatcher.util.config.JConfig;
import com.jbidwatcher.util.config.ErrorManagement;
import com.jbidwatcher.util.Constants;
import com.jbidwatcher.util.queue.SuperQueue;

import javax.swing.*;
import javax.swing.table.TableModel;
import javax.swing.table.TableColumn;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.Point;
import java.awt.Dimension;
import java.util.TreeMap;

/**
 * Created by IntelliJ IDEA.
* User: Morgan
* Date: Jun 20, 2008
* Time: 12:06:56 PM
* Handle tooltips, at least.  A very cool feature.
*/
public class AuctionTable extends JTable {

  /**
   * @brief Constructs a JTable out of a prefix to search for in the
   * configuration, and a TableModel to apply to the table.
   *
   * The TableModel has the list of column names, and when you add
   * "prefix." to the front of them, it makes a configuration entry
   * which says the size of that column.
   *
   * @param name - A string that gets prepended to the column name
   *                 in order to produce a display property showing
   *                 the preferred width of that column.
   * @param atm - A TableModel that will be used for rendering the table.
   *
   * @return A new JTable, properly spaced and filled out according to
   *         the configuration preferences.
   */
  public AuctionTable(String name, TableModel atm) {
    super();
    createDefaultRenderers();
    setShowGrid(false);
    setIntercellSpacing(new Dimension(0, 0));
    setDoubleBuffered(true);
    setAutoCreateColumnsFromModel(false);

    setModel(atm);
    loadColumnSettings(name, atm);
    doLayout();
  }

  public String getToolTipText(MouseEvent event) {
    Point point = new Point(event.getX(), event.getY());
    int rowPoint = rowAtPoint(point);
    String result = null;
    int colPoint = columnAtPoint(point);

    if(rowPoint != -1) {
      AuctionEntry ae = (AuctionEntry) getValueAt(rowPoint, -1);
      boolean showThumbnail = true;

      if(getRowHeight() == Constants.MICROTHUMBNAIL_ROW_HEIGHT) {
        showThumbnail = getColumnName(colPoint).equals("Thumbnail");
      }

      result = ae.buildHTMLComment(showThumbnail);
    }

    return result == null ? super.getToolTipText(event) : result;
  }

  class MouseListenerSelectProxy implements MouseListener {
    private MouseListener m_peer;

    MouseListenerSelectProxy(MouseListener ml) { m_peer = ml; }

    public MouseListener getPeer() { return m_peer; }

    /**
     * Invoked when the mouse button has been clicked (pressed
     * and released) on a component.
     */
    public void mouseClicked(MouseEvent e) { if (!e.isPopupTrigger()) m_peer.mouseClicked(e); }

    /**
     * Invoked when the mouse enters a component.
     */
    public void mouseEntered(MouseEvent e) { m_peer.mouseEntered(e); }

    /**
     * Invoked when the mouse exits a component.
     */
    public void mouseExited(MouseEvent e) { m_peer.mouseExited(e); }

    /**
     * Invoked when a mouse button has been pressed on a component.
     */
    public void mousePressed(MouseEvent e) { if (!e.isPopupTrigger()) m_peer.mousePressed(e); }

    /**
     * Invoked when a mouse button has been released on a component.
     */
    public void mouseReleased(MouseEvent e) { if (!e.isPopupTrigger()) m_peer.mouseReleased(e); }
  }

  private final static String METAL_MOUSE_LISTENER = "javax.swing.plaf.basic.BasicTableUI$MouseInputHandler";
  private final static String AQUA_MOUSE_LISTENER = "apple.laf.AquaTableUI$MouseInputHandler";

  /** @noinspection InstanceVariableMayNotBeInitialized*/
  // DO NOT INITIALIZE proxyMouseListener!!!!!
  // It is used in addMouseListener BEFORE the constuctor is run
  // (called from the base class constructor). If it is set here
  // or in the constructor, it will clobber the value set by the
  // call from the base class constructor.
  private MouseListenerSelectProxy proxyMouseListener;

  public synchronized void addMouseListener(MouseListener ml) {
    String mlClass = ml.getClass().getName();
    if ((proxyMouseListener == null) &&
        (mlClass.equals(METAL_MOUSE_LISTENER) ||
         mlClass.equals(AQUA_MOUSE_LISTENER))) {
      proxyMouseListener = new MouseListenerSelectProxy(ml);
      super.addMouseListener(proxyMouseListener);
    } else {
      super.addMouseListener(ml);
    }
  }

  public synchronized void removeMouseListener(MouseListener ml) {
    //noinspection ObjectEquality
    if ((proxyMouseListener != null) && (ml == proxyMouseListener.getPeer())) {
      super.removeMouseListener(proxyMouseListener);
      proxyMouseListener = null;
    } else {
      super.removeMouseListener(ml);
    }
  }

  private static final String[][] DEFAULT_COLUMNS = new String[][]{
          {"Number", "100"},
          {"Current", "89"},
          {"Max", "62"},
          {"Description", "297"},
          {"Time left", "127"},
          {"Status", "67"},
          {"Seller", "147"},
  };

  private static int notify_delay = 0;

  private void loadColumnSettings(String prefix, TableModel atm) {
    String curColumnName = "";

    TreeMap<String, Integer> initialToSaved = new TreeMap<String, Integer>();
    //  This code would need to be somewhat revamped if we allowed
    //  arbitrary, or user-selected column names.
    try {
      for(int i = 0; i<atm.getColumnCount(); i++) {
        curColumnName = atm.getColumnName(i);
        //noinspection StringContatenationInLoop
        String colWidth = JConfig.queryDisplayProperty(prefix + '.' + curColumnName);
        if(colWidth == null) {
          colWidth = JConfig.queryDisplayProperty(curColumnName);
        }
        if(colWidth != null) {
          TableColumn tc = new TableColumn(i);
          tc.setHeaderValue(curColumnName);
          tc.setIdentifier(curColumnName);
          addColumn(tc);
          int dotIndex = colWidth.indexOf('.');
          if(dotIndex != -1) {
            String colIndex = colWidth.substring(0, dotIndex);
            colWidth = colWidth.substring(dotIndex+1);
            initialToSaved.put(curColumnName, Integer.parseInt(colIndex));
          }
          getColumn(curColumnName).setPreferredWidth(Integer.parseInt(colWidth));
          getColumn(curColumnName).setWidth(Integer.parseInt(colWidth));
        }
      }
    } catch(Exception e) {
      //  If we encountered any errors in earlier columns, don't try
      //  to set later columns.
      ErrorManagement.handleException("In display configuration for table " + prefix +", column \"" + curColumnName + "\" has an invalid property.", e);
      ErrorManagement.logDebug("No longer loading column widths from configuration.");
    }

    //  If there are less than 2 columns, freak out and refresh.
    if(initialToSaved.size() < 2) {
      SuperQueue.getInstance().preQueue("NOTIFY Column data for '" + prefix + "' was corrupted; resetting to defaults", "Swing", System.currentTimeMillis() + Constants.ONE_SECOND * 12 + notify_delay);
      ErrorManagement.logMessage("Column data for '\" + prefix + \"' was corrupted; resetting to defaults");
      notify_delay += 2 * Constants.ONE_SECOND;
      for(String[] column : DEFAULT_COLUMNS) {
        if(column[0].equals("Time left") && prefix.equals("complete")) continue;

        TableColumn tc = new TableColumn(TableColumnController.getInstance().getColumnNumber(column[0]));
        tc.setHeaderValue(column[0]);
        tc.setIdentifier(column[0]);
        addColumn(tc);
        getColumn(column[0]).setPreferredWidth(Integer.parseInt(column[1]));
        getColumn(column[0]).setWidth(Integer.parseInt(column[1]));
      }
    }

    if(!initialToSaved.isEmpty()) {
      for (String colName : initialToSaved.keySet()) {
        int colFrom = getColumnModel().getColumnIndex(colName);
        int colTo = initialToSaved.get(colName);
        try {
          moveColumn(colFrom, colTo);
        } catch (IllegalArgumentException iae) {
          //  Ignore it, and move on.
        }
      }
    }
  }
}
