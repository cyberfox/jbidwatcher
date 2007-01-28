package com.jbidwatcher.queue;
/*
 * Copyright (c) 2000-2007, CyberFOX Software, Inc. All Rights Reserved.
 *
 * Developed by mrs (Morgan Schweers)
 */

import java.util.*;

public abstract class MessageQueue implements Runnable {
  protected final LinkedList<Object> _queue = new LinkedList<Object>();
  protected MessageQueue.Listener _listener = null;
  protected abstract void handleListener();

  public interface Listener {
    void messageAction(Object deQ);
  }

  public void registerListener(MessageQueue.Listener ml) {
    _listener = ml;
    handleListener();
  }

  public abstract void enqueue(Object objToEnqueue);

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
    return out;
  }

  public void clear() {
    synchronized(_queue) {
      _queue.clear();
    }
  }
}
