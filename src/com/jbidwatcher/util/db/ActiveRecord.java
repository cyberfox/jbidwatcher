package com.jbidwatcher.util.db;

import com.jbidwatcher.util.HashBacked;
import com.jbidwatcher.util.SoftMap;
import com.jbidwatcher.util.Record;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;

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

  public String saveDB() {
    if(!isDirty() && get("id") != null && get("id").length() != 0) return get("id");
    String id = getDatabase().insertOrUpdate(getBacking());
    commit();
    if(id != null && id.length() != 0) set("id", id); else id = get("id");
    return id;
  }

  public boolean delete() {
    String id = get("id");
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

  public static void saveCached() {
    if(sCache == null) return;

    synchronized(sCache) {
      for (Class klass : sCache.keySet()) {
        Map<String, ActiveRecord> klassCache = sCache.get(klass);
        for (ActiveRecord record : klassCache.values()) {
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
}
