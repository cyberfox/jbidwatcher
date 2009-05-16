package com.jbidwatcher.util;

/**
 * Created by IntelliJ IDEA.
* User: mrs
* Date: May 16, 2009
* Time: 3:20:08 PM
* To change this template use File | Settings | File Templates.
*/
public class Pair<K,V> {
  private K mFirst;
  private V mLast;

  public Pair() {
    mFirst = null;
    mLast = null;
  }

  public Pair(K k, V v) {
    mFirst = k;
    mLast = v;
  }

  public K getFirst() {
    return mFirst;
  }

  public V getLast() {
    return mLast;
  }
}
