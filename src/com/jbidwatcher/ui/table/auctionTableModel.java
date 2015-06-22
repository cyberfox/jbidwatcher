package com.jbidwatcher.ui.table;
/*
 * Copyright (c) 2000-2007, CyberFOX Software, Inc. All Rights Reserved.
 *
 * Developed by mrs (Morgan Schweers)
 */

import com.jbidwatcher.auction.*;
import com.jbidwatcher.util.Comparison;
import com.jbidwatcher.util.Constants;
import com.jbidwatcher.util.config.JConfig;
import com.jbidwatcher.util.Currency;
import com.jbidwatcher.util.IconFactory;
import com.jbidwatcher.util.xml.XMLElement;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import java.awt.Image;
import java.text.SimpleDateFormat;
import java.util.*;

public class auctionTableModel extends AbstractTableModel
{
  private static final String neverBid = "--";
  private final MultiSnipeManager multiManager;
  private AuctionList dispList;
  private Date futureForever = new Date(Long.MAX_VALUE);

  public int getRowCount() { return dispList.size(); }
  public int getColumnCount() { return TableColumnController.columnCount(); }
  public String getColumnName(int aColumn) { return TableColumnController.getInstance().getColumnName(aColumn); }

  public void delete(int row) {
    dispList.remove(row);
    fireTableRowsDeleted(row, row);
  }

  public synchronized int findRow(Comparison c) {
    for(int i=0; i<getRowCount(); i++) {
      if(c.match(getValueAt(i, -1))) return i;
    }
    return -1;
  }

  public synchronized int findRow(Object o) {
    List<Object> cache = new ArrayList<Object>();
    for(int i=0; i<getRowCount(); i++) {
      Object curStep = getValueAt(i, -1);
      if(curStep == o) return i;
      cache.add(curStep);
    }

    for(int i=0; i<cache.size(); i++) {
      if(cache.get(i).equals(o)) return i;
    }
    return -1;
  }

  public boolean delete(Object o) {
    int row = findRow(o);
    if(row != -1) {
      delete(row);
      fireTableRowsDeleted(row, row);
    }
    return row != -1;
  }

  public int insert(Object o) {
    dispList.add((AuctionEntry)o);
    int row = findRow(o);
    fireTableRowsInserted(row, row);
    return dispList.size()-1;
  }

  //  All columns return strings...
  public Class getColumnClass(int aColumn) { if(aColumn != 5) return String.class; return Icon.class; }

  private static final ImageIcon dummyIcon = new ImageIcon(JConfig.getResource("/icons/white_ball.gif"));
  private static final ImageIcon greenIcon = new ImageIcon(JConfig.getResource("/icons/green_ball.gif"));
  //  private final static ImageIcon blueIcon = new ImageIcon(JConfig.getResource("/icons/blue_ball.gif"));
  private static final ImageIcon binIcon = new ImageIcon(JConfig.getResource("/icons/bin_item.gif"));
  private static final ImageIcon resIcon = new ImageIcon(JConfig.getResource("/icons/unmet_reserve.gif"));
  private static final ImageIcon resMetIcon = new ImageIcon(JConfig.getResource("/icons/met_reserve.gif"));
  private static final ImageIcon imageIcon = new ImageIcon(JConfig.getResource("/icons/camera.gif"));
  private static final ImageIcon commentIcon=new ImageIcon(JConfig.getResource("/icons/note3.gif"));
  private static final ImageIcon winningIcon=new ImageIcon(JConfig.getResource("/icons/winning.gif"));
  private static final ImageIcon invalidIcon=new ImageIcon(JConfig.getResource("/icons/invalid.png"));
  private static final ImageIcon deletedIcon=new ImageIcon(JConfig.getResource("/icons/deleted.png"));
  private static final ImageIcon paypalIcon =new ImageIcon(JConfig.getResource("/icons/paypal16x16.gif"));
  //  private final static ImageIcon boughtIcon = new ImageIcon(JConfig.getResource("/icons/blue_check_ball.gif"));
  //  private final static ImageIcon soldIcon = new ImageIcon(JConfig.getResource("/icons/green_check_ball.gif"));

