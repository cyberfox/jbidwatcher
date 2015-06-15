package com.jbidwatcher.util.db;

import com.jbidwatcher.util.HashBacked;
import com.jbidwatcher.util.Record;

import java.util.*;

/**
 * Provides utility methods for database-backed objects.
 *
 * User: Morgan
 * Date: Oct 21, 2007
 * Time: 1:54:46 PM
 */
public abstract class ActiveRecord extends HashBacked {
  private static boolean sDBDisabled = false;
  private static ArrayList<Table> sTables = new ArrayList<Table>();

  public static void disableDatabase() {
    sDBDisabled = true;
  }

  public static Table openDB(String tableName) {
    if (sDBDisabled || tableName == null) return null;

    Table db;
    try {
      db = new Table(tableName);
      sTables.add(db);
    } catch (Exception e) {
      throw new RuntimeException("Can't access the " + tableName + " database table", e);
    }
    return db;
  }

  public static void shutdown() {
    Set<Database> s = new HashSet<Database>();
    for(Table t : sTables) {
      s.add(t.shutdown());
    }
    for(Database db : s) {
      db.shutdown();
    }
  }

  protected static Table getTable(Object o) {
    ActiveRecord record = (ActiveRecord) o;
    return record.getDatabase();
  }

  protected abstract Table getDatabase();

  /**
   * This returns the count of entries in the table for an ActiveRecord descendant.
   *
   * @param klass - The class to count for.
   *
   * @return - The count of entries in the database table associated with the given class.
   */
  public static int count(Class klass) {
    if(sDBDisabled) return 0;
    return getExemplar(klass).getDatabase().count();
  }

  public void commit() {
    if(!sDBDisabled) getDatabase().commit();
  }

  /**
   * Upcase first letter, and each letter after an '_', and remove all '_'...
   *
   * @param className - The lowercase version of the name to be converted.
   *
   * @return - The capitalized version of the word provided.  i.e. 'auction' becomes Auction.
   */
  private String classify(String className) {
    return String.valueOf(className.charAt(0)).toUpperCase() + className.substring(1);
  }

  public String saveDB() {
    if(sDBDisabled) return "0";

    Table db = getDatabase();
    if(db.hasColumn("currency")) {
      setString("currency", getDefaultCurrency().fullCurrencyName());
    }
    if(!isDirty() && get("id") != null && get("id").length() != 0) return get("id");
    String id = getDatabase().insertOrUpdate(getBacking());
    commit();
    if(id != null && id.length() != 0) set("id", id); else id = get("id");
    clearDirty();
    return id;
  }

  public static List<? extends ActiveRecord> findAllBy(Class klass, String key, String value) {
    return findAllBy(klass, key, value, null);
  }

  protected static List<? extends ActiveRecord> findAllMulti(Class klass, String[] keys, String[] values, String order) {
    if (sDBDisabled) return new LinkedList<ActiveRecord>();
    ActiveRecord found = getExemplar(klass);
    List<Record> results = getTable(found).findAllMulti(keys, values, order);
    return convertResultsToList(klass, results);
  }

  protected static List<? extends ActiveRecord> findAllComparator(Class klass, String key, String comparator, String value, String order) {
    if (sDBDisabled) return new LinkedList<ActiveRecord>();
    ActiveRecord found = getExemplar(klass);
    List<Record> results = getTable(found).findAllComparator(key, comparator, value, order);
    return convertResultsToList(klass, results);
  }

  protected static List<? extends ActiveRecord> findAllBy(Class klass, String key, String value, String order) {
    if(sDBDisabled) return new LinkedList<ActiveRecord>();
    ActiveRecord found = getExemplar(klass);
    List<Record> results = getTable(found).findAll(key, value, order);
    return convertResultsToList(klass, results);
  }

  public static List<? extends ActiveRecord> findAllByPrepared(Class klass, String query, String... parameters) {
    if(sDBDisabled) return new LinkedList<ActiveRecord>();
    ActiveRecord found = getExemplar(klass);
    List<Record> results = getTable(found).findAllPrepared(query, 0, parameters);
    return convertResultsToList(klass, results);
  }

  public static List<? extends ActiveRecord> findAllBySQL(Class klass, String query) {
    return findAllBySQL(klass, query, 0);
  }

  public static List<? extends ActiveRecord> findAllBySQL(Class klass, String query, int count) {
    if(sDBDisabled) return new LinkedList<ActiveRecord>();
    ActiveRecord found = getExemplar(klass);
    List<Record> results = getTable(found).findAll(query, count);
    return convertResultsToList(klass, results);
  }

  private static List<ActiveRecord> convertResultsToList(Class klass, List<Record> results) {
    List<ActiveRecord> rval = new ArrayList<ActiveRecord>();

    try {
      for (Record record : results) {
        ActiveRecord row = (ActiveRecord) klass.newInstance();
        row.setBacking(record);
        rval.add(row);
      }

      return rval;
    } catch (InstantiationException e) {
      e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
    } catch (IllegalAccessException e) {
      e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
    }

    return null;
  }

  protected static ActiveRecord getExemplar(Class klass) {
    ActiveRecord found;
    try {
      found = (ActiveRecord) klass.newInstance();
    } catch (Exception e) {
      throw new RuntimeException("Can't instantiate " + klass.getName(), e);
    }
    return found;
  }

  public Integer getId() { return getInteger("id"); }

  public static String makeCommaList(List<? extends ActiveRecord> records) {
    StringBuffer ids = new StringBuffer("");

    boolean first = true;
    for(ActiveRecord id : records) {
      if(!first) {
        ids.append(", ");
      }
      ids.append(id.getId());
      first = false;
    }
    return ids.toString();
  }

  public boolean delete() {
    if(sDBDisabled) return false;
    String id = get("id");
    return id != null && getDatabase().delete(Integer.parseInt(id));
  }

  public static ActiveRecord findFirstBySQL(Class klass, String query) {
    if(sDBDisabled) return null;
    ActiveRecord found = getExemplar(klass);
    Table t = getTable(found);
    Record result = t.findFirstBy(query);
    if (result != null && !result.isEmpty()) {
      found.setBacking(result);
    } else {
      found = null;
    }
    return found;
  }

  protected static ActiveRecord findFirstByUncached(Class klass, String key, String value) {
    if(sDBDisabled) return null;
    ActiveRecord found = getExemplar(klass);
    Table t = getTable(found);
    Record result = t.findFirstBy(key, value);
    if (result != null && !result.isEmpty()) {
      found.setBacking(result);
    } else {
      found = null;
    }
    return found;
  }

  public static ActiveRecord findFirstBy(Class klass, String key, String value) {
    return findFirstByUncached(klass, key, value);
  }

  public String getUnique() {
    return get("id");
  }
}
