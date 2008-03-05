package com.jbidwatcher.search;

import com.jbidwatcher.util.HashBacked;
import com.jbidwatcher.util.Record;
import com.jbidwatcher.util.db.Table;
import com.jbidwatcher.util.xml.XMLElement;

import java.util.Map;
import java.util.HashMap;

/**
 * User: mrs
 * Date: Oct 7, 2007
 * Time: 10:20:25 PM
 * To change this template use File | Settings | File Templates.
 */
public class Category extends HashBacked {
  private static Map<String, Category> categories = null;
  private static Table sDB = null;

  private Category(String name) {
    setString("name", name);
  }

  private Category(Record base) {
    setBacking(base);
  }

  public String getName() {
    return getString("name");
  }

  public static Category findCategory(String newCategory) {
    establishCategoryDatabase();
    if(sDB == null) try { sDB = new Table("categories"); } catch(Exception e) { sDB = null; }

    Category rval;
    if(categories.containsKey(newCategory)) {
      rval = categories.get(newCategory);
    } else {
      Record existing = sDB.findByColumn("name", newCategory);
      if(existing != null) {
        rval = new Category(existing);
      } else {
        rval = new Category(newCategory);
      }

      categories.put(newCategory, rval);
    }
    return rval;
  }

  private static void establishCategoryDatabase() {
    if (categories == null) {
      categories = new HashMap<String, Category>();
    }
  }

  protected void handleTag(int i, XMLElement curElement) { }

  protected String[] getTags() {
    return null;
  }

  public XMLElement toXML() {
    return null;
  }
}
