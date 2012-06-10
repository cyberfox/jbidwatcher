package com.jbidwatcher.util.queue;

/**
 * Created by IntelliJ IDEA.
 * User: mrs
 * Date: Feb 4, 2008
 * Time: 1:00:19 AM
 *
 * A queue of events to be placed on other queues, at timed intervals or immediately.
 *
 * The backbone of the message/event based architecture of JBidwatcher.
 */
public class SuperQueue {
  private TimeQueueManager mTQM = new TimeQueueManager();
  private static SuperQueue mInstance = null;

  public static SuperQueue getInstance() {
    if(mInstance == null) mInstance = new SuperQueue();

    return mInstance;
  }

  public TimeQueueManager getQueue() { return mTQM; }

  public void preQueue(Object payload, String queueName, long when) {
    mTQM.add(payload, queueName, when);
  }

  public void preQueue(Object payload, String queueName, long when, long repeat) {
    mTQM.add(payload, queueName, when, repeat);
  }

  public void remove(Object payload) {
    mTQM.erase(payload);
  }

  public TimerHandler start() {
    TimerHandler timeQueue = new TimerHandler(mTQM);
    timeQueue.setName("SuperQueue");
    timeQueue.start();

    return timeQueue;
  }
}
