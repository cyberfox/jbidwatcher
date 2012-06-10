package com.jbidwatcher.util.queue;
/*
 * Copyright (c) 2000-2007, CyberFOX Software, Inc. All Rights Reserved.
 *
 * Developed by mrs (Morgan Schweers)
 */

import java.util.*;

/** MQFactory is a factory class, returning MessageQueue objects from a pool.
 *  The object returned is based on the object passed in to the getConcrete()
 *  method.  This will usually be a string, but there are times when you want
 *  to pass a more fundamental object.
 */
@SuppressWarnings({"UtilityClass"})
public class MQFactory {
  private static Map<Object, MessageQueue> MQs = null;

  private MQFactory() { }

  public static void addQueue(String queueName, MessageQueue whatQueue) {
    if(MQs == null) {
      MQs = new HashMap<Object, MessageQueue>();
    }

    MQs.put(queueName, whatQueue);
  }

  /**
   * Find or create a message queue with a given name.  The first time this is called, it will create a plain message queue with
   * this name, and return it.  Subsequent calls will return the same message queue.
   *
   * @param whatConcrete The name of the queue; this also becomes its thread name.
   *
   * @return A queue addressable by the provided name.
   */
  public static MessageQueue getConcrete(String whatConcrete) {
    if(MQs == null) {
      MQs = new HashMap<Object, MessageQueue>();
    }

    MessageQueue foundMQ = MQs.get(whatConcrete);

    if(foundMQ == null) {
      foundMQ = new PlainMessageQueue(whatConcrete);
      MQs.put(whatConcrete, foundMQ);
    }

    return foundMQ;
  }
}
