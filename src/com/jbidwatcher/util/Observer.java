package com.jbidwatcher.util;

/**
 * Created by IntelliJ IDEA.
 * User: mrs
 * Date: 7/16/11
 * Time: 2:03 PM
 * To change this template use File | Settings | File Templates.
 */
public abstract class Observer<T> {
  public void afterCreate(T o) { }
  public void afterSave(T o) { }
}