  private Object getDummyValueAtColumn(int j) {
    switch(j) {
      // For the 'get the whole object', all we can safely do is
      // return null.
      case -1:
        return null;
      case TableColumnController.CUR_BID:
      case TableColumnController.SNIPE_OR_MAX:
      case TableColumnController.SHIPPING_INSURANCE:
      case TableColumnController.MAX:
      case TableColumnController.SNIPE:
      case TableColumnController.FIXED_PRICE:
      case TableColumnController.JUSTPRICE:
      case TableColumnController.CUR_TOTAL:
        return Currency.NoValue();
      case TableColumnController.TIME_LEFT:
      case TableColumnController.END_DATE:
        return futureForever;
      case TableColumnController.STATUS:
      case TableColumnController.THUMBNAIL:
        return dummyIcon;
      case TableColumnController.ID:
      case TableColumnController.TITLE:
      case TableColumnController.SELLER:
      case TableColumnController.COMMENT:
      case TableColumnController.BIDDER:
      case TableColumnController.SELLER_POSITIVE_FEEDBACK:
      case TableColumnController.ITEM_LOCATION:
      case TableColumnController.BIDCOUNT:
      case TableColumnController.SNIPE_TOTAL:
      default:
        return("");
    }
  }

  private int buildEntryFlags(AuctionEntry ae) {
    //  This shouldn't happen, but to be safe...
    if(ae == null) return 1;
    return ae.getFlags();
  }

  private ImageIcon getEntryIcon(AuctionEntry ae) {
    ImageIcon ret_icon = null;
    if(ae.isFixed()) ret_icon = null;

    if (ae.getHighBidder() != null) {
      if (ae.isHighBidder()) {
        ret_icon = winningIcon;
      } else {
        if (ae.isSeller() && ae.getNumBidders() > 0 &&
            (!ae.isReserve() || ae.isReserveMet())) {
          ret_icon = greenIcon;
        }
      }
    }
    if(!ae.getBuyNow().isNull()) {
      ret_icon = IconFactory.getCombination(ret_icon, binIcon);
    }
    if(ae.isReserve()) {
      if(ae.isReserveMet()) {
        ret_icon = IconFactory.getCombination(ret_icon, resMetIcon);
      } else {
        ret_icon = IconFactory.getCombination(ret_icon, resIcon);
      }
    }
    if(ae.getThumbnail() != null) {
      ret_icon = IconFactory.getCombination(ret_icon, imageIcon);
    }
    if(ae.getComment() != null) {
      ret_icon = IconFactory.getCombination(ret_icon, commentIcon);
    }
    if(ae.isInvalid()) {
      ret_icon = IconFactory.getCombination(ret_icon, invalidIcon);
    }
    if(ae.isDeleted()) {
      ret_icon = IconFactory.getCombination(ret_icon, deletedIcon);
    }
    if(ae.hasPaypal()) {
      ret_icon = IconFactory.getCombination(ret_icon, paypalIcon);
    }
    return ret_icon;
  }

