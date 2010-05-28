package com.jbidwatcher.my;

import com.jbidwatcher.util.db.ActiveRecord;
import com.jbidwatcher.util.db.Table;

/**
 * User: mrs
 * Date: May 24, 2010
 * Time: 1:53:24 AM
 *
 * Track per-entry interactions with My JBidwatcher
 */
public class My extends ActiveRecord {
  //  For creating ActiveRecord instances and filling them out with stuff from the database.
  public My() { }

  public My(String identifier) {
    setString("identifier", identifier);
  }

  /**
   * Boilerplate ActiveRecord-ish stuff...
   */

  /**
   * @return The name of the table this class refers to.
   */
  protected static String getTableName() { return "my_jbidwatcher"; }

  public static My findByIdentifier(String identifier) {
    return findFirstBy("identifier", identifier);
  }

  public static My findFirstBy(String key, String value) {
    return (My) ActiveRecord.findFirstBy(My.class, key, value);
  }

  private static ThreadLocal<Table> tDB = new ThreadLocal<Table>() {
    protected synchronized Table initialValue() {
      return openDB(getTableName());
    }
  };

  @Override
  protected Table getDatabase() {
    return getRealDatabase();
  }

  public static Table getRealDatabase() {
    return tDB.get();
  }
}
