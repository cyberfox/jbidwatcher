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
import com.jbidwatcher.config.JConfig;
import com.jbidwatcher.platform.Platform;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.util.HashMap;
import java.util.Map;

public class myTableCellRenderer extends DefaultTableCellRenderer {
  private static Color darkBG = null;
  private static Font boldFont = null;
  private static Font fixedFont = null;

  private static String selectionColorString = null;
  private static Color selectionColor = null;

  private static final Color darkGreen = new Color(0, 127, 0);
//  private static final Color darkBlue = new Color(0, 0, 127);
  private static final Color darkRed = new Color(127, 0, 0);
  private static final Color medBlue = new Color(0, 0, 191);
  private static final Color linuxSelection = new Color(204,204,255);
  private int mRow = 0;
  private boolean mSelected = false;

  private class Colors {
    Color mForeground;
    Color mBackground;

    private Colors(Color foreground, Color background) {
      mForeground = foreground;
      mBackground = background;
    }

    public Color getForeground() {
      return mForeground;
    }

    public Color getBackground() {
      return mBackground;
    }
  }

  public static void resetBehavior() { darkBG = null; boldFont = null; fixedFont = null; }

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
    Component returnComponent;
    if(value instanceof Icon) {
      setHorizontalAlignment(JLabel.CENTER);
    } else {
      setHorizontalAlignment(JLabel.LEFT);
    }
    returnComponent = super.getTableCellRendererComponent(table, value, isSelected, false, row, column);

    AuctionEntry ae = (AuctionEntry)table.getValueAt(row, -1);
    if(ae == null) return returnComponent;

    Color foreground = chooseForeground(ae, column);
    Color background = chooseBackground(ae, column, table.getBackground());

    mRow = row;

    if( (row % 2) == 1) {
      if(darkBG == null) {
        int r = background.getRed();
        int g = background.getGreen();
        int b = background.getBlue();
        r = Math.max(0, r - 20);
        g = Math.max(0, g - 20);
        b = Math.max(0, b - 20);
        darkBG = new Color(r, g, b);
      }

      if (column != 2 || !ae.isMultiSniped()) {
      if ((column != TableColumnController.SNIPE_OR_MAX && column != TableColumnController.SNIPE_TOTAL) || !ae.isMultiSniped()) {
          if(JConfig.queryConfiguration("display.alternate", "true").equals("true")) {
        }
          background = darkBG;
        }
      }
    }

    mSelected = isSelected;
    if(isSelected) {
      Colors selectionColors = getSelectionColors(column, ae, foreground, background);

      foreground = selectionColors.getForeground();
      background = selectionColors.getBackground();
    }

    Font foo = chooseFont(returnComponent.getFont(), ae, column);
    returnComponent.setFont(foo);
    returnComponent.setForeground(foreground);
    returnComponent.setBackground(background);

    return(returnComponent);
  }

  private Map<Integer, GradientPaint> gradientCache = new HashMap<Integer, GradientPaint>();

  public void paintComponent(Graphics g) {
    if(g != null) {
      if(JConfig.queryDisplayProperty("background.complex", "false").equals("true")) {
        Graphics2D g2d = (Graphics2D) g;
        Rectangle bounds = g2d.getClipBounds();
        if (bounds != null) {
          if (!mSelected) {
            setOpaque(false);
            GradientPaint paint = getGradientPaint();
            g2d.setPaint(paint);
          } else {
            g.setColor(getBackground());
          }
          g2d.fillRect((int) bounds.getX(), (int) bounds.getY(), (int) bounds.getWidth(), (int) bounds.getHeight());
        }
      }
      super.paintComponent(g);
    }
  }

  private int cacheMapper() {return 10000 * (mRow % 2) + getHeight();}

  private GradientPaint getGradientPaint() {
    GradientPaint paint = gradientCache.get(cacheMapper());
    if(paint == null) {
      if ((mRow % 2) == 0) {
        paint = new GradientPaint(0, 0, Color.WHITE, 0, getHeight(), Color.LIGHT_GRAY, false);
      } else {
        paint = new GradientPaint(0, 0, Color.LIGHT_GRAY, 0, getHeight(), Color.WHITE, false);
      }
    }
    gradientCache.put(cacheMapper(), paint);
    return paint;
  }

  private Colors getSelectionColors(int column, AuctionEntry ae, Color foreground, Color background) {
    if (JConfig.queryConfiguration("selection.invert", "false").equals("true")) {
      Color tmp = foreground;
      foreground = background;
      background = tmp;
    } else {
      if (JConfig.queryConfiguration("selection.color") != null) {
        if (selectionColorString == null || !selectionColorString.equals(JConfig.queryConfiguration("selection.color"))) {
          selectionColorString = JConfig.queryConfiguration("selection.color");
          selectionColor = MultiSnipe.reverseColor(selectionColorString);
        }
        background = selectionColor;
      } else {
        if (Platform.isMac() || Platform.isLinux()) {
          if (column == 2 && ae.isMultiSniped()) foreground = background;
          background = linuxSelection;
        } else {
          if (column == 2 && ae.isMultiSniped()) {
            foreground = background;
          } else if (foreground.equals(Color.BLACK)) {
            foreground = SystemColor.textHighlightText;
          }
          background = SystemColor.textHighlight;
        }
      }
    }
    return new Colors(foreground, background);
  }

  private Color chooseForeground(AuctionEntry ae, int col) {
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
        return Color.BLACK;
    }
  }

  private Color chooseBackground(AuctionEntry ae, int col, Color default_color) {
    Color ret = null;

    if(ae != null) {
      if ((col == TableColumnController.SNIPE_OR_MAX || col == TableColumnController.SNIPE_TOTAL) && ae.isSniped()) {
        ret = snipeBidBackground(ae);
      }
    }

    if(ret != null) return ret;
    return default_color;
  }

  private Font chooseFont(Font base, AuctionEntry ae, int col) {
    boolean hasComment = ae.getComment() != null;

    if(fixedFont == null) fixedFont = new Font("Monospaced", base.getStyle(), base.getSize());
    if(boldFont == null) boldFont = base.deriveFont(Font.BOLD);
    if(col == TableColumnController.TIME_LEFT) return fixedFont;
    if(hasComment && col == TableColumnController.ID) return boldFont;
    if(ae.isShippingOverridden() && col == TableColumnController.SHIPPING_INSURANCE) return boldFont;
    return base;
  }

  private Color snipeBidBackground(AuctionEntry ae) {
    if (ae.isMultiSniped()) {
      return ae.getMultiSnipe().getColor();
    }
    return null;
  }

  Color titleColor(AuctionEntry ae) {
    if (ae != null && ae.getHighBidder() != null) {
      if (ae.isHighBidder()) {
        if (!ae.isReserve() || ae.isReserveMet()) {
          return medBlue;
        } else {
          return darkRed;
        }
      } else {
        if (ae.getNumBidders() > 0 && (!ae.isReserve() || ae.isReserveMet())) {
          if (true) { // !_isSelling) {
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
        if (!ae.isMultiSniped()) {
          if (ae.isSnipeValid() || ae.isDutch()) {
            return darkGreen;
          }
          return darkRed;
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
    if(ae != null) {
      boolean recent = ae.getJustAdded() != 0;
      boolean isUpdating = ae.isUpdating();

      if (recent) {
        return darkGreen;
      } else if (isUpdating) {
        return darkRed;
      }
    }
    return Color.BLACK;
  }
}
