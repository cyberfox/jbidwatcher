package com.jbidwatcher.queue;
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

import com.jbidwatcher.TimerHandler;

import java.util.*;

/**
 * Author: Morgan Schweers
 * Date: May 19, 2005
 * Time: 11:41:40 PM
 */
public class TimeQueueManager implements TimerHandler.WakeupProcess {
  private TimeQueue m_tq = new TimeQueue();

  private class TQCarrier {
    private Object payload;
    private String destination_queue;
    private long repeatRate;
    private int repeatCount;

    public Object getPayload() {
      return payload;
    }

    public String getDestinationQueue() {
      return destination_queue;
    }

    public long getRepeatRate() {
      return repeatRate;
    }

    public long getRepeatCount() {
      return repeatCount;
    }

    public void decrementCount() {
      repeatCount--;
    }

    public TQCarrier(Object o, String s) {
      destination_queue = s;
      payload = o;
      repeatRate = 0;
      repeatCount = 1;
    }

    public TQCarrier(Object o, String s, long r) {
      destination_queue = s;
      payload = o;
      repeatRate = r;
      repeatCount = -1;
    }

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
    while( (deQ = m_tq.getAnyLessThan(getCurrentTime()+900)) != null) {
      TQCarrier interim = (TQCarrier) deQ;
      MQFactory.getConcrete(interim.getDestinationQueue()).enqueue(interim.getPayload());
      if(interim.getRepeatRate() != 0) {
        //  If there's a positive repeat count, decrement it once.
        if(interim.getRepeatCount() > 0) {
          interim.decrementCount();
        }
        //  As long as repeat count hasn't reached zero, re-add it.
        if(interim.getRepeatCount() != 0) {
          m_tq.addEvent(getCurrentTime()+interim.getRepeatRate(), interim);
        }
      }
    }

    return false;
  }

  public void add(Object payload, String destination, long when) {
    m_tq.addEvent(when, new TQCarrier(payload, destination));
  }

  public void add(Object payload, String destination, long when, long repeat) {
    m_tq.addEvent(when, new TQCarrier(payload, destination, repeat));
  }

  public void add(Object payload, String destination, long when, long repeat, int howmany) {
    m_tq.addEvent(when, new TQCarrier(payload, destination, repeat, howmany));
  }

  public boolean erase(Object o) {
    List doErase = new ArrayList();
    List current = m_tq.getUnsorted();
    boolean didErase = false;
    for (Iterator it = current.listIterator(); it.hasNext();) {
      TimeQueue.QObject tqo = (TimeQueue.QObject) it.next();
      TQCarrier event = (TQCarrier) tqo.getEvent();
      if(event.getPayload() == o) {
        doErase.add(tqo);
        didErase = true;
      }
    }
    if(didErase) {
      for (Iterator it = doErase.iterator(); it.hasNext();) {
        TimeQueue.QObject delMe = (TimeQueue.QObject) it.next();
        m_tq.erase(delMe);
      }
    }
    return didErase;
  }

  public void dumpQueue() {
    List current = m_tq.getSorted();

    for(ListIterator it=current.listIterator(); it.hasNext();) {
      TimeQueue.QObject step = (TimeQueue.QObject) it.next();
      TQCarrier event = (TQCarrier) step.getEvent();
      System.err.println("Queue: " + event.getDestinationQueue());
      System.err.println("Object: [" + event.getPayload() + "]");
      System.err.println("When: " + new Date(step.getTime()));
      System.err.println();
    }
  }
}
