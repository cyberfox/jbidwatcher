package com.jbidwatcher.auction;

import com.jbidwatcher.util.db.ActiveRecord;
import com.jbidwatcher.util.db.Table;

import java.util.Date;

/**
 * Created by IntelliJ IDEA.
 * User: mrs
 * Date: Jun 12, 2008
 * Time: 12:35:22 AM
 * To change this template use File | Settings | File Templates.
 */
public class DeletedEntry extends ActiveRecord {
  public DeletedEntry(String identifier) {
    setString("identifier", identifier);
    setDate("created_at", new Date());
  }

  private static Table sDB = null;
  protected static String getTableName() { return "deleted"; }
  protected Table getDatabase() {
    if(sDB == null) {
      sDB = openDB(getTableName());
    }
    return sDB;
  }
}
