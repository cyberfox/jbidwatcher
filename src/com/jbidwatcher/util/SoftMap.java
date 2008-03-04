package com.jbidwatcher.util;

import java.util.*;
import java.lang.ref.SoftReference;
import java.lang.ref.ReferenceQueue;

/**
 * User: mrs
 * Date: Feb 27, 2008
 * Time: 5:04:16 PM
 * 
 * To change this template use File | Settings | File Templates.
 */
public abstract class SoftMap<K, V> implements Map<K, V> {
  private Map<K, SoftReference<V>> cacheMap = new HashMap<K, SoftReference<V>>();

  //  Delegations
  public int size() { return cacheMap.size(); }
  public boolean isEmpty() { return cacheMap.isEmpty(); }
  public boolean containsKey(Object key) { return cacheMap.containsKey(key); }
  public boolean containsValue(Object value) { return cacheMap.containsValue(value); }
  public V put(K key, V value) {
    V old = null;
    SoftReference<V> old_ref = cacheMap.put(key, new SoftReference<V>(value));
    if(old_ref != null) {
      old = old_ref.get();
      if(old == null) {
        old = reload(key);
      }
    }
    return old_ref==null ? null : old;
  }

  public void putAll(Map t) { cacheMap.putAll(t); }
  public void clear() { cacheMap.clear(); }
  public Set<K> keySet() { return cacheMap.keySet(); }

  //  Things which need special handling...
  public V remove(Object key) {
    V rval = null;
    SoftReference<V> element = cacheMap.remove(key);
    if(element != null) {
      rval = element.get();
      if(rval == null) {
        rval = reload(key);
        cacheMap.put((K)key, new SoftReference<V>(rval));
      }
      return rval;
    }

    return rval;
  }

  public Collection<V> values() {
    HashSet<V> values = null;
    for(Map.Entry<K, SoftReference<V>> entry : cacheMap.entrySet()) {
      if(values == null) values = new HashSet<V>();
      V stepValue = entry.getValue().get();
      if(stepValue == null) stepValue = reload(entry.getKey());
      values.add(stepValue);
    }
    return values;
  }

  public Set<Map.Entry<K, V>> entrySet() {
    HashSet<Map.Entry<K, V>> values = null;
    for(Map.Entry<K, SoftReference<V>> entry : cacheMap.entrySet()) {
      if(values == null) values = new HashSet<Map.Entry<K, V>>();
      V stepValue = entry.getValue().get();
      if(stepValue == null) stepValue = reload(entry.getKey());
      values.add(new SimpleEntry<K, V>(entry.getKey(), stepValue));
    }
    return values;
  }

  public V get(Object key) {
    V value = null;
    SoftReference<V> entry = cacheMap.get(key);
    if(entry != null) {
      value = entry.get();
      if(value == null) {
        value = reload(key);
      }
    }

    return value;
  }

  public abstract V reload(Object key);
}
