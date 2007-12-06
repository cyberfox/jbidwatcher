package com.jbidwatcher.auction;

import com.jbidwatcher.util.HashBacked;
import com.jbidwatcher.util.db.AuctionDB;
import com.jbidwatcher.util.db.DBRecord;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

  protected static ActiveRecord findFirstBy(Class klass, String key, String value) {
    ActiveRecord cached = cached(klass, key, value);
    if(cached != null) return cached;

    ActiveRecord found;
    try {
      found = (ActiveRecord)klass.newInstance();
    } catch (Exception e) {
      throw new RuntimeException("Can't instantiate " + klass.getName(), e);
    }
    DBRecord result = getTable(found).findFirstBy(key, value);
    if (result != null && result.size() != 0) {
      found.setBacking(result);
    } else {
      found = null;
    }
    if(found != null) cache(klass, key, value, found);
    return found;
  }

  private final static Map<Class, Map<String, ActiveRecord>> sCache = new HashMap<Class, Map<String, ActiveRecord>>();

  public static Map<String, ActiveRecord> getCache(Class klass) {
    Map<String, ActiveRecord> klassCache = sCache.get(klass);
    if(klassCache == null) {
      klassCache = new HashMap<String, ActiveRecord>();
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
    System.err.println("klass == " + this.getClass().getName());
    cache(klass, "id", getString("id"), this);
  }

  public static int precache(Class klass, String key) {
    List<DBRecord> results = null;
    try {
      ActiveRecord o = (ActiveRecord) klass.newInstance();
      results = getTable(o).findAll();
      boolean first = true;
      for (DBRecord record : results) {
        ActiveRecord row = (ActiveRecord) klass.newInstance();
        row.setBacking(record);
        cache(klass, key, row.get(key), row);
        if(first) {
          first = false;
          for(String colName : record.keySet()) System.err.print(colName + "\t");
          System.err.println();
        }
        for(String colName : record.keySet()) System.err.print(record.get(colName) + "\t");
        System.err.println();
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
            System.err.println("id = " + record.getId());
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
