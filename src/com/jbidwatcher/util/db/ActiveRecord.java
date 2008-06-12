package com.jbidwatcher.util.db;

import com.jbidwatcher.util.HashBacked;
import com.jbidwatcher.util.SoftMap;
import com.jbidwatcher.util.Record;

import java.util.*;
import java.lang.reflect.Field;
import java.lang.reflect.Member;

/**
 * Provides utility methods for database-backed objects.
 *
 * User: Morgan
 * Date: Oct 21, 2007
 * Time: 1:54:46 PM
 * To change this template use File | Settings | File Templates.
 */
public abstract class ActiveRecord extends HashBacked {
  protected static Table openDB(String tableName) {
    if (tableName == null) return null;

    Table db;
    try {
      db = new Table(tableName);
    } catch (Exception e) {
      throw new RuntimeException("Can't access the " + tableName + " database table", e);
    }
    return db;
  }

  private static Table getTable(Object o) {
    ActiveRecord record = (ActiveRecord) o;
    return record.getDatabase();
  }

  protected abstract Table getDatabase();

  /**
   * This returns the count of entries in the table.
   *
   * @return - The count of entries in the database table.
   */
  public int count() {
    return getDatabase().count();
  }

  public void commit() {
    getDatabase().commit();
  }

  private void saveAssociations() {
    Set<String> colNames=getDatabase().getColumns();
    for(String name : colNames) {
      if(name.endsWith("_id")) {
        String className = name.substring(0, name.length()-3);
        String member = "m" + classify(className);

        // TODO -- Inspect for 'member', instanceof ActiveRecord.
        // TODO -- set("#{name}", member.saveDB())
      }
    }
  }

  //  Upcase first letter, and each letter after an '_', and remove all '_'...
  private String classify(String className) {
    return String.valueOf(className.charAt(0)).toUpperCase() + className.substring(1);
  }

  //  TODO -- Look for columns of type: {foo}_id
  //  For each of those, introspect for 'm{Foo}'.
  //  For each non-null of those, call 'saveDB' on it.
  //  Store the result of that call as '{foo}_id'.
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

  public boolean delete(Class klass) {
    String id = get("id");
    uncache(klass, "id", id);
    return id != null && getDatabase().delete(Integer.parseInt(id));
  }

  protected static ActiveRecord findFirstBy(Class klass, String key, String value) {
    ActiveRecord cached = cached(klass, key, value);
    if(cached != null) return cached;

    return findFirstByUncached(klass, key, value);
  }

  private static ActiveRecord findFirstByUncached(Class klass, String key, String value) {
    ActiveRecord found;
    try {
      found = (ActiveRecord)klass.newInstance();
    } catch (Exception e) {
      throw new RuntimeException("Can't instantiate " + klass.getName(), e);
    }
    Record result = getTable(found).findFirstBy(key, value);
    if (result != null && !result.isEmpty()) {
      found.setBacking(result);
    } else {
      found = null;
    }
    if(found != null) cache(klass, key, value, found);
    return found;
  }

  protected static List<ActiveRecord> findAllBy(Class klass, String key, String value) {
    return findAllBy(klass, key, value, null);
  }

  protected static List<ActiveRecord> findAllBy(Class klass, String key, String value, String order) {
    ActiveRecord found;
    try {
      found = (ActiveRecord) klass.newInstance();
    } catch (Exception e) {
      throw new RuntimeException("Can't instantiate " + klass.getName(), e);
    }

    List<Record> results = getTable(found).findAll(key, value, order);
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

  private final static Map<Class, SoftMap<String, ActiveRecord>> sCache=new HashMap<Class, SoftMap<String, ActiveRecord>>();

  public static Map<String, ActiveRecord> getCache(final Class klass) {
    SoftMap<String, ActiveRecord> klassCache = sCache.get(klass);
    if(klassCache == null) {
      klassCache = new SoftMap<String, ActiveRecord>() {
        public ActiveRecord reload(Object key) {
          String[] pair = ((String)key).split(":");
          return findFirstByUncached(klass, pair[0], pair[1]);
        }
      };
      sCache.put(klass, klassCache);
    }
    return klassCache;
  }

  private void uncache(Class klass, String key, String value) {
    String combined = key + ':' + value;
    getCache(klass).remove(combined);
  }

  private static ActiveRecord cached(Class klass, String key, String value) {
    String combined = key + ':' + value;
    return getCache(klass).get(combined);
  }

  protected static void cache(Class klass, String key, String value, ActiveRecord result) {
    String combined = key + ':' + value;
    getCache(klass).put(combined, result);
  }

  protected void cache(Class klass) {
    cache(klass, "id", getString("id"), this);
  }

  public static int precache(Class klass, String key) {
    List<Record> results = null;
    try {
      ActiveRecord o = (ActiveRecord) klass.newInstance();
      results = getTable(o).findAll();
//      boolean first = true;
      for (Record record : results) {
        ActiveRecord row = (ActiveRecord) klass.newInstance();
        row.setBacking(record);
        cache(klass, key, row.get(key), row);
      }
    } catch (Exception e) {
      //  Ignore, as this is just for pre-caching...
    }
    return results == null ? 0 : results.size();
  }

  public static int precacheBySQL(Class klass, String sql) {
    List<Record> results = null;
    try {
      ActiveRecord o = (ActiveRecord) klass.newInstance();
      results = getTable(o).findAll(sql);

      for (Record record : results) {
        ActiveRecord row = (ActiveRecord) klass.newInstance();
        row.setBacking(record);
        cache(klass, "id", row.get("id"), row);
      }
    } catch (Exception e) {
      //  Ignore, as this is just for pre-caching...
    }
    return results == null ? 0 : results.size();
  }

  public static void saveCached() {
    if(sCache == null) return;

    synchronized(sCache) {
      for (Class klass : sCache.keySet()) {
        Map<String, ActiveRecord> klassCache = sCache.get(klass);
        Collection<ActiveRecord> values = klassCache.values();
        if(values != null) for (ActiveRecord record : values) {
          if (record != null && record.isDirty()) {
            record.saveDB();
          }
        }
      }
      sCache.clear();
    }
  }

  public static int precache(Class klass) {
    return precache(klass, "id");
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
}
