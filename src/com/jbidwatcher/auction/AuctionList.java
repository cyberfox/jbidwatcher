package com.jbidwatcher.auction;

import com.jbidwatcher.util.Comparison;

import java.util.List;
import java.util.ArrayList;

/**
 * Created by IntelliJ IDEA.
* User: Morgan
* Date: Apr 6, 2008
* Time: 3:32:10 PM
* To change this template use File | Settings | File Templates.
*/
public class AuctionList {
  private List<AuctionEntry> mList = new ArrayList<AuctionEntry>();

  public int size() { return mList.size(); }
  public AuctionEntry get(int i) { return mList.get(i); }
  public AuctionEntry remove(int i) { return mList.remove(i); }
  public boolean add(AuctionEntry e) { return mList.add(e); }

  public AuctionEntry find(Comparison c) {
    for (AuctionEntry entry : mList) {
      if (c.match(entry)) return entry;
    }

    return null;
  }

  public List<AuctionEntry> getList() {
    return mList;
  }
}
