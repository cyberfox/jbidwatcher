package com.jbidwatcher.util;
/*
 * Copyright (c) 2000-2007, CyberFOX Software, Inc. All Rights Reserved.
 *
 * Developed by mrs (Morgan Schweers)
 */

import javazoom.jlme.util.*;
import com.jbidwatcher.util.queue.MessageQueue;
import com.jbidwatcher.util.queue.MQFactory;
import com.jbidwatcher.util.config.JConfig;

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
    String playme = JConfig.getResource(s).toString();
    ErrorManagement.logMessage("playme = " + playme);
    try {
      Player.playURL(playme);
    } catch(Exception mp3Exception) {
      ErrorManagement.handleException("Failed to play.", mp3Exception);
    }
  }
}
