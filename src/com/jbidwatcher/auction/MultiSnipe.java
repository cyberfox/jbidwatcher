package com.jbidwatcher.auction;
/*
 * Copyright (c) 2000-2007, CyberFOX Software, Inc. All Rights Reserved.
 *
 * Developed by mrs (Morgan Schweers)
 */

import com.jbidwatcher.util.ErrorManagement;
import com.jbidwatcher.util.db.AuctionDB;

import java.util.*;
import java.util.List;
import java.awt.*;

/**
 *  MultiSnipe class
 */
public class MultiSnipe extends ActiveRecord {
  private Color _bgColor;
  private LinkedList<AuctionEntry> auctionEntriesInThisGroup = new LinkedList<AuctionEntry>();
  private static final int HEX_BASE = 16;

  private void setValues(Color groupColor, com.jbidwatcher.util.Currency snipeValue, long id, boolean subtractShipping) {
    _bgColor = groupColor;
    setString("color", makeRGB(groupColor));
    setMonetary("default_bid", snipeValue);
    setBoolean("subtract_shipping", subtractShipping);
    //  Basically, the identifier is a long value based on
    //  the time at which it's created.
    setString("identifier", Long.toString(id));
  }

  /** @noinspection NonConstantStringShouldBeStringBuffer
   * @param groupColor - The color for the group.
   * @return - A string consisting of the hex equivalent for the color provided.
   */
  //  Construct a standard HTML 'bgcolor="#ffffff"' style color string.
  public static String makeRGB(Color groupColor) {
    String red = Integer.toString(groupColor.getRed(), HEX_BASE);
    if (red.length() == 1) red = '0' + red;
    String green = Integer.toString(groupColor.getGreen(), HEX_BASE);
    if (green.length() == 1) green = '0' + green;
    String blue = Integer.toString(groupColor.getBlue(), HEX_BASE);
    if (blue.length() == 1) blue = '0' + blue;

    return red + green + blue;
  }

  public static Color reverseColor(String colorText) {
    int red = Integer.parseInt(colorText.substring(0, 2), HEX_BASE);
    int green = Integer.parseInt(colorText.substring(2, 4), HEX_BASE);
    int blue = Integer.parseInt(colorText.substring(4, 6), HEX_BASE);

    return new Color(red, green, blue);
  }

  public MultiSnipe(String groupColor, com.jbidwatcher.util.Currency snipeValue, long id, boolean subtractShipping) {
    Color rgb = reverseColor(groupColor);
    setString("color", groupColor);
    setValues(rgb, snipeValue, id, subtractShipping);
  }

  public MultiSnipe(Color groupColor, com.jbidwatcher.util.Currency snipeValue, boolean subtractShipping) {
    setValues(groupColor, snipeValue, System.currentTimeMillis(), subtractShipping);
  }

  public Color getColor() { return _bgColor; }
  public String getColorString() { return getString("color"); }
  public com.jbidwatcher.util.Currency getSnipeValue(AuctionEntry ae) {
    if(ae != null && getBoolean("subtract_shipping")) {
      com.jbidwatcher.util.Currency shipping = ae.getShippingWithInsurance();
      if(shipping != null && !shipping.isNull()) {
        try {
          return getMonetary("default_bid").subtract(shipping);
        } catch (com.jbidwatcher.util.Currency.CurrencyTypeException e) {
          //  It's not relevant (although odd), we fall through to the return.
        }
      }
    }

    return getMonetary("default_bid");
  }

  public long getIdentifier() {
    return Long.parseLong(getString("identifier", "0"));
  }

  public void add(AuctionEntry aeNew) {
    auctionEntriesInThisGroup.add(aeNew);
  }

  public void remove(AuctionEntry aeOld) {
    auctionEntriesInThisGroup.remove(aeOld);
  }

  /**
   *  Right now it doesn't use the passed in parameter.  I'm not sure
   *  what it would do with it, but it seems right to pass it in.
   *
   * param ae - The auction that was won.
   */
  public void setWonAuction(/*AuctionEntry ae*/) {
    List<AuctionEntry> oldEntries = auctionEntriesInThisGroup;
    auctionEntriesInThisGroup = new LinkedList<AuctionEntry>();

    for (AuctionEntry aeFromList : oldEntries) {
      ErrorManagement.logDebug("Cancelling Snipe for: " + aeFromList.getTitle() + '(' + aeFromList.getIdentifier() + ')');
      //  TODO --  Fix this up; this calls back into here, for the remove() function.  This needs to be seperated somehow.
      aeFromList.cancelSnipe(false);
    }
    oldEntries.clear();
  }

  public boolean anyEarlier(AuctionEntry firingEntry) {
    for (AuctionEntry ae : auctionEntriesInThisGroup) {
      //  If any auction entry in the list ends BEFORE the one we're
      //  checking, then we really don't want to do anything until
      //  it's no longer in the list.
      if (ae.getEndDate().before(firingEntry.getEndDate())) return true;
    }

    return false;
  }

  public static boolean isSafeMultiSnipe(AuctionEntry ae1, AuctionEntry ae2) {
    long end1 = ae1.getEndDate().getTime();
    long end2 = ae2.getEndDate().getTime();
    long snipe1 = end1 - ae1.getSnipeTime();
    long snipe2 = end2 - ae2.getSnipeTime();

    /*
     * If they end at the same time, or A1 ends first, but within
     * {snipetime} seconds of A2, or A2 ends first, but within
     * {snipetime} seconds of A1, then it is NOT safe.
     */
    return !((end1 == end2) ||
             ((end1 < end2) && (end1 >= snipe2)) ||
             ((end2 < end1) && (end2 >= snipe1)));

  }

  public boolean isSafeToAdd(AuctionEntry ae) {
    for (AuctionEntry fromList : auctionEntriesInThisGroup) {
      //  It's always safe to 'add' an entry that already exists,
      //  it'll just not get added.
      //noinspection ObjectEquality
      if (fromList != ae) {
        if (!isSafeMultiSnipe(fromList, ae)) return false;
      }
    }

    return true;
  }

  public boolean subtractShipping() {
    return getBoolean("subtract_shipping");
  }

  /*************************/
  /* Database access stuff */
  /*************************/

  private static AuctionDB sDB = null;

  protected static String getTableName() { return "multisnipes"; }

  protected AuctionDB getDatabase() {
    if (sDB == null) {
      sDB = openDB(getTableName());
    }
    return sDB;
  }

  public static MultiSnipe findFirstBy(String key, String value) {
    return (MultiSnipe) ActiveRecord.findFirstBy(MultiSnipe.class, key, value);
  }
}
