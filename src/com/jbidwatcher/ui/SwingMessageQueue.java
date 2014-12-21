package com.jbidwatcher.ui;
/*
 * Copyright (c) 2000-2007, CyberFOX Software, Inc. All Rights Reserved.
 *
 * Developed by mrs (Morgan Schweers)
 */

import com.jbidwatcher.util.queue.MessageQueue;
import com.jbidwatcher.util.config.JConfig;

import javax.swing.SwingUtilities;

public class SwingMessageQueue extends MessageQueue
{
  protected void handleListener() {
    //  Nothing to do here, unless we want to handle postponed messages
    // here too...  We don't appear to have as much of a problem with
    // them here, though.
  }

  public void run() {
    Object data = dequeue();
    if(!_listeners.isEmpty()) {
      try {
        for (Listener l : _listeners) {
          l.messageAction(data);
        }
      } catch(Exception e) {
        JConfig.log().handleException("SMQ Caught exception: " + e, e);
      }
    }
  }

  public boolean enqueue(String obj) {
    if (JConfig.queryConfiguration("debug.queues", "false").equals("true")) {
      JConfig.log().logMessage(obj);
    }
    synchronized(_queue) {
      if (_queue.isEmpty() || _queue.getLast() != obj) {
        _queue.addLast(obj);
        SwingUtilities.invokeLater(this);
        return true;
      }
    }
    return false;
  }
}
