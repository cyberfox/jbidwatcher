package com.jbidwatcher.util.config;

/**
 * Created by IntelliJ IDEA.
 * User: Morgan
 * Date: Jun 17, 2008
 * Time: 2:18:03 PM
 * To change this template use File | Settings | File Templates.
 */
public interface ErrorHandler {
  void addLog(String s);

  void exception(String log, String message, String trace);
}
