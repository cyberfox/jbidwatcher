package com.cyberfox.util.config;

/**
 * Created by IntelliJ IDEA.
 * User: Morgan
 * Date: Jun 17, 2008
 * Time: 2:18:03 PM
 *
 * An abstract error handler, to be added to the ErrorManagement's notification list.
 */
public interface ErrorHandler {
  void addLog(String s);

  void exception(String log, String message, String trace);

  void close();
}
