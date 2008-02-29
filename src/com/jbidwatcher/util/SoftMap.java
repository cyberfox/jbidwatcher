package com.jbidwatcher.util;

import java.util.Map;
import java.util.Set;
import java.util.Collection;
import java.util.HashMap;
import java.lang.ref.SoftReference;

/**
 * User: mrs
 * Date: Feb 27, 2008
 * Time: 5:04:16 PM
 * 
 * To change this template use File | Settings | File Templates.
 */
public abstract class SoftMap<K, V> implements Map<K, V>
{
  private Map<K, SoftReference<V>> cacheMap = new HashMap<K, SoftReference<V>>();

  //  Delegations
  public int size() { return cacheMap.size(); }
  public boolean isEmpty() { return cacheMap.isEmpty(); }
  public boolean containsKey(Object key) { return cacheMap.containsKey(key); }
  public boolean containsValue(Object value) { return cacheMap.containsValue(value); }
  public V put(K key, V value) { return cacheMap.put(key, new SoftReference<V>(value)).get(); }
  public void putAll(Map t) { cacheMap.putAll(t); }
  public void clear() { cacheMap.clear(); }
  public Set keySet() { return cacheMap.keySet(); }

  //  Things which need special handling...
  public V remove(Object key) {
    V rval = null;
    SoftReference<V> element = cacheMap.remove(key);
    if(element != null) {
      rval = element.get();
      if(rval == null) {
        reload(key);
      }
      return rval;
    }

    return rval;
  }

  abstract V reload(Object key);

  public Collection values() { return null; }
  public Set entrySet() { return null; }
  public V get(Object key) {
    return null;
  }
}
