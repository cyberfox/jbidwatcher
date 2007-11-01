package com.jbidwatcher.auction;

import com.jbidwatcher.util.HashBacked;
import com.jbidwatcher.util.db.AuctionDB;
import com.jbidwatcher.util.db.DBRecord;

/**
 * Provides utility methods for database-backed objects.
 *
 * User: Morgan
 * Date: Oct 21, 2007
 * Time: 1:54:46 PM
 * To change this template use File | Settings | File Templates.
 */
public abstract class ActiveRecord extends HashBacked {
  protected static AuctionDB openDB(String tableName) {
    AuctionDB db;
    if (tableName == null) return null;

    try {
      db = new AuctionDB(tableName);
    } catch (Exception e) {
      throw new RuntimeException("Can't access the " + tableName + " database table", e);
    }
    return db;
  }

  private static AuctionDB getTable(Object o) {
    ActiveRecord record = (ActiveRecord) o;
    return record.getDatabase();
  }

  protected abstract AuctionDB getDatabase();

  /**
   * This returns the count of auction entries.
   *
   * @return - The count of entries in the database table.
   */
  public int count() {
    return getDatabase().count();
  }

  public String saveDB() {
    return getDatabase().insertOrUpdate(getBacking());
  }

  protected static Object findFirstBy(Class c, String key, String value) {
    Object found;
    try {
      found = c.newInstance();
    } catch (Exception e) {
      throw new RuntimeException("Can't instantiate " + c.getName(), e);
    }
    DBRecord result = getTable(found).findFirstBy(key, value);
    if (result != null) {
      ((ActiveRecord)found).setBacking(result);
    }
    return found;
  }
}