  public Object getSortByValueAt(int i, int j) {
    JConfig.log().logMessage("Trying to sort by value at (" + i + ", " + j + ")");
    try {
      AuctionEntry entry = dispList.get(i);
      AuctionSortable sortBy = new AuctionSortable(entry);

      switch(j) {
        case -1: return entry;
        case TableColumnController.ID: return sortBy.getId();
        case TableColumnController.CUR_BID: return sortBy.getCurrentBid();
        case TableColumnController.SNIPE_OR_MAX: return sortBy.getSnipeOrMax();
        case TableColumnController.TIME_LEFT: return entry.getEndDate();
        case TableColumnController.TITLE: return entry.getTitle();
        case TableColumnController.STATUS: return buildEntryFlags(entry);
        case TableColumnController.THUMBNAIL: return 0;
        case TableColumnController.SELLER: return entry.getSellerName();
        case TableColumnController.FIXED_PRICE: return sortBy.getFixedPrice();
        case TableColumnController.SHIPPING_INSURANCE: return sortBy.getShippingInsurance();
        case TableColumnController.BIDDER: return entry.getHighBidder();
        case TableColumnController.MAX: return sortBy.getMax();
        case TableColumnController.SNIPE: return sortBy.getSnipe();
        case TableColumnController.COMMENT: return sortBy.getComment();
        case TableColumnController.END_DATE: return entry.getEndDate();
        case TableColumnController.SELLER_FEEDBACK: return sortBy.getSellerFeedback();
        case TableColumnController.ITEM_LOCATION: return entry.getItemLocation();
        case TableColumnController.BIDCOUNT: return entry.getNumBidders();
        case TableColumnController.JUSTPRICE: return entry.getUSCurBid();
        case TableColumnController.SELLER_POSITIVE_FEEDBACK: return sortBy.getSellerPositiveFeedback();
        case TableColumnController.CUR_TOTAL: return sortBy.getCurrentTotal();
        case TableColumnController.SNIPE_TOTAL: return sortBy.getSnipeTotal();
        //  Columns beyond the MAX_FIXED_COLUMN are custom columns, or a bug.
        default: {
          if(j > TableColumnController.MAX_FIXED_COLUMN && j < TableColumnController.columnCount()) {
            return TableColumnController.getInstance().customColumn(j, entry);
          }
          //  This should never happen, but to be safe...
          return "";
        }
      }
    } catch(ArrayIndexOutOfBoundsException ignored) {
      return getDummyValueAtColumn(j);
    }
  }

  private String formatSnipeAndBid(AuctionEntry aEntry) {
    String errorNote = "";
    if(aEntry.getErrorPage() != null) errorNote="*";

    if(aEntry.isSniped()) {
      return formatSnipe(aEntry, errorNote);
    }
    if(aEntry.isBidOn()) {
      return formatBid(aEntry, errorNote);
    }
    if(aEntry.snipeCancelled() && aEntry.isComplete()) {
      return errorNote + '(' + aEntry.getCancelledSnipe() + ')';
    }
    return neverBid;
  }

  private String formatBid(AuctionEntry aEntry, String errorNote) {
    String bidCount = "";
    return errorNote + aEntry.getBid().toString() + bidCount;
  }

  private String formatSnipe(AuctionEntry aEntry, String errorNote) {
    String snipeCount = "";

    MultiSnipe ms = multiManager.getForAuctionIdentifier(aEntry.getIdentifier());
    if(ms != null) {
      if(aEntry.isSnipeValid()) {
        return errorNote + "Multi: " + aEntry.getSnipeAmount() + snipeCount;
      } else {
        return errorNote + "Multi: (" + aEntry.getSnipeAmount() + snipeCount + ')';
      }
    } else {
      if(aEntry.isSnipeValid()) {
        return errorNote + aEntry.getSnipeAmount().toString() + snipeCount;
      } else {
        return errorNote + '(' + aEntry.getSnipeAmount() + snipeCount + ')';
      }
    }
  }

  private String formatTotalSnipe(AuctionEntry aEntry, String errorNote) {
    if(!aEntry.isSniped()) return "--";
    Currency shipping = aEntry.getShippingWithInsurance();
    if (shipping.getCurrencyType() == Currency.NONE || aEntry.getSnipeAmount().getCurrencyType() == Currency.NONE) {
      return "--"; // shipping not set so cannot add up values
    }

    Currency totalSnipe;

    try {
      totalSnipe = aEntry.getSnipeAmount().add(shipping);
    } catch (Currency.CurrencyTypeException e) {
      /* Should never happen, since we've checked the currency already.  */
      JConfig.log().handleException("Currency addition threw a bad currency exception, which should be very difficult to cause to happen.", e); //$NON-NLS-1$
      return "--";
    }

    MultiSnipe ms = multiManager.getForAuctionIdentifier(aEntry.getIdentifier());
    if (ms != null) {
      if (aEntry.isSnipeValid()) {
        return errorNote + "Multi: " + totalSnipe;
      } else {
        return errorNote + "Multi: (" + totalSnipe + ')';
      }
    } else {
      if (aEntry.isSnipeValid()) {
        return errorNote + totalSnipe.toString();
      } else {
        return errorNote + '(' + totalSnipe + ')';
      }
    }
  }

