package com.jbidwatcher.auction;

import java.lang.ref.Reference;
import java.lang.ref.SoftReference;
import java.lang.ref.WeakReference;
import java.util.*;
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
  private Map<String, Reference<AuctionEntry>> mEntryList;
  private final Map<String, Lock> mLockList;
  private static EntryCorral sInstance = null;

  private EntryCorral() {
    mEntryList = new HashMap<String, Reference<AuctionEntry>>();
    mLockList = new HashMap<String, Lock>();
  }

  public static EntryCorral getInstance() {
    if(sInstance == null) sInstance = new EntryCorral();
    return sInstance;
  }

  private AuctionEntry get(String identifier) {
    Reference<AuctionEntry> r = mEntryList.get(identifier);
    if(r != null) return r.get();
    return null;
  }

  public AuctionEntry takeForWrite(String identifier) {
    AuctionEntry result = get(identifier);
    if(result == null) {
      result = AuctionEntry.findByIdentifier(identifier);
    }
    if(result != null) {
      Lock l = mLockList.get(identifier);
      if (l == null) {
        l = new ReentrantLock(true);
        synchronized (mLockList) {
          mLockList.put(identifier, l);
        }
      }
      l.lock();
    }
    return result;
  }

  public void release(String identifier) {
    Lock l = mLockList.get(identifier);
    if(l != null) l.unlock();
  }

  public AuctionEntry takeForRead(String identifier) {
    AuctionEntry result = get(identifier);
    if (result == null) {
      result = AuctionEntry.findByIdentifier(identifier);
      if(result != null) mEntryList.put(identifier, new WeakReference<AuctionEntry>(result));
    }
    return result;
  }

  public AuctionEntry put(AuctionEntry ae) {
    AuctionEntry result = chooseLatest(ae);
    mEntryList.put(ae.getIdentifier(), new SoftReference<AuctionEntry>(result));

    return result;
  }

  public AuctionEntry putWeakly(AuctionEntry ae) {
    return chooseLatest(ae);
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
    AuctionEntry existing = get(ae.getIdentifier());
    final Date inputDate = ae.getDate("updated_at");
    final Date existingDate = (existing == null ? null : existing.getDate("updated_at"));
    if(existing == null ||
        (inputDate != null && existingDate == null) ||
        (inputDate != null && inputDate.after(existingDate))) {
      if(mEntryList.get(ae.getIdentifier()) instanceof SoftReference) {
        mEntryList.put(ae.getIdentifier(), new SoftReference<AuctionEntry>(ae));
      } else {
        mEntryList.put(ae.getIdentifier(), new WeakReference<AuctionEntry>(ae));
      }
      chosen = ae;
    } else {
      chosen = existing;
    }
    return chosen;
  }

  public AuctionEntry erase(String identifier) {
    synchronized(mLockList) {
      Lock l = mLockList.remove(identifier);
      Reference<AuctionEntry> rval = mEntryList.remove(identifier);
      if (l != null) l.unlock();
      if (rval == null) {
        return null;
      }
      return rval.get();
    }
  }

  public void clear() {
    synchronized(mLockList) {
      for (String s : mLockList.keySet()) {
        erase(s);
      }

      mEntryList.clear();
    }
  }
}
