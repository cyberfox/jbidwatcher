package com.jbidwatcher.util;

import com.jbidwatcher.util.config.JConfig;

/**
 * Created by IntelliJ IDEA.
 * User: Morgan
 * Date: Jun 17, 2008
 * Time: 2:42:26 PM
 *
 * A small utility class to monitor the error log, via a ScrollingBuffer.
 */
public class ErrorMonitor implements LogProvider {
  private final static int MAX_BUFFER_SIZE = 50000;
  private static ErrorMonitor sInstance;
  private ScrollingBuffer mBuffer;

  static {
    if(sInstance == null) sInstance = new ErrorMonitor();
  }

  public static ErrorMonitor getInstance() {
    if(sInstance == null) sInstance = new ErrorMonitor();
    return sInstance;
  }

  private ErrorMonitor() {
    mBuffer = new ScrollingBuffer(MAX_BUFFER_SIZE);
    JConfig.log().addHandler(mBuffer);
  }

  public StringBuffer getLog() {
    return mBuffer.getLog();
  }
}
