package com.jbidwatcher.util.services;

import com.jbidwatcher.util.LogProvider;
import com.jbidwatcher.util.queue.MessageQueue;
import com.jbidwatcher.util.queue.MQFactory;
import com.jbidwatcher.util.ScrollingBuffer;

import java.util.Date;

/**
 * Created by IntelliJ IDEA.
 * User: Morgan
 * Date: Mar 9, 2008
 * Time: 4:45:46 AM
 *
 * An activity monitor that stores recent activity (mostly status messages) in a fixed-size buffer.
 */
public class ActivityMonitor implements MessageQueue.Listener, LogProvider {
  private static final int MAX_BUFFER=4096;
  private ScrollingBuffer mBuffer;
  private static ActivityMonitor sInstance;

  public ActivityMonitor() {
    mBuffer = new ScrollingBuffer(MAX_BUFFER);
  }

  public StringBuffer getLog() {
    return mBuffer.getLog();
  }

  public void messageAction(Object deQ) {
    String msg = (String)deQ;
    Date logTime = new Date();

    mBuffer.addLog(logTime + ": " + msg);
  }

  public static ActivityMonitor getInstance() {
    return sInstance;
  }

  public static void start() {
    if(sInstance == null) MQFactory.getConcrete("activity").registerListener(sInstance = new ActivityMonitor());
  }
}
