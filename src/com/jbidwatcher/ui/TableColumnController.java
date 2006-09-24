package com.jbidwatcher.ui;
/**
 * Created by IntelliJ IDEA.
 * User: Morgan Schweers
 * Date: Jun 3, 2005
 * Time: 2:11:32 AM
 * To change this template use File | Settings | File Templates.
 */
import java.util.HashMap;
import java.util.Map;
import java.util.Iterator;
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

  public static final int COLUMN_COUNT=21;
  private static Map m_column_map;

  private TableColumnController() {
    m_column_map = new HashMap(COLUMN_COUNT*3);

    m_column_map.put(new Integer(ID), "Number");
    m_column_map.put(new Integer(CUR_BID), "Current");
    m_column_map.put(new Integer(SNIPE_OR_MAX), "Max");
    m_column_map.put(new Integer(TIME_LEFT), "Time left");
    m_column_map.put(new Integer(TITLE), "Description");
    m_column_map.put(new Integer(STATUS), "Status");
    m_column_map.put(new Integer(SELLER), "Seller");
    m_column_map.put(new Integer(SHIPPING_INSURANCE), "Shipping");
    m_column_map.put(new Integer(BIDDER), "High Bidder");
    m_column_map.put(new Integer(MAX), "Max Bid");
    m_column_map.put(new Integer(SNIPE), "Snipe Bid");
    m_column_map.put(new Integer(COMMENT), "Comment");
    m_column_map.put(new Integer(END_DATE), "End Date");
    m_column_map.put(new Integer(FIXED_PRICE), "Buy Price");
    m_column_map.put(new Integer(SELLER_FEEDBACK), "Feedback");
    m_column_map.put(new Integer(SELLER_POSITIVE_FEEDBACK), "Feedback %");
    m_column_map.put(new Integer(ITEM_LOCATION), "Location");
    m_column_map.put(new Integer(BIDCOUNT), "# of bids");
    m_column_map.put(new Integer(JUSTPRICE), "Price");
    m_column_map.put(new Integer(CUR_TOTAL), "Total");
    m_column_map.put(new Integer(SNIPE_TOTAL), "Snipe Max");
  }

  public static TableColumnController getInstance() {
    return m_instance;
  }

  public String getColumnName(int index) {
    Integer col_index = new Integer(index);
    return (String)m_column_map.get(col_index);
  }

  public int getColumnNumber(String colName) {
    if(colName.equals("Ended at")) colName = "End Date";

    for (Iterator it = m_column_map.entrySet().iterator(); it.hasNext();) {
      Map.Entry entry = (Map.Entry) it.next();
      if(entry.getValue().equals(colName)) {
        return ((Integer)entry.getKey()).intValue();
      }
    }

    return -1;
  }

  public Collection getColumnNames() {
    return m_column_map.values();
  }
}