  static Map<String, ImageIcon> iconCache = new HashMap<String, ImageIcon>();

  private static Map<String, Seller> sellers = new HashMap<String, Seller>();
  private Seller getSeller(String sellerId) {
    Seller seller;
    if(sellers.containsKey(sellerId)) {
      seller = sellers.get(sellerId);
    } else {
      seller = Seller.findFirstBy("id", sellerId);
      sellers.put(sellerId, seller);
    }
    return seller;
  }

  public Object getValueAt(int rowIndex, int columnIndex) {
    try {
      AuctionEntry aEntry = dispList.get(rowIndex);
      if(columnIndex == -1)
        return aEntry;
      if(aEntry == null) {
        dispList.remove(rowIndex);
        return "*";
      }
      String errorNote = aEntry.getErrorPage()==null?"":"*";
      Seller seller = getSeller(aEntry.getSellerId());
      switch(columnIndex) {
        case TableColumnController.ID: return aEntry.getIdentifier();
        case TableColumnController.CUR_BID:
          return currentBid(aEntry);
        case TableColumnController.SNIPE_OR_MAX: return formatSnipeAndBid(aEntry);
        case TableColumnController.MAX: return aEntry.isBidOn()?formatBid(aEntry, errorNote):neverBid;
        case TableColumnController.SNIPE:
          return snipeColumn(aEntry, errorNote);
        case TableColumnController.TIME_LEFT:
          return timeLeftColumn(aEntry);
        case TableColumnController.END_DATE:
          return endDateColumn(aEntry);
        case TableColumnController.TITLE: return XMLElement.decodeString(aEntry.getTitle());
        case TableColumnController.STATUS: return getEntryIcon(aEntry);
        case TableColumnController.THUMBNAIL:
          return thumbnailColumn(aEntry);
        case TableColumnController.SELLER:
          return aEntry.getSellerName();
        case TableColumnController.COMMENT:
          String comment = aEntry.getComment();
          return(comment==null?"":comment);
        case TableColumnController.BIDDER:
          String bidder = aEntry.getHighBidder();
          if(bidder != null && bidder.length() != 0) return bidder;
          return "--";
        case TableColumnController.FIXED_PRICE:
          Currency bin = aEntry.getBuyNow();
          if(bin.isNull()) return "--";
          return bin;
        case TableColumnController.SHIPPING_INSURANCE:
          Currency ship = aEntry.getShippingWithInsurance();
          if(ship.isNull()) return "--";
          return ship;
        case TableColumnController.ITEM_LOCATION:
          return aEntry.getItemLocation();
        case TableColumnController.BIDCOUNT:
          if(aEntry.getNumBidders() < 0) return "(FP)";
          return Integer.toString(aEntry.getNumBidders());
        case TableColumnController.JUSTPRICE:
          return aEntry.getCurrentPrice();
        case TableColumnController.SELLER_FEEDBACK:
          return seller.getFeedback();
        case TableColumnController.SELLER_POSITIVE_FEEDBACK:
          String fbp = seller.getPositivePercentage();
          return (fbp == null || fbp.length() == 0)?"--":fbp;
        case TableColumnController.CUR_TOTAL:
          return priceWithShippingColumn(aEntry);
        case TableColumnController.SNIPE_TOTAL:
          return formatTotalSnipe(aEntry, errorNote);
        default: {
          if(columnIndex > TableColumnController.MAX_FIXED_COLUMN && columnIndex < TableColumnController.columnCount()) {
            return TableColumnController.getInstance().customColumn(columnIndex, aEntry);
          }
          return "";
        }
      }
    } catch(ArrayIndexOutOfBoundsException aioobe) {
      return(getDummyValueAtColumn(columnIndex));
    }
  }

