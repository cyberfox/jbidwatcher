package com.jbidwatcher.util.queue;
/*
 * Copyright (c) 2000-2007, CyberFOX Software, Inc. All Rights Reserved.
 *
 * Developed by mrs (Morgan Schweers)
 */

import com.jbidwatcher.util.config.ErrorManagement;

import java.util.ArrayList;
import java.util.List;

/** @noinspection ThisEscapedInObjectConstruction,CallToThreadStartDuringObjectConstruction*/
public final class PlainMessageQueue extends MessageQueue {
  List<Object> _postpone = new ArrayList<Object>();
  Thread _myself;

  protected void handleListener() {
    if(_postpone != null) {
      for (Object data : _postpone) {
        enqueue(data);
      }
      _postpone.clear();
      _postpone = null;
    }
  }

  public PlainMessageQueue(String qName) {
    _myself = new Thread(this);
    //  Go ahead and die if all other threads are closed!
    _myself.setDaemon(true);
    _myself.setName("MQ_" + qName);
    _myself.start();
  }

  /** @noinspection StringContatenationInLoop*/
  public void run() {
    //noinspection InfiniteLoopStatement
    while(true) {
      Object data = null;
      try {
        synchronized(_queue) {
          if(_queue.isEmpty()) _queue.wait();
          data = dequeueObject();
        }
      } catch(InterruptedException ignore) {
        //  Ignore the interrupted exception, it just wakes us up.
      }

      if(data != null) {
        boolean empty;
        do {
          if (_listener != null) {
            try {
              _listener.messageAction(data);
            } catch (Exception e) {
              ErrorManagement.handleException("PMQ Caught exception: " + e, e);
              clear();
            }
          } else {
            ErrorManagement.logDebug(_myself.getName() + ": Postponing Message: " + data);
            if(_postpone != null) _postpone.add(data);
          }
          synchronized (_queue) {
            empty = _queue.isEmpty();
            if(!empty) data = dequeueObject();
          }
        } while (!empty);
      }
    }
  }

  public void enqueue(Object objToEnqueue) {
    synchronized(_queue) {
      //  We really do want to make sure the exact same object isn't enqueued multiple times.
      //noinspection ObjectEquality
      if(_queue.isEmpty() || _queue.getLast() != objToEnqueue) {
        _queue.addLast(objToEnqueue);
        _queue.notifyAll();
      }
    }
  }
}
