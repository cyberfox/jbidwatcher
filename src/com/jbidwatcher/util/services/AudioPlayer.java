package com.jbidwatcher.util.services;
/*
 * Copyright (c) 2000-2007, CyberFOX Software, Inc. All Rights Reserved.
 *
 * Developed by mrs (Morgan Schweers)
 */

import javazoom.jl.player.advanced.*;

import com.jbidwatcher.util.Constants;
import com.jbidwatcher.util.queue.SuperQueue;
import com.jbidwatcher.util.queue.MessageQueue;
import com.jbidwatcher.util.queue.MQFactory;
import com.jbidwatcher.util.config.JConfig;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

public class AudioPlayer implements MessageQueue.Listener {
  private final Map<Integer, AdvancedPlayer> lastPlayer;
  private Integer track;
  private static int MAX_AUDIO_CLIP = 10;

  private AudioPlayer() {
    track = 0;
    lastPlayer = new HashMap<Integer, AdvancedPlayer>();
  }

  public void messageAction(Object deQ) {
    final String s=(String)deQ;

    if(JConfig.queryConfiguration("sound.max_clip") != null) {
      MAX_AUDIO_CLIP = Integer.parseInt(JConfig.queryConfiguration("sound.max_clip"));
    }

    // Format is 'STOP #' where '#' is the track number to stop.
    if (s.startsWith("STOP")) {
      String stopTrackStr = s.substring(5);
      Integer stopTrack = Integer.parseInt(stopTrackStr);
      synchronized (lastPlayer) {
        if(lastPlayer.get(stopTrack) != null) {
          lastPlayer.get(stopTrack).stop();
        }
      }
      return;
    }

    track++;

    SuperQueue sq = SuperQueue.getInstance();
    long now = System.currentTimeMillis();
    sq.preQueue("STOP " + track, "sfx", now + (Constants.ONE_SECOND * MAX_AUDIO_CLIP));

    final Integer current_track = track;
    //  Play the stream we've chosen.
    new Thread(new Runnable() {
      public void run() {
        InputStream fin;
        //  Try to figure out the stream to play.
        try {
          URL playme = JConfig.getResource(s);
          fin = playme.openStream();
        } catch (Exception badResource) {
          fin = JConfig.bestSource(JConfig.class.getClassLoader(), s);
        }

        try {
          BufferedInputStream bin = new BufferedInputStream(fin);

          final AdvancedPlayer p = new AdvancedPlayer(bin);
          lastPlayer.put(current_track, p);

          //  Mark it so we won't call stop if the playback already completed.
          p.setPlayBackListener(new PlaybackListener() {
            @Override
            public void playbackStarted(PlaybackEvent playbackEvent) { }

            @Override
            public void playbackFinished(PlaybackEvent playbackEvent) {
              synchronized (lastPlayer) {
                lastPlayer.remove(current_track);
              }
            }
          });

          //  This may block until it's done.
          p.play();
        } catch (Exception mp3Exception) {
          JConfig.log().handleException("Failed to play.", mp3Exception);
          synchronized (lastPlayer) {
            lastPlayer.remove(current_track);
          }
        }
      }
    }).start();
  }

  public static void start() {
    MQFactory.getConcrete("sfx").registerListener(new AudioPlayer());
  }
}
