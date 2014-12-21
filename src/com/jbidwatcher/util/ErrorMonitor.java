package com.jbidwatcher.util;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.jbidwatcher.util.config.JConfig;

/**
 * Created by IntelliJ IDEA.
 * User: Morgan
 * Date: Jun 17, 2008
 * Time: 2:42:26 PM
 *
 * A small utility class to monitor the error log, via a ScrollingBuffer.
 */
@Singleton
public class ErrorMonitor implements LogProvider {
  private final static int MAX_BUFFER_SIZE = 50000;
  private ScrollingBuffer mBuffer;

  @Inject
  private ErrorMonitor() {
    mBuffer = new ScrollingBuffer(MAX_BUFFER_SIZE);
    JConfig.log().addHandler(mBuffer);
  }

  public StringBuffer getLog() {
    return mBuffer.getLog();
  }
}
