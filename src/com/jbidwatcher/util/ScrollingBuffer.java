package com.jbidwatcher.util;

import com.cyberfox.util.config.ErrorHandler;

/**
 * Created by IntelliJ IDEA.
 * User: Morgan
 * Date: Mar 9, 2008
 * Time: 12:57:51 AM
 *
 * Provide a fixed-size, scrolling StringBuffer for logging.
 */
public class ScrollingBuffer implements ErrorHandler {
  private final StringBuffer sLogBuffer;
  private int mMaxSize;

  public ScrollingBuffer(int maxBufferSize) {
    mMaxSize = maxBufferSize;
    sLogBuffer = new StringBuffer(mMaxSize);
  }

  public StringBuffer getLog() {
    return sLogBuffer;
  }

  public void addLog(String s) {
    if(s == null) return;
    synchronized(sLogBuffer) {
      if(s.length() + sLogBuffer.length() > mMaxSize) {
        int newline = sLogBuffer.indexOf("\n", s.length());
        sLogBuffer.delete(0, newline);
      }
      sLogBuffer.append(s);
      sLogBuffer.append("\n");
    }
  }

  public void exception(String log, String message, String trace) {
    addLog(log);
    addLog(message);
    addLog(trace);
  }

  public void close() {
    sLogBuffer.append("--- Log file was reset at this point ---\n");
  }
}
