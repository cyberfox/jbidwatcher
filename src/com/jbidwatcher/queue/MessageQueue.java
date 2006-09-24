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

import java.util.*;

public abstract class MessageQueue implements Runnable {
  protected final LinkedList _queue = new LinkedList();
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
