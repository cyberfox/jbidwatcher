package com.cyberfox.util.config;

import java.io.File;

/**
 * Created by IntelliJ IDEA.
 * User: Morgan
 * Date: Dec 20, 2008
 * Time: 3:18:48 PM
 *
 * A basic logging interface, so we can have a null and normal logger.
 */
public interface LoggerInterface {
  void logMessage(String msg);

  void logDebug(String msg);

  void handleException(String sError, Throwable e);

  void logVerboseDebug(String msg);

  void handleDebugException(String sError, Throwable e);

  void logFile(String msgtop, StringBuffer dumpsb);

  void dump2File(String fname, StringBuffer sb);

  File closeLog();
  boolean openLog(File fp);

  void pause();
  void resume();


  void addHandler(ErrorHandler eh);

  void dumpFile(StringBuffer loadedPage);

  String getLog();
}
