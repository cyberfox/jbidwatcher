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

public abstract class MessageQueue implements Runnable {
  protected final LinkedList<Object> _queue = new LinkedList<Object>();
  protected MessageQueue.Listener _listener = null;
  protected abstract void handleListener();

  public interface Listener {
    void messageAction(Object deQ);
  }

  public Listener registerListener(MessageQueue.Listener ml) {
    Listener old = _listener;
    _listener = ml;
    handleListener();
    return old;
  }

  //  TODO -- Should end up only allowing String and XMLEncode'able objects (which get converted to a String?).
  public abstract void enqueue(String objToEnqueue);

  //  Maybe XML queues are different?  MQFactory.getXMLQueue()?
  public void enqueueBean(QObject xe) {
    // Create output stream.
    ByteArrayOutputStream fos = new ByteArrayOutputStream();
    XMLEncoder xe2 = new XMLEncoder(fos);
    xe2.writeObject(xe);
    xe2.close();
    enqueue(fos.toString());
  }

  public Object dequeueBean() {
    String obj = dequeue();
    return convertBean(obj);
  }

  private Object convertBean(String obj) {
    ByteArrayInputStream fis = new ByteArrayInputStream(obj.getBytes());
    XMLDecoder xd = new XMLDecoder(fis);
    Object rval = xd.readObject();
    xd.close();
    return rval;
  }

  public String dequeue() {
    String outStr;
    synchronized(_queue) {
      outStr = (String) _queue.removeFirst();
    }
    return outStr;
  }

  public Object dequeueObject() {
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

  public void deRegisterListener(Listener listener) {
    if(_listener == listener) _listener = null;
  }
}
