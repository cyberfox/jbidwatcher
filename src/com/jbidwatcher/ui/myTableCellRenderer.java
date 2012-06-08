package com.jbidwatcher.ui;//  -*- Java -*-
/*
 * Copyright (c) 2000-2007, CyberFOX Software, Inc. All Rights Reserved.
 *
 * Developed by mrs (Morgan Schweers)
 */

//
//  History:
//  mrs: 23-July-1999 09:29 - This exists to eliminate cell-based selection in the table cell renderer.  (It looks ugly.)

import com.jbidwatcher.auction.AuctionEntry;
import com.jbidwatcher.auction.MultiSnipe;
import com.jbidwatcher.auction.MultiSnipeManager;
import com.jbidwatcher.util.config.JConfig;
import com.jbidwatcher.ui.table.TableColumnController;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.util.HashMap;
import java.util.Map;

public class myTableCellRenderer extends DefaultTableCellRenderer {
  private static Font boldFont = null;
  private static Font fixedFont = null;

  private static final Color darkGreen = new Color(0, 127, 0);
  private static final Color darkRed = new Color(127, 0, 0);
  private static final Color medBlue = new Color(0, 0, 191);
  private int mRow = 0;
  private boolean mThumbnail = false;
  private boolean mSelected;

  public static void resetBehavior() { boldFont = null; fixedFont = null; }

  public void setValue(Object o) {
    if(o instanceof Icon) {
      super.setIcon((Icon) o);
      super.setValue(null);
    } else {
      super.setIcon(null);
      super.setValue(o);
    }
  }

  public Component getTableCellRendererComponent(JTable table, Object value,
                                                 boolean isSelected, boolean hasFocus,
                                                 int row, int column) {
    column = table.convertColumnIndexToModel(column);
    if(value instanceof Icon) {
      setHorizontalAlignment(SwingConstants.CENTER);
      setVerticalAlignment(SwingConstants.CENTER);
    } else {
      setHorizontalAlignment(JLabel.LEFT);
    }
    JComponent returnComponent = (JComponent)super.getTableCellRendererComponent(table, value, isSelected, false, row, column);
    returnComponent.setOpaque(false);

    Object rowData = table.getValueAt(row, -1);
    if(rowData instanceof String) return returnComponent;
    AuctionEntry ae = (AuctionEntry)rowData;
    if(ae == null) return returnComponent;

    Color foreground = chooseForeground(ae, column, table.getForeground());

    mRow = row;

    mThumbnail = column == TableColumnController.THUMBNAIL;

    if (ae.isSniped() &&
        (column == TableColumnController.SNIPE_OR_MAX ||
         column == TableColumnController.SNIPE_TOTAL ||
         column == TableColumnController.SNIPE)) {
      returnComponent.setBackground(snipeBidBackground(ae));
      returnComponent.setOpaque(true);
    }

    mSelected = isSelected;

    Font foo = chooseFont(returnComponent.getFont(), ae, column);
    returnComponent.setFont(foo);
    returnComponent.setForeground(foreground);

    return(returnComponent);
  }

  private Color lighten(Color background) {
    int r = background.getRed();
    int g = background.getGreen();
    int b = background.getBlue();
    r = Math.min(255, r + 20);
    g = Math.min(255, g + 20);
    b = Math.min(255, b + 20);
    return new Color(r, g, b);
  }

  private Map<Integer, GradientPaint> gradientCache = new HashMap<Integer, GradientPaint>();
  private Color mLastColor = null;

  private final static String evenList = "List.evenRowBackgroundPainter";
  private final static String oddList = "List.oddRowBackgroundPainter";

  private final static Color evenDefault = new Color(0x0f1, 0x0f6, 0x0fe);
  private final static Color oddDefault = new Color(0x0ff, 0x0ff, 0x0ff);

