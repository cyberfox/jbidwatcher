package com.jbidwatcher.auction;


import com.jbidwatcher.util.db.Table;
import com.jbidwatcher.util.db.ActiveRecord;

import java.util.List;
import java.util.ArrayList;

/**
 * Category DB accessor.
 *
 * User: Morgan
 * Date: Oct 21, 2007
 * Time: 12:47:22 PM
 * To change this template use File | Settings | File Templates.
 */
public class Category extends ActiveRecord
{
  public Category() {
  }

  public Category(String name) {
    setString("name", name);
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

  protected static String getTableName() { return "categories"; }

  private static ThreadLocal<Table> tDB = new ThreadLocal<Table>() {
    protected synchronized Table initialValue() {
      return openDB(getTableName());
    }
  };

  public static Table getRealDatabase() {
    return tDB.get();
  }

  protected Table getDatabase() {
    return getRealDatabase();
  }

  public static Category findFirstBy(String key, String value) {
    return (Category) findFirstBy(Category.class, key, value);
  }

  public static List<Category> all() {
    return (List<Category>) findAllBySQL(Category.class, "SELECT * FROM " + getTableName());
  }

  public static List<String> categories() {
    List<Category> categories = (List<Category>) findAllBySQL(Category.class, "SELECT name FROM " + getTableName());
    if(categories.isEmpty()) return null;

    List<String> results = new ArrayList<String>(categories.size());

    for(Category category : categories) {
      results.add(category.getName());
    }

    return results;
  }
}
