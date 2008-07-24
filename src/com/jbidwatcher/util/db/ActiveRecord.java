package com.jbidwatcher.util.db;

import com.jbidwatcher.util.HashBacked;
import com.jbidwatcher.util.Record;

import java.util.Set;
import java.util.List;
import java.util.ArrayList;
import java.lang.reflect.Field;

/**
 * Provides utility methods for database-backed objects.
 *
 * User: Morgan
 * Date: Oct 21, 2007
 * Time: 1:54:46 PM
 */
public abstract class ActiveRecord extends HashBacked {
  private static ArrayList<Table> sTables = new ArrayList<Table>();
  protected static Table openDB(String tableName) {
    if (tableName == null) return null;

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
    for(Table t : sTables) {
      t.shutdown();
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
    return getExemplar(klass).getDatabase().count();
  }

  public void commit() {
    getDatabase().commit();
  }

  /**
   * Look for columns with the name: {foo}_id.  For each of those,
   * introspect for 'm{Foo}'.  For each non-null of those, call 'saveDB'
   * on it. Store the result of that call in the '{foo}_id' column.
   */
  private void saveAssociations() {
    Set<String> colNames=getDatabase().getColumns();
    for(String name : colNames) {
      if(name.endsWith("_id")) {
        String className = name.substring(0, name.length()-3);
        String member = "m" + classify(className);

        try {
          Field classMember = getClass().getField(member);
          Object memberVariable = classMember.get(this);
          if(memberVariable instanceof ActiveRecord) {
            set(name, ((ActiveRecord)memberVariable).saveDB());
          }
        } catch (NoSuchFieldException e) {
          System.err.println("No such field: " + member);
        } catch (IllegalAccessException e) {
          System.err.println("Can't access field: " + member);
        }
      }
    }
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
    if(getDatabase().hasColumn("currency")) {
      setString("currency", getDefaultCurrency().fullCurrencyName());
    }
    if(!isDirty() && get("id") != null && get("id").length() != 0) return get("id");
    String id = getDatabase().insertOrUpdate(getBacking());
    commit();
    if(id != null && id.length() != 0) set("id", id); else id = get("id");
    clearDirty();
    return id;
  }

  protected static List<ActiveRecord> findAllBy(Class klass, String key, String value) {
    return findAllBy(klass, key, value, null);
  }

  protected static List<ActiveRecord> findAllBy(Class klass, String key, String value, String order) {
    ActiveRecord found = getExemplar(klass);
    List<Record> results = getTable(found).findAll(key, value, order);
    return convertResultsToList(klass, results);
  }

  protected static List<? extends ActiveRecord> findAllBySQL(Class klass, String query) {
    ActiveRecord found = getExemplar(klass);
    List<Record> results = getTable(found).findAll(query);
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

  protected static String makeCommaList(List<? extends ActiveRecord> records) {
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

  public boolean delete(Class klass) {
    String id = get("id");
    ActiveRecordCache.uncache(klass, "id", id);
    return id != null && getDatabase().delete(Integer.parseInt(id));
  }

  protected static ActiveRecord findFirstBySQL(Class klass, String query) {
    ActiveRecord found = getExemplar(klass);
    Record result = getTable(found).findFirstBy(query);
    if (result != null && !result.isEmpty()) {
      found.setBacking(result);
    } else {
      found = null;
    }
    return found;
  }

  protected static ActiveRecord findFirstByUncached(Class klass, String key, String value) {
    ActiveRecord found = getExemplar(klass);
    Record result = getTable(found).findFirstBy(key, value);
    if (result != null && !result.isEmpty()) {
      found.setBacking(result);
    } else {
      found = null;
    }
    return found;
  }

  protected static ActiveRecord findFirstBy(Class klass, String key, String value) {
    ActiveRecord cached = ActiveRecordCache.cached(klass, key, value);
    if(cached != null) return cached;

    cached = findFirstByUncached(klass, key, value);
    if (cached != null) ActiveRecordCache.cache(klass, key, value, cached);

    return cached;
  }
}
