package com.jbidwatcher.auction;

import com.jbidwatcher.util.Comparison;
import com.jbidwatcher.util.Task;

import java.util.List;
import java.util.Collections;
import java.util.ArrayList;

/**
 * Created by IntelliJ IDEA.
* User: Morgan
* Date: Apr 6, 2008
* Time: 3:32:10 PM
* To change this template use File | Settings | File Templates.
*/
public class AuctionList {
  private final List<String> mList = Collections.synchronizedList(new ArrayList<String>());
  private final EntryCorral entryCorral;

  public AuctionList(EntryCorral corral) {
    entryCorral = corral;
  }

  public int size() { synchronized(mList) { return mList.size(); } }
  public AuctionEntry get(int i) {
    synchronized (mList) {
      String identifier = mList.get(i);
      return entryCorral.takeForRead(identifier);
    }
  }
  public void remove(int i) {
    synchronized (mList) {
      String identifier = mList.get(i);
      entryCorral.takeForRead(identifier);
      mList.remove(i);
    }
  }

  public void add(AuctionEntry ae) {
    synchronized (mList) {
      entryCorral.put(ae);
      mList.add(ae.getIdentifier());
    }
  }

  public AuctionEntry find(Comparison c) {
    synchronized (mList) {
      for (String identifier : mList) {
        AuctionEntry result = entryCorral.takeForRead(identifier);
        if (c.match(result)) return result;
      }
    }
    return null;
  }

  public void each(Task task) {
    synchronized(mList) {
      for (String identifier : mList) {
        AuctionEntry result = (AuctionEntry) entryCorral.takeForWrite(identifier);
        try { task.execute(result); } finally { entryCorral.release(identifier); }
      }
    }
  }
}
