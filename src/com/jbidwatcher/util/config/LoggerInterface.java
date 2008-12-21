package com.jbidwatcher.util.config;

/**
 * Created by IntelliJ IDEA.
 * User: Morgan
 * Date: Dec 20, 2008
 * Time: 3:18:48 PM
 * To change this template use File | Settings | File Templates.
 */
public interface LoggerInterface {
  void logMessage(String msg);

  void logDebug(String msg);

  void handleException(String sError, Throwable e);

  void logVerboseDebug(String msg);

  void handleDebugException(String sError, Throwable e);

  void logFile(String msgtop, StringBuffer dumpsb);

  void dump2File(String fname, StringBuffer sb);

  public void closeLog();

  public void addHandler(ErrorHandler eh);
}
