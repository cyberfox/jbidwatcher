package com.jbidwatcher.util;
/*
 * Copyright (c) 2000-2005 CyberFOX Software, Inc. All Rights Reserved.
 *
 * This library is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Library General Public License as published
 * by the Free Software Foundation; either version 2 of the License, or (at
 * your option) any later version.
 *
 * This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Library General Public License for more
 * details.
 *
 * You should have received a copy of the GNU Library General Public License
 * along with this library; if not, write to the
 *  Free Software Foundation, Inc.
 *  59 Temple Place
 *  Suite 330
 *  Boston, MA 02111-1307
 *  USA
 */

import com.jbidwatcher.config.JConfig;

import java.io.*;
import java.util.Date;

public class ErrorManagement {
  static PrintWriter _pw = null;

  private static void init() {
    String sep = System.getProperty("file.separator");
    String home = JConfig.getHomeDirectory("jbidwatcher");

    String doLogging = JConfig.queryConfiguration("logging", "true");
    if(doLogging.equals("true")) {
      if(_pw == null) {
        int stepper = 1;
        String increment = "";

        try {
          File fp;
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

    String doLogging = JConfig.queryConfiguration("logging", "true");
    if(doLogging.equals("true")) {
      if(_pw != null) {
        _pw.println(log_time + ": " + msg);
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

  public static void handleException(String sError, Throwable e) {
    init();

    Date log_time = new Date();

    if(sError == null || sError.equals("")) {
      System.err.println("[" + log_time + "]");
    } else {
      System.err.println(log_time + ": " + sError);
    }
    e.printStackTrace();

    String doLogging = JConfig.queryConfiguration("logging", "true");
    if(doLogging.equals("true")) {
      if(_pw != null) {

        if(sError == null || sError.equals("")) {
          _pw.println("[" + log_time + "]");
        } else {
          _pw.println(log_time + ": " + sError);
        }
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
