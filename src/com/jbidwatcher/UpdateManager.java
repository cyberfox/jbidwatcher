package com.jbidwatcher;

/*
 * Copyright (c) 2000-2007, CyberFOX Software, Inc. All Rights Reserved.
 *
 * Developed by mrs (Morgan Schweers)
 */

import com.jbidwatcher.util.config.JConfig;
import com.jbidwatcher.util.queue.MQFactory;
import com.jbidwatcher.util.queue.MessageQueue;
import com.jbidwatcher.util.queue.TimerHandler;
import com.jbidwatcher.util.Constants;

public class UpdateManager implements TimerHandler.WakeupProcess, MessageQueue.Listener {
  private static UpdateManager _instance=null;
  private static UpdaterEntry _ue = null;

  private UpdateManager() {
    //  Nothing to do here?
  }

  public static UpdateManager getInstance() {
    if(_instance == null) {
      _instance = new UpdateManager();
      MQFactory.getConcrete("update").registerListener(_instance);
    }

    return _instance;
  }

  public void messageAction(Object deQ) {
    boolean interactive = false;
    if(deQ.equals("INTERACTIVE")) {
      interactive = true;
    }
    checkUpdate(interactive);
  }

  public UpdaterEntry getUpdateInfo() { return _ue; }

  public boolean check() {
    if(JConfig.queryConfiguration("updates.enabled", "true").equals("true")) {
      checkUpdate(false);
    }

    return true;
  }

  public static void checkUpdate(boolean interactive) {
    MQFactory.getConcrete("Swing").enqueue("Checking for a newer version.");
    UpdaterEntry ue = new UpdaterEntry(Constants.PROGRAM_NAME, Constants.UPDATE_URL);
    if(!ue.isValid()) {
      MQFactory.getConcrete("Swing").enqueue("NOTIFY Failure checking for update.");
      if(interactive) MQFactory.getConcrete("Swing").enqueue("BAD_NEWVERSION");
      return;
    }
    String lastKnownVersion = JConfig.queryConfiguration("updates.last_version", null);

    if(lastKnownVersion == null) lastKnownVersion = Constants.PROGRAM_VERS;
    if(!lastKnownVersion.equals(ue.getVersion())) {
      JConfig.setConfiguration("updates.last_version", ue.getVersion());
      if(!Constants.PROGRAM_VERS.equals(ue.getVersion())) {
        _ue = ue;
        MQFactory.getConcrete("Swing").enqueue("NEWVERSION");
        MQFactory.getConcrete("Swing").enqueue("Found a new version available!");
        return;
      }
    }
    if(interactive) MQFactory.getConcrete("Swing").enqueue("NO_NEWVERSION");
    MQFactory.getConcrete("Swing").enqueue("No new versions on the server.");

    //  Only apply configuration updates if they are for THIS version.
    if(Constants.PROGRAM_VERS.equals(ue.getVersion())) {
      if(ue.hasConfigurationUpdates()) {
        if(JConfig.queryConfiguration("updates.allowConfig", "true").equals("true")) {
          ue.applyConfigurationUpdates();
        }
      }
    }
  }
}
