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

  public static MessageQueue getConcrete(Object whatConcrete) {
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
