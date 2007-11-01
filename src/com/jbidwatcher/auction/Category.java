package com.jbidwatcher.auction;

import com.jbidwatcher.util.db.DBRecord;
import com.jbidwatcher.util.db.AuctionDB;

/**
 * Category DB accessor.
 *
 * User: Morgan
 * Date: Oct 21, 2007
 * Time: 12:47:22 PM
 * To change this template use File | Settings | File Templates.
 */
public class Category extends ActiveRecord {
  public Category(String name) {
    setString("name", name);
  }

  private Category(DBRecord r) {
    setBacking(r);
  }

  public static Category findFirstByName(String name) {
    return findFirstBy("name", name);
  }

  public static Category findOrCreateByName(String name) {
    Category c = findFirstByName(name);
    if (c == null) {
      c = create(name);
    }

    return c;
  }

  public static Category create(String newCategory) {
    Category c = new Category(newCategory);
    String id = c.saveDB();
    if(id != null) c.setInteger("id", Integer.parseInt(id));
    return c;
  }

  public String getName() { return getString("name"); }
  public int getId() { return getInteger("id"); }

  private static AuctionDB sDB;
  protected static String getTableName() { return "categories"; }
  protected AuctionDB getDatabase() {
    if (sDB == null) {
      sDB = openDB(getTableName());
    }
    return sDB;
  }
  public static Category findFirstBy(String key, String value) {
    return (Category) findFirstBy(Category.class, key, value);
  }
}
