package com.jbidwatcher.util;

/**
 * Created by IntelliJ IDEA.
 * User: Morgan
 * Date: Apr 6, 2008
 * Time: 1:32:41 PM
 *
 * Simple class to manage blocking doing updates while a snipe or bid is going off.
 */
public class UpdateBlocker {
  private static volatile boolean isBlocked =false;

  public static void startBlocking() { isBlocked = true; }

  public static void endBlocking() { isBlocked = false; }

  public static boolean isBlocked() { return isBlocked; }
}
