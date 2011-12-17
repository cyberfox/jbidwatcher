package com.jbidwatcher.util.queue;
/*
 * Copyright (c) 2000-2007, CyberFOX Software, Inc. All Rights Reserved.
 *
 * Developed by mrs (Morgan Schweers)
 */

import java.util.*;
import java.beans.*;
import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;
import com.jbidwatcher.util.Currency;

public abstract class MessageQueue implements Runnable {
  protected final LinkedList<Object> _queue = new LinkedList<Object>();
  protected List<MessageQueue.Listener> _listeners = new ArrayList<Listener>(1);
  protected abstract void handleListener();

  public interface Listener {
    void messageAction(Object deQ);
  }

  /**
   * registerListener treats the message queue as if it is a 1-entry list,
   * replacing all existing listeners (expected to be one or zero) with the
   * newly passed in listener.
   *
   * @param ml - The new listener to be notified of events on this queue.
   *
   * @return The old listener that was being notified of events on this queue.
   */
  public Listener registerListener(MessageQueue.Listener ml) {
    Listener old = _listeners.isEmpty() ? null : _listeners.get(0);
    _listeners.clear();
    _listeners.add(ml);
    handleListener();
    return old;
  }

  //  TODO -- Should end up only allowing String and XMLEncode'able objects (which get converted to a String?).
  public abstract boolean enqueue(String objToEnqueue);

  //  Maybe XML queues are different?  MQFactory.getXMLQueue()?
  public void enqueueBean(QObject xe) {
    // Create output stream.
    ByteArrayOutputStream fos = new ByteArrayOutputStream();
    XMLEncoder xe2 = new XMLEncoder(fos);
    xe2.setPersistenceDelegate(Currency.class, Currency.getDelegate());
    xe2.writeObject(xe);
    xe2.close();
    enqueue(fos.toString());
  }

  private Object convertBean(String obj) {
    ByteArrayInputStream fis = new ByteArrayInputStream(obj.getBytes());
    XMLDecoder xd = new XMLDecoder(fis);
    Object rval = xd.readObject();
    xd.close();
    return rval;
  }

  public Object dequeue() {
    Object out;
    synchronized(_queue) {
      out = _queue.removeFirst();
    }
    if(out instanceof String) {
      if( ((String)out).contains("java.beans.XMLDecoder") ) {
        out = convertBean((String)out);
      }
    }
    return out;
  }

  public void clear() {
    synchronized(_queue) {
      _queue.clear();
    }
  }

  public void removeListener(Listener listener) {
    _listeners.remove(listener);
  }

  public void addListener(Listener listener) {
    _listeners.add(listener);
  }
}
