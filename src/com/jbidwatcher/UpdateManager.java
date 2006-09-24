package com.jbidwatcher;

import com.jbidwatcher.config.JConfig;
import com.jbidwatcher.queue.MQFactory;
import com.jbidwatcher.queue.MessageQueue;
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
    //  Ignore the parameter deQ, it's just a 'ping'.
    checkUpdate(false);
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
