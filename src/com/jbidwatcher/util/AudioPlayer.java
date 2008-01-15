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
  private static AudioPlayer sAP = null;

  public static AudioPlayer getInstance() {
    if(sAP == null) sAP = new AudioPlayer();

    return sAP;
  }

  private AudioPlayer() {
    MQFactory.getConcrete("sfx").registerListener(this);
  }

  public void messageAction(Object deQ) {
    String s=(String)deQ;
    String playme = AudioPlayer.class.getClassLoader().getResource(s).toString();
    ErrorManagement.logMessage("playme = " + playme);
    try {
      Player.playURL(playme);
    } catch(Exception mp3Exception) {
      ErrorManagement.handleException("Failed to play.", mp3Exception);
    }
  }
}