  private Object priceWithShippingColumn(AuctionEntry aEntry) {Currency shipping = aEntry.getShippingWithInsurance();
    if(shipping.getCurrencyType() == Currency.NONE) {
      return "--"; // shipping not set so cannot add up values
    }
    try {
      return aEntry.getCurrentPrice().add(shipping);
    } catch (Currency.CurrencyTypeException e) {
      JConfig.log().handleException("Currency addition threw a bad currency exception, which is odd...", e); //$NON-NLS-1$
    }
    return "--";
  }

  private Object thumbnailColumn(AuctionEntry aEntry) {
    String thumb = aEntry.getThumbnail();
    if (thumb != null) {
      if(iconCache.containsKey(thumb)) return iconCache.get(thumb);
      thumb = thumb.replaceAll("file:", "");
      ImageIcon base = new ImageIcon(thumb);
      int h = base.getIconHeight();
      int w = base.getIconWidth();
      if (h <= 64 && w <= 64) {
        h = -1;
        w = -1;
      }
      if (h != -1 && w != -1) {
        if (h > w) {
          h = 64;
          w = -1;
        } else if (w > h) {
          w = 64;
          h = -1;
        } else if (h == w) {
          h = 64;
          w = -1;
        }
      }
      ImageIcon thumbIcon = new ImageIcon(base.getImage().getScaledInstance(w, h, Image.SCALE_SMOOTH));
      iconCache.put(thumb, thumbIcon);
      return thumbIcon;
    } else return dummyIcon;
  }

  private Object endDateColumn(AuctionEntry aEntry) {
    if (aEntry.getEndDate() == null || aEntry.getEndDate().equals(Constants.FAR_FUTURE))
      return "N/A";
    SimpleDateFormat fmt = new SimpleDateFormat("dd-MMM-yy HH:mm:ss zzz");
    return fmt.format(aEntry.getEndDate());
  }

  private Object timeLeftColumn(AuctionEntry aEntry) {
    if (aEntry.getEndDate() == null || aEntry.getEndDate().equals(Constants.FAR_FUTURE))
      return "N/A";
    String endTime = aEntry.getTimeLeft();
    if(endTime.equals(AuctionEntry.endedAuction)) {
      SimpleDateFormat fmt = new SimpleDateFormat("dd-MMM-yy HH:mm:ss zzz");
      endTime = fmt.format(aEntry.getEndDate());
      if(!aEntry.isComplete()) {
        endTime = "<html><body color=\"red\">" + endTime + "</body></html>";
      }
    }
    return endTime;
  }

  private Object snipeColumn(AuctionEntry aEntry, String errorNote) {
    if (aEntry.isSniped()) {
      return formatSnipe(aEntry, errorNote);
    }
    if(aEntry.snipeCancelled() && aEntry.isComplete()) {
      return errorNote + '(' + aEntry.getCancelledSnipe() + ')';
    }

    return neverBid;
  }

  private Object currentBid(AuctionEntry aEntry) {Currency curPrice = aEntry.getCurrentPrice();
    if(aEntry.isFixed()) {
      return curPrice + " (FP" + ((aEntry.getQuantity() > 1) ? " x " + aEntry.getQuantity() + ")" : ")");
    } else {
      return curPrice + " (" + Integer.toString(aEntry.getNumBidders()) + ')';
    }
  }

  public auctionTableModel(MultiSnipeManager multiManager, AuctionList inList) {
    this.multiManager = multiManager;
    dispList = inList;
  }

  public boolean isCellEditable(int row, int column) {
    return false;
  }
}