  /**
   * Paint a row prior to drawing the components on it.  There are four core
   * paths.  If complex backgrounds are enabled (my hackery from a while ago)
   * then they are rendered.  Otherwise, if it's not a Mac, then the compoent's
   * default rendered is painted with.  If it's a Mac and the row is selected,
   * we use a custom gradient render.  If it's not selected, we use the Mac
   * default even/odd row background painters.  (If those defaults aren't available,
   * we use some default colors that are similar to those painters under Snow
   * Leopard.  @see drawCustomBackground)
   *
   * @param g - The Graphics context into which to draw the row background.
   */
  public void paintComponent(Graphics g) {
    if(g != null) {
      boolean painted = false;
      if (mSelected) {
        Color selected = UIManager.getColor("Table.selectionBackground");
        String userColor = JConfig.queryConfiguration("selection.color");
        if(userColor != null) {
          selected = MultiSnipe.reverseColor(userColor);
        }
        renderGradient(g, selected);
      } else {
        painted = drawCustomBackground(g);
      }
      if (mThumbnail) {
        drawThumbnailBox(g);
      }
      if(!painted) super.paintComponent(g);
    }
  }

  /**
   * Retrieve the default Mac border painters, or use default colors
   * if the painters aren't available.  The component is painted across
   * the entire row, and then a 0.1 Alpha + Black component line is drawn
   * over the bottom line, darkening it slightly, but leaving whatever
   * color it was in place.
   *
   * @param g - The Graphics context into which to draw the row background.
   * @return - true if the super.paintComponent() method was called (always true currently).
   */
  private boolean drawCustomBackground(Graphics g) {
    boolean painted;
    Border bgPaint = UIManager.getBorder((mRow % 2) == 0 ? evenList : oddList);
    if(bgPaint != null) {
      bgPaint.paintBorder(this, g, 0, 0, getWidth(), getHeight());
      super.paintComponent(g);
      painted = true;
    } else {
      renderColor(g, (mRow % 2) == 0 ? evenDefault : oddDefault);
      super.paintComponent(g);
      painted = true;
    }

    Graphics2D g2d = (Graphics2D) g;
    float alpha = .1f;
    Composite saved = g2d.getComposite();
    g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
    g.setColor(Color.BLACK);
    g.drawLine(0, getHeight() - 1, getWidth(), getHeight() - 1);
    g2d.setComposite(saved);

    return painted;
  }

  private void drawThumbnailBox(Graphics g) {
    int top = getHeight() / 2 - 32;
    int left = getWidth() / 2 - 32;
    float alpha = .1f;
    Graphics2D g2d = (Graphics2D) g;
    Color oldColor = g2d.getColor();
    Stroke oldStroke = g2d.getStroke();
    Composite oldComp= g2d.getComposite();

    g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
    g2d.setColor(Color.BLACK);
    g2d.setStroke(new BasicStroke(2, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 0, new float[]{4}, 0));
    g2d.drawRoundRect(left, top, 64, 64, 4, 4);

    g2d.setStroke(oldStroke);
    g2d.setColor(oldColor);
    g2d.setComposite(oldComp);
  }

  private void renderGradient(Graphics g, Color selected) {
    if(mLastColor != null && !mLastColor.equals(selected)) gradientCache.clear();
    mLastColor = selected;
    GradientPaint paint = gradientCache.get(cacheMapper());
    if (paint == null) {
      paint = new GradientPaint(0, 0, lighten(selected), 0, getHeight(), selected, false);
      gradientCache.put(cacheMapper(), paint);
    }
    Graphics2D g2d = (Graphics2D) g;
    g2d.setPaint(paint);
    Rectangle bounds = g2d.getClipBounds();
    g2d.fillRect((int) bounds.getX(), (int) bounds.getY(), (int) bounds.getWidth(), (int) bounds.getHeight());
  }

  private void renderColor(Graphics g, Color color) {
    g.setColor(color);
    Rectangle bounds = g.getClipBounds();
    g.fillRect((int) bounds.getX(), (int) bounds.getY(), (int) bounds.getWidth(), (int) bounds.getHeight());
  }

