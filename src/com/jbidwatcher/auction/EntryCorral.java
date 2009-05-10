package com.jbidwatcher.auction;

import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.Lock;

/**
 * User: mrs
 * Date: Apr 26, 2009
 * Time: 1:46:02 AM
 *
 * A single clearing house for auction entries, so everything operates on the
 * same underlying objects.  Thread safety is a serious concern.
 */
public class EntryCorral {
  private Map<String, AuctionEntry> mEntryList;
  private final Map<String, Lock> mLockList;
  private static EntryCorral sInstance = null;

  private EntryCorral() {
    mEntryList = new HashMap<String, AuctionEntry>();
    mLockList = new HashMap<String, Lock>();
  }

  public static EntryCorral getInstance() {
    if(sInstance == null) sInstance = new EntryCorral();
    return sInstance;
  }

  public AuctionEntry takeForWrite(String identifier) {
    AuctionEntry result = mEntryList.get(identifier);
    if(result == null) {
      result = AuctionEntry.findByIdentifier(identifier);
    }
    if(result != null) {
      synchronized (mLockList) {
        Lock l = mLockList.get(identifier);
        if (l == null) {
          l = new ReentrantLock(true);
          mLockList.put(identifier, l);
        }
        l.lock();
      }
    }
    return result;
  }

  public void release(String identifier) {
    Lock l = mLockList.get(identifier);
    if(l != null) l.unlock();
  }

  public AuctionEntry takeForRead(String identifier) {
    AuctionEntry result = mEntryList.get(identifier);
    if (result == null) {
      result = AuctionEntry.findByIdentifier(identifier);
    }
    return result;
  }

  public AuctionEntry put(AuctionEntry ae) {
    AuctionEntry result = chooseLatest(ae);
    mEntryList.put(ae.getIdentifier(), result);

    return result;
  }

  public List<AuctionEntry> findAllSniped() {
    List<AuctionEntry> sniped = AuctionEntry.findAllSniped();
    if(sniped != null) {
      List<AuctionEntry> results = new ArrayList<AuctionEntry>();
      for (AuctionEntry ae : sniped) {
        results.add(chooseLatest(ae));
      }
      return results;
    }
    return null;
  }

  private AuctionEntry chooseLatest(AuctionEntry ae) {
    AuctionEntry chosen;
    AuctionEntry existing = mEntryList.get(ae.getIdentifier());
    if(existing == null ||
        (ae.getDate("updated_at") != null && existing.getDate("updated_at") == null) ||
        (ae.getDate("updated_at") != null && existing.getDate("updated_at") != null &&
            ae.getDate("updated_at").after(existing.getDate("updated_at")))) {
      mEntryList.put(ae.getIdentifier(), ae);
      chosen = ae;
    } else {
      chosen = existing;
    }
    return chosen;
  }

  public AuctionEntry erase(String identifier) {
    AuctionEntry rval = mEntryList.remove(identifier);
    Lock l = mLockList.remove(identifier);
    if(l != null) l.unlock();
    return rval;
  }
}
