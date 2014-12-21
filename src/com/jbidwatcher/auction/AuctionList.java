package com.jbidwatcher.auction;

import com.jbidwatcher.util.Comparison;
import com.jbidwatcher.util.Task;

import java.util.*;

/**
 * Created by IntelliJ IDEA.
* User: Morgan
* Date: Apr 6, 2008
* Time: 3:32:10 PM
* To change this template use File | Settings | File Templates.
*/
public class AuctionList {
  private final List<String> mIdentifierList = Collections.synchronizedList(new ArrayList<String>());
  private final Set<String> mIdentifierSet = Collections.synchronizedSet(new HashSet<String>());
  private final EntryCorral entryCorral;

  public AuctionList(EntryCorral corral) {
    entryCorral = corral;
  }

  public int size() { synchronized(mIdentifierList) { return mIdentifierList.size(); } }
  public AuctionEntry get(int i) {
    synchronized (mIdentifierList) {
      String identifier = mIdentifierList.get(i);
      return entryCorral.takeForRead(identifier);
    }
  }
  public void remove(int i) {
    synchronized (mIdentifierList) {
      String identifier = mIdentifierList.get(i);
      entryCorral.takeForRead(identifier);
      mIdentifierList.remove(i);
      mIdentifierSet.remove(identifier);
    }
  }

  public void add(AuctionEntry ae) {
    if(ae.getIdentifier() == null || ae.getIdentifier().length() == 0 || mIdentifierSet.contains(ae.getIdentifier())) {
      return;
    }
    synchronized (mIdentifierList) {
      entryCorral.put(ae);
      mIdentifierList.add(ae.getIdentifier());
      mIdentifierSet.add(ae.getIdentifier());
    }
  }

  public AuctionEntry find(Comparison c) {
    synchronized (mIdentifierList) {
      for (String identifier : mIdentifierList) {
        AuctionEntry result = entryCorral.takeForRead(identifier);
        if (c.match(result)) return result;
      }
    }
    return null;
  }

  public void each(Task task) {
    synchronized(mIdentifierList) {
      for (String identifier : mIdentifierList) {
        AuctionEntry result = (AuctionEntry) entryCorral.takeForWrite(identifier);
        try { task.execute(result); } finally { entryCorral.release(identifier); }
      }
    }
  }
}
