package com.jbidwatcher.util.db;

import com.jbidwatcher.util.db.ActiveRecord;
import com.jbidwatcher.util.queue.MQFactory;
import com.jbidwatcher.util.queue.MessageQueue;

import java.util.HashSet;
import java.util.Set;
import java.util.Collections;

/**
 * Handle save and flush behavior for the database.  Queue DB saves up, and
 * flush them every few seconds.
 *
 * User: Morgan
 * Date: Nov 24, 2007
 * Time: 2:17:57 PM
 */
public class DBManager {
  private static DBManager sInstance;
  private final Set<ActiveRecord> mStores;

  private class DBSaveManager implements MessageQueue.Listener {
    public void messageAction(Object deQ) {
      mStores.add((com.jbidwatcher.util.db.ActiveRecord)deQ);
    }
  }

  private class DBFlushManager implements MessageQueue.Listener {
    public void messageAction(Object deQ) {
      synchronized(mStores) {
        for(ActiveRecord entry : mStores) {
          entry.saveDB();
        }
        mStores.clear();
      }
    }
  }

  public static DBManager getInstance() {
    if(sInstance == null) {
      sInstance = new DBManager();
    }
    return sInstance;
  }

  private DBManager() {
    mStores = Collections.synchronizedSet(new HashSet<com.jbidwatcher.util.db.ActiveRecord>());

    DBSaveManager saveManager = new DBSaveManager();
    MQFactory.getConcrete("dbsave").registerListener(saveManager);

    DBFlushManager flushManager = new DBFlushManager();
    MQFactory.getConcrete("dbflush").registerListener(flushManager);
  }
}