  private int cacheMapper() {return 10000 * (mRow % 2) + getHeight();}

  private Color chooseForeground(AuctionEntry ae, int col, Color foreground) {
    switch(col) {
      case TableColumnController.ID:
        return chooseIDColor(ae);
      case TableColumnController.SNIPE_OR_MAX:
      case TableColumnController.SNIPE_TOTAL:
        return snipeBidColor(ae);
      case TableColumnController.TITLE:
        return titleColor(ae);
      case TableColumnController.CUR_BID:
      default:
        return (foreground == null) ? Color.BLACK : foreground;
    }
  }

  private static Font sDefaultFont = null;
  public static Font getDefaultFont() {
    if(sDefaultFont == null) {
      String cfgDefault = JConfig.queryConfiguration("default.font");
      if(cfgDefault != null) {
        sDefaultFont = Font.decode(cfgDefault);
      }
    }
    return sDefaultFont;
  }

  private static String getStyleName(int style) {
    switch(style) {
      case 1: return "bold";
      case 2: return "italic";
      case 3: return "bolditalic";
      case 0:
      default: return "plain";
    }
  }

  public static void setDefaultFont(Font defaultFont) {
    String formattedFontName = defaultFont.getFamily() + "-" + getStyleName(defaultFont.getStyle()) + "-" + defaultFont.getSize();
    JConfig.setConfiguration("default.font", formattedFontName);
    sDefaultFont = defaultFont;
    fixedFont = null;
    boldFont = null;
  }

  private Font chooseFont(Font base, AuctionEntry ae, int col) {
    boolean hasComment = ae.getComment() != null;
    if(sDefaultFont != null) base = sDefaultFont; else sDefaultFont = base;

    if(fixedFont == null) fixedFont = new Font("Monospaced", base.getStyle(), base.getSize());
    if(boldFont == null) boldFont = base.deriveFont(Font.BOLD);
    if(col == TableColumnController.TIME_LEFT) return fixedFont;
    if(hasComment && col == TableColumnController.ID) return boldFont;
    if(ae.isShippingOverridden() && col == TableColumnController.SHIPPING_INSURANCE) return boldFont;
    return base;
  }

  private Color snipeBidBackground(AuctionEntry ae) {
    MultiSnipe ms = MultiSnipeManager.getInstance().getForAuctionIdentifier(ae.getIdentifier());
    if (ms != null) {
      return ms.getColor();
    }
    return null;
  }

  private Color titleColor(AuctionEntry ae) {
    if (ae != null && ae.getHighBidder() != null) {
      if (ae.isHighBidder()) {
        if (!ae.isReserve() || ae.isReserveMet()) {
          return medBlue;
        } else {
          return darkRed;
        }
      } else {
        if (ae.getNumBidders() > 0 && (!ae.isReserve() || ae.isReserveMet())) {
          if (!ae.isSeller()) {
            return darkRed;
          } else {
            return darkGreen;
          }
        }
      }
    }

    return Color.BLACK;
  }

  private Color snipeBidColor(AuctionEntry ae) {
    if(ae != null) {
      if(ae.isSniped()) {
        MultiSnipe ms = MultiSnipeManager.getInstance().getForAuctionIdentifier(ae.getIdentifier());
        if (ms == null) {
          return ae.isSnipeValid() ? darkGreen : darkRed;
        }
        if (ae.snipeCancelled()) {
          return darkRed;
        }
      } else if (ae.isBidOn()) {
        if(ae.isHighBidder()) return medBlue;
        return darkRed;
      } else if (ae.snipeCancelled()) {
        return darkRed;
      }
    }
    return Color.BLACK;
  }

  private Color chooseIDColor(AuctionEntry ae) {
    return ae != null && ae.isJustAdded() ? darkGreen : Color.BLACK;
  }
}
