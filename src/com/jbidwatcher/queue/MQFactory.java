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

/** MQFactory is a factory class, returning MessageQueue objects from a pool.
 *  The object returned is based on the string passed in to the getConcrete()
 *  method.
 */
public class MQFactory {
  private static MQFactory _instance = null;
  private static Map MQs;

  private MQFactory() {
    MQs = new TreeMap();
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

    foundMQ = (MessageQueue) MQs.get(whatConcrete);

    if(foundMQ == null) {
      foundMQ = new PlainMessageQueue(whatConcrete);
      MQs.put(whatConcrete, foundMQ);
    }

    return foundMQ;
  }
}
