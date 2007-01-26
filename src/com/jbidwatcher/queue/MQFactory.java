package com.jbidwatcher.queue;
/*
 * Copyright (c) 2000-2007, CyberFOX Software, Inc. All Rights Reserved.
 *
 * Developed by mrs (Morgan Schweers)
 */

import java.util.*;

/** MQFactory is a factory class, returning MessageQueue objects from a pool.
 *  The object returned is based on the string passed in to the getConcrete()
 *  method.
 */
public class MQFactory {
  private static MQFactory _instance = null;
  private static Map<String, MessageQueue> MQs;

  private MQFactory() {
    MQs = new TreeMap<String, MessageQueue>();
  }

  public static void addQueue(String queueName, MessageQueue whatQueue) {
    if(_instance == null) {
      _instance = new MQFactory();
    }

    MQs.put(queueName, whatQueue);
  }

  public static MessageQueue getConcrete(String whatConcrete) {
    MessageQueue foundMQ;

    if(_instance == null) {
      _instance = new MQFactory();
    }

    foundMQ = MQs.get(whatConcrete);

    if(foundMQ == null) {
      foundMQ = new PlainMessageQueue(whatConcrete);
      MQs.put(whatConcrete, foundMQ);
    }

    return foundMQ;
  }
}
