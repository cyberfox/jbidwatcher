package com.jbidwatcher.ui.table;
/*
 * Copyright (c) 2000-2007, CyberFOX Software, Inc. All Rights Reserved.
 *
 * Developed by mrs (Morgan Schweers)
 */

/**
 * Created by IntelliJ IDEA.
 * User: Morgan Schweers
 * Date: Jun 3, 2005
 * Time: 2:11:32 AM
 *
 * Contains the basic list of columns for potential display, and their display-names.
 */
import com.jbidwatcher.scripting.Scripting;
import com.jbidwatcher.util.config.JConfig;

import java.util.HashMap;
import java.util.Map;
import java.util.Collection;

public class TableColumnController {
  private static TableColumnController m_instance = new TableColumnController();

  public static final int ID=0;
  public static final int CUR_BID=1;
  public static final int SNIPE_OR_MAX=2;
  public static final int TIME_LEFT=3;
  public static final int TITLE=4;
  public static final int STATUS=5;
  public static final int SELLER=6;
  public static final int SHIPPING_INSURANCE=7;
  public static final int BIDDER=8;
  public static final int MAX=9;
  public static final int SNIPE=10;
  public static final int COMMENT=11;
  public static final int END_DATE=12;
  public static final int FIXED_PRICE=13;
  public static final int SELLER_FEEDBACK=14;
  public static final int SELLER_POSITIVE_FEEDBACK=15;
  public static final int ITEM_LOCATION=16;
  public static final int BIDCOUNT=17;
  public static final int JUSTPRICE=18;
  public static final int CUR_TOTAL=19;
  public static final int SNIPE_TOTAL=20;
  public static final int THUMBNAIL = 21;

  public static final int MAX_FIXED_COLUMN=21;

  private static int columnCount =22;
  private static Map<Integer, String> m_column_map;

  public static int columnCount() {
    return columnCount;
  }

  private TableColumnController() {
    m_column_map = new HashMap<Integer, String>(columnCount *3);

    m_column_map.put(ID, "Number");
    m_column_map.put(CUR_BID, "Current");
    m_column_map.put(SNIPE_OR_MAX, "Max");
    m_column_map.put(TIME_LEFT, "Time left");
    m_column_map.put(TITLE, "Description");
    m_column_map.put(STATUS, "Status");
    m_column_map.put(SELLER, "Seller");
    m_column_map.put(SHIPPING_INSURANCE, "Shipping");
    m_column_map.put(BIDDER, "High Bidder");
    m_column_map.put(MAX, "Max Bid");
    m_column_map.put(SNIPE, "Snipe Bid");
    m_column_map.put(COMMENT, "Comment");
    m_column_map.put(END_DATE, "End Date");
    m_column_map.put(FIXED_PRICE, "Buy Price");
    m_column_map.put(SELLER_FEEDBACK, "Feedback");
    m_column_map.put(SELLER_POSITIVE_FEEDBACK, "Feedback %");
    m_column_map.put(ITEM_LOCATION, "Location");
    m_column_map.put(BIDCOUNT, "# of bids");
    m_column_map.put(JUSTPRICE, "Price");
    m_column_map.put(CUR_TOTAL, "Total");
    m_column_map.put(SNIPE_TOTAL, "Snipe Max");
    m_column_map.put(THUMBNAIL, "Thumbnail");
  }

  public static TableColumnController getInstance() {
    return m_instance;
  }

  public String getColumnName(int index) {
    return m_column_map.get(index);
  }

  public int getColumnNumber(String colName) {
    if(colName.equals("Ended at")) colName = "End Date";

    for (Map.Entry<Integer, String> entry : m_column_map.entrySet()) {
      if (entry.getValue().equals(colName)) {
        return entry.getKey();
      }
    }

    return -1;
  }

  public Collection<String> getColumnNames() {
    return m_column_map.values();
  }

  public void addColumn(String name) {
    m_column_map.put(columnCount++, name);
  }

  public String customColumn(int j, Object auctionEntry) {
    if(JConfig.scriptingEnabled()) return (String)Scripting.rubyMethod("custom_column", getColumnName(j), auctionEntry);
    return "";
  }
}
