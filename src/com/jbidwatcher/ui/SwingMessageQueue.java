package com.jbidwatcher.ui;
/*
 * Copyright (c) 2000-2007, CyberFOX Software, Inc. All Rights Reserved.
 *
 * Developed by mrs (Morgan Schweers)
 */

import com.jbidwatcher.util.ErrorManagement;
import com.jbidwatcher.util.queue.MessageQueue;

import javax.swing.SwingUtilities;

public class SwingMessageQueue extends MessageQueue
{
  protected void handleListener() {
    //  Nothing to do here, unless we want to handle postponed messages
    // here too...  We don't appear to have as much of a problem with
    // them here, though.
  }

  public void run() {
    Object data = dequeueObject();
    if(_listener != null) {
      try {
        _listener.messageAction(data);
      } catch(Exception e) {
        ErrorManagement.handleException("SMQ Caught exception: " + e, e);
      }
    }
  }

  public void enqueue(Object obj) {
    synchronized(_queue) {
      if (_queue.isEmpty() || _queue.getLast() != obj) {
        _queue.addLast(obj);
        SwingUtilities.invokeLater(this);
      }
    }
  }
}
