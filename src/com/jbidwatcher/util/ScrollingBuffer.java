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
      makeRoom(s);
      sLogBuffer.append(s);
      sLogBuffer.append("\n");
    }
  }

  private void makeRoom(String s) {
    if(s.length() + sLogBuffer.length() > mMaxSize) {
      int newline = sLogBuffer.indexOf("\n", s.length());
      if(newline >= 0) {
        // Truncate AFTER the newline, so the first line isn't blank.
        newline++;
      } else {
        // If there's no newline from s.length on, clear everything.
        newline = sLogBuffer.length();
      }
      sLogBuffer.delete(0, newline);
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
