package com.jbidwatcher.util;

/**
 * Created by IntelliJ IDEA.
 * User: mrs
 * Date: 7/16/11
 * Time: 2:03 PM
 * To change this template use File | Settings | File Templates.
 */
public interface CreationObserver<T> {
  public void onCreation(T o);
}
