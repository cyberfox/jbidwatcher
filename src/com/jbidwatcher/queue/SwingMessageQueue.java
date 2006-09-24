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

import javax.swing.SwingUtilities;

public class SwingMessageQueue extends MessageQueue {
  public SwingMessageQueue() {
    super();
  }

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
