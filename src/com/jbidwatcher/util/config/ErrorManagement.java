package com.jbidwatcher.util.config;
/*
 * Copyright (c) 2000-2007, CyberFOX Software, Inc. All Rights Reserved.
 *
 * Developed by mrs (Morgan Schweers)
 */

import java.io.*;
import java.util.Date;

@SuppressWarnings({"UseOfSystemOutOrSystemErr", "UtilityClass"})
public class ErrorManagement {
  private static int MAX_BUFFER_SIZE=50000;
  private static PrintWriter _pw = null;
  private static final StringBuffer sLogBuffer = new StringBuffer();
  private static LoggerWriter lw = new LoggerWriter();

  private ErrorManagement() { }

  public static StringBuffer getLog() {
    return sLogBuffer;
  }

  private static void addLog(String s) {
    if(s == null) return;
    synchronized(sLogBuffer) {
      if(s.length() + sLogBuffer.length() > MAX_BUFFER_SIZE) {
        int newline = sLogBuffer.indexOf("\n", s.length());
        sLogBuffer.delete(0, newline);
      }
      sLogBuffer.append(s);
      sLogBuffer.append("\n");
    }
  }

  private static void init() {
    String sep = System.getProperty("file.separator");
    String home = JConfig.getHomeDirectory("jbidwatcher");

    String doLogging = JConfig.queryConfiguration("logging", "true");
    if(doLogging.equals("true")) {
      if(_pw == null) {
        try {
          File fp;
          String increment = "";
          int stepper = 1;
          do {
            fp = new File(home + sep + "errors" + increment + ".log");
            increment = "." + stepper++;
          } while(fp.exists());

          _pw = new PrintWriter(new FileOutputStream(fp));
        } catch(IOException ioe) {
          System.err.println("FAILED TO OPEN AN ERROR LOG.");
          ioe.printStackTrace();
        }
      }
    }
  }

  public static void closeLog() {
    if(_pw != null) {
      _pw.close();
      _pw = null;
    }
  }

  public static void logMessage(String msg) {
    init();
    Date log_time = new Date();

    System.err.println(log_time + ": " + msg);

    String logMsg = log_time + ": " + msg;
    addLog(logMsg);

    String doLogging = JConfig.queryConfiguration("logging", "true");
    if(doLogging.equals("true")) {
      if(_pw != null) {
        _pw.println(logMsg);
        _pw.flush();
      }
    }
  }

  public static void logDebug(String msg) {
    if(JConfig.debugging) logMessage(msg);
  }

  public static void handleDebugException(String sError, Throwable e) {
    if(JConfig.debugging) handleException(sError, e);
  }

  private static class LoggerWriter extends PrintWriter {
    public LoggerWriter() {
      super(System.out);
    }

    public void println(String x) {
      addLog(x);
    }
  }

  public static void handleException(String sError, Throwable e) {
    init();

    Date log_time = new Date();

    if(sError == null || sError.length() == 0) {
      System.err.println("[" + log_time + "]");
    } else {
      System.err.println(log_time + ": " + sError);
    }
    e.printStackTrace();

    String doLogging = JConfig.queryConfiguration("logging", "true");

    String logMsg;
    if (sError == null || sError.length() == 0) {
      logMsg = "[" + log_time + "]";
    } else {
      logMsg = log_time + ": " + sError;
    }

    addLog(logMsg);
    e.printStackTrace(lw);

    if(doLogging.equals("true")) {
      if(_pw != null) {
        _pw.println(logMsg);
        e.printStackTrace(_pw);
        _pw.flush();
      }
    }
  }

  public static void logFile(String msgtop, StringBuffer dumpsb) {
    String doLogging = JConfig.queryConfiguration("logging", "true");
    if(doLogging.equals("true")) {
      if(JConfig.debugging) {
        init();
        if(_pw != null) {
          _pw.println("+------------------------------");
          _pw.println("| " + msgtop);
          _pw.println("+------------------------------");
          if(dumpsb != null) {
            _pw.println(dumpsb);
          } else {
            _pw.println("(null)");
          }
          _pw.println("+------------end---------------");
          _pw.flush();
        }
      }
    }
  }
}
