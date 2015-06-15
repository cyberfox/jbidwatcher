package com.jbidwatcher.auction;

import com.jbidwatcher.util.db.ActiveRecord;
import com.jbidwatcher.util.db.Table;

/**
 * Created by mrs on 6/14/15.
 */
public class EntryTable {
  private static ThreadLocal<Table> tDB = new ThreadLocal<Table>() {
    protected synchronized Table initialValue() {
      return ActiveRecord.openDB(getTableName());
    }
  };

  public static Table getRealDatabase() {
    return tDB.get();
  }

  protected static String getTableName() { return "entries"; }
}
