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

  public int size() { synchronized(mIdentifierList) { return mIdentifierList.size(); } }
  public AuctionEntry get(int i) {
    synchronized (mIdentifierList) {
      String identifier = mIdentifierList.get(i);
      return EntryCorral.getInstance().takeForRead(identifier);
    }
  }
  public void remove(int i) {
    synchronized (mIdentifierList) {
      String identifier = mIdentifierList.get(i);
      EntryCorral.getInstance().takeForRead(identifier);
      mIdentifierList.remove(i);
      mIdentifierSet.remove(identifier);
    }
  }

  public void add(AuctionEntry ae) {
    if(ae.getIdentifier() == null || ae.getIdentifier().length() == 0 || mIdentifierSet.contains(ae.getIdentifier())) {
      return;
    }
    synchronized (mIdentifierList) {
      EntryCorral.getInstance().put(ae);
      mIdentifierList.add(ae.getIdentifier());
      mIdentifierSet.add(ae.getIdentifier());
    }
  }

  public AuctionEntry find(Comparison c) {
    synchronized (mIdentifierList) {
      for (String identifier : mIdentifierList) {
        AuctionEntry result = EntryCorral.getInstance().takeForRead(identifier);
        if (c.match(result)) return result;
      }
    }
    return null;
  }

  public void each(Task task) {
    synchronized(mIdentifierList) {
      for (String identifier : mIdentifierList) {
        AuctionEntry result = (AuctionEntry) EntryCorral.getInstance().takeForWrite(identifier);
        try { task.execute(result); } finally { EntryCorral.getInstance().release(identifier); }
      }
    }
  }
}
