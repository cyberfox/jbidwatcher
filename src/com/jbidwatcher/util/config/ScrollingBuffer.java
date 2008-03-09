package com.jbidwatcher.util.config;

import java.io.PrintWriter;

/**
 * Created by IntelliJ IDEA.
 * User: Morgan
 * Date: Mar 9, 2008
 * Time: 12:57:51 AM
 *
 * Provide a fixed-size, scrolling StringBuffer for logging.
 */
public class ScrollingBuffer {
  private final StringBuffer sLogBuffer;
  private LoggerWriter mWriter = new LoggerWriter();
  private int mMaxSize;

  public class LoggerWriter extends PrintWriter {
    public LoggerWriter() {
      super(System.out);
    }

    public void println(String x) {
      addLog(x);
    }
  }

  public ScrollingBuffer(int max_buffer_size) {
    mMaxSize = max_buffer_size;
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

  public void addStackTrace(Throwable e) {
    e.printStackTrace(mWriter);
  }
}
