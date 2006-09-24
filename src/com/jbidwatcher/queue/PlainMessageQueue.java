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

import com.jbidwatcher.util.ErrorManagement;

import java.util.ArrayList;
import java.util.List;
import java.util.Iterator;

/** @noinspection ThisEscapedInObjectConstruction,CallToThreadStartDuringObjectConstruction*/
public final class PlainMessageQueue extends MessageQueue {
  List _postpone = new ArrayList();
  Thread _myself;

  protected void handleListener() {
    if(_postpone != null) {
      for (Iterator it = _postpone.iterator(); it.hasNext();) {
        Object data = it.next();
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
        boolean empty = false;
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
