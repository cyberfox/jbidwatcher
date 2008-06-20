package com.jbidwatcher.util.db;

import com.jbidwatcher.util.SoftMap;
import com.jbidwatcher.util.Record;

import java.util.*;

/**
 * Created by IntelliJ IDEA.
 * User: Morgan
 * Date: Jun 19, 2008
 * Time: 7:46:41 PM
 *
 * Adds rudimentary caching to the ActiveRecord objects.
 */
public abstract class ActiveRecordCache extends ActiveRecord {
  protected static ActiveRecord findFirstByUncached(Class klass, String key, String value) {
    ActiveRecord found = getExemplar(klass);
    Record result = getTable(found).findFirstBy(key, value);
    if (result != null && !result.isEmpty()) {
      found.setBacking(result);
    } else {
      found = null;
    }
    if(found != null) cache(klass, key, value, found);
    return found;
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

  public static void uncache(Class klass, String key, String value) {
    String combined = key + ':' + value;
    getCache(klass).remove(combined);
  }

  protected static ActiveRecord cached(Class klass, String key, String value) {
    String combined = key + ':' + value;
    return getCache(klass).get(combined);
  }

  public static void cache(Class klass, String key, String value, ActiveRecord result) {
    String combined = key + ':' + value;
    getCache(klass).put(combined, result);
  }

  public static void cache(ActiveRecord obj) {
    cache(obj.getClass(), "id", obj.getString("id"), obj);
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

  public static int precacheBySQL(Class klass, String sql, String... columns) {
    List<Record> results = null;
    try {
      ActiveRecord o = (ActiveRecord) klass.newInstance();
      results = getTable(o).findAll(sql);

      for (Record record : results) {
        ActiveRecord row = (ActiveRecord) klass.newInstance();
        row.setBacking(record);
        if(columns == null) {
          cache(klass, "id", row.get("id"), row);
        } else {
          for (String col : columns) {
            cache(klass, col, row.get(col), row);
          }
        }
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

}
