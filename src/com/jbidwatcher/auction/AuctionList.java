package com.jbidwatcher.auction;

import com.jbidwatcher.util.Comparison;
import com.jbidwatcher.util.SortedList;

import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.util.TreeSet;

/**
 * Created by IntelliJ IDEA.
* User: Morgan
* Date: Apr 6, 2008
* Time: 3:32:10 PM
* To change this template use File | Settings | File Templates.
*/
public class AuctionList {
  private final List<AuctionEntry> mList = Collections.synchronizedList(new SortedList<AuctionEntry>());

  public int size() { synchronized(mList) { return mList.size(); } }
  public AuctionEntry get(int i) { synchronized (mList) { return mList.get(i); } }
  public AuctionEntry remove(int i) { synchronized (mList) { return mList.remove(i); } }
  public boolean add(AuctionEntry e) { synchronized (mList) { return mList.add(e); } }

  public AuctionEntry find(Comparison c) {
    synchronized (mList) {
      for (AuctionEntry entry : mList) {
        if (c.match(entry)) return entry;
      }
    }
    return null;
  }

  public List<AuctionEntry> getList() {
    return mList;
  }
}
