package com.jbidwatcher.util.queue;

import com.jbidwatcher.util.config.JConfig;

import java.util.*;

/*
 * Copyright (c) 2000-2007, CyberFOX Software, Inc. All Rights Reserved.
 *
 * Author: Morgan Schweers
 * Date: May 19, 2005
 * Time: 11:41:40 PM
 */
public class TimeQueueManager implements TimerHandler.WakeupProcess {
  protected TimeQueue mTQ;

  public TimeQueueManager() {
    mTQ = new TimeQueue();
  }

  protected class TQCarrier {
    private Object payload;
    private String destination_queue;
    private long repeatRate;
    private int repeatCount;

    public Object getPayload() { return payload; }
    public String getDestinationQueue() { return destination_queue; }
    public long getRepeatRate() { return repeatRate; }
    public long getRepeatCount() { return repeatCount; }
    public void decrementCount() { repeatCount--; }

    public TQCarrier(Object o, String s, long r, int c) {
      destination_queue = s;
      payload = o;
      repeatRate = r;
      repeatCount = c;
    }
  }

  protected long getCurrentTime() { return System.currentTimeMillis(); }

  public boolean check() {
    Object deQ;
    while( (deQ = mTQ.getAnyLessThan(getCurrentTime()+900)) != null) {
      TQCarrier interim = (TQCarrier) deQ;
      MessageQueue q = MQFactory.getConcrete(interim.getDestinationQueue());

      Object payload = interim.getPayload();
      if(payload instanceof QObject) {
        q.enqueueBean((QObject)payload);
      } else if (payload instanceof String) {
        q.enqueue((String) payload);
      } else if(q instanceof PlainMessageQueue) {
        ((PlainMessageQueue)q).enqueueObject(interim.getPayload());
      } else {
        //  Payload isn't a QObject or String, and q isn't a plainMessageQueue.
        //  Trying to submit an arbitrary object to the SwingMessageQueue?  Teh fail.
        JConfig.log().logDebug("Submitting: " + payload.toString() + " to " + q.toString() + " will probably fail.");
        q.enqueue(payload.toString());
      }
      if(interim.getRepeatRate() != 0) {
        //  If there's a positive repeat count, decrement it once.
        if(interim.getRepeatCount() > 0) {
          interim.decrementCount();
        }
        //  As long as repeat count hasn't reached zero, re-add it.
        if(interim.getRepeatCount() != 0) {
          mTQ.addEvent(getCurrentTime()+interim.getRepeatRate(), interim);
        }
      }
    }

    return false;
  }

  private TQCarrier createCarrier(Object payload, String destination, long repeat, int howmany) {
    return new TQCarrier(payload, destination, repeat, howmany);
  }

  public void add(Object payload, String destination, long when) {
    mTQ.addEvent(when, createCarrier(payload, destination, 0, 1));
  }

  public void add(Object payload, String destination, long when, long repeat) {
    mTQ.addEvent(when, createCarrier(payload, destination, repeat, -1));
  }

  public void add(Object payload, String destination, long when, long repeat, int howmany) {
    mTQ.addEvent(when, createCarrier(payload, destination, repeat, howmany));
  }

  public boolean erase(Object payload) {
    List<TimeQueue.QObject> doErase = new ArrayList<TimeQueue.QObject>();
    List current = mTQ.getUnsorted();
    boolean didErase = false;
    for(Object aCurrent : current) {
      TimeQueue.QObject tqo = (TimeQueue.QObject) aCurrent;
      TQCarrier event = (TQCarrier) tqo.getEvent();
      if (event.getPayload() == payload || event.getPayload().equals(payload)) {
        doErase.add(tqo);
        didErase = true;
      }
    }
    if(didErase) {
      for (TimeQueue.QObject delMe : doErase) {
        mTQ.erase(delMe);
      }
    }
    return didErase;
  }

  public interface Matcher {
    public boolean match(Object payload, Object queue, long when);
  }

  public boolean contains(Matcher m) {
    for(Object o : mTQ.getUnsorted()) {
      TimeQueue.QObject qo = (TimeQueue.QObject) o;
      TQCarrier carrier = (TQCarrier) qo.getEvent();
      if(m.match(carrier.getPayload(), carrier.getDestinationQueue(), qo.getTime())) return true;
    }
    return false;
  }

  public boolean contains(Object payload) {
    for(Object o : mTQ.getUnsorted()) {
      TimeQueue.QObject qo = (TimeQueue.QObject)o;
      TQCarrier carrier = (TQCarrier) qo.getEvent();
      if(payload.equals(carrier.getPayload())) return true;
    }

    return false;
  }

  public void dumpQueue(String prefix) {
    List current = mTQ.getSorted();

    if(current.isEmpty()) {
      JConfig.log().logDebug(prefix + ": queue empty");
    }

    for(Object aCurrent : current) {
      TimeQueue.QObject step = (TimeQueue.QObject) aCurrent;
      TQCarrier event = (TQCarrier) step.getEvent();
      JConfig.log().logDebug(prefix + ": Queue: " + event.getDestinationQueue());
      JConfig.log().logDebug(prefix + ": Object: [" + event.getPayload() + "]");
      JConfig.log().logDebug(prefix + ": When: " + new Date(step.getTime()));
      JConfig.log().logDebug("--");
    }
  }
}
