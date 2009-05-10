package com.jbidwatcher.util.queue;

import com.jbidwatcher.util.db.DBTimeQueue;

import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: Morgan
 * Date: Apr 4, 2009
 * Time: 10:22:41 PM
 *
 *
 */
public class DBTimeQueueManager extends TimeQueueManager {
  @Override
  public boolean check() {
    List<DBTimeQueue> events = DBTimeQueue.allBefore(getCurrentTime() + 900);
    for(DBTimeQueue event : events) {
      event.delete();
      MQFactory.getConcrete(event.getDestination()).enqueue(event.getPayload()); // NONSTRING Queue Object ?
      if(event.getInterval() != 0) {
        int count = event.getRepeatCount();
        if(count > 0) count--;
        if(count != 0) {
          DBTimeQueue.addEvent(event.getDestination(), event.getPayload(),
              getCurrentTime() + event.getInterval(), event.getInterval(),
              count);
        }
      }
    }
    return false;
  }

  public DBTimeQueueManager() { }

  @Override
  public void add(Object payload, Object destination, long when) {
    DBTimeQueue.addEvent(destination.toString(), payload.toString(), when, 0, 1);
  }

  @Override
  public void add(Object payload, Object destination, long when, long repeat) {
    DBTimeQueue.addEvent(destination.toString(), payload.toString(), when, (int)repeat, -1);
  }

  @Override
  public void add(Object payload, String destination, long when, long repeat, int howmany) {
    DBTimeQueue.addEvent(destination, payload.toString(), when, (int)repeat, howmany);
  }

  @Override
  public boolean erase(Object destination, Object payload) {
    return DBTimeQueue.erase(destination, payload);
  }

  @Override
  public void dumpQueue() {
    List<DBTimeQueue> wholeQueue = DBTimeQueue.getSorted();
    for(DBTimeQueue event : wholeQueue) {
      System.err.println(event.toString());
    }
  }
}
