package com.jbidwatcher.util;
/*
 * Copyright (c) 2000-2007, CyberFOX Software, Inc. All Rights Reserved.
 *
 * Developed by mrs (Morgan Schweers)
 */

import javazoom.jlme.util.*;
import com.jbidwatcher.queue.MessageQueue;
import com.jbidwatcher.queue.MQFactory;

public class AudioPlayer implements MessageQueue.Listener {
  public AudioPlayer() {
    MQFactory.getConcrete("sfx").registerListener(this);
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
