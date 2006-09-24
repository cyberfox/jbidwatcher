package com.jbidwatcher.util;
/*
 * Copyright (c) 2000-2006 CyberFOX Software, Inc. All Rights Reserved.
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

import javazoom.jlme.util.*;
import com.jbidwatcher.queue.MessageQueue;
import com.jbidwatcher.queue.MQFactory;

public class AudioPlayer implements MessageQueue.Listener {
  public AudioPlayer() {
    MQFactory.getConcrete("audio").registerListener(this);
  }

  public void messageAction(Object deQ) {
    String s=(String)deQ;
    String playme = AudioPlayer.class.getResource(s).toString();
    ErrorManagement.logMessage("playme = " + playme);
    try {
      Player.playURL(playme);
    } catch(Exception mp3Exception) {
      ErrorManagement.handleException("Failed to play.", mp3Exception);
    }
  }
}
