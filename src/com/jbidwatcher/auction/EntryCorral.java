package com.jbidwatcher.auction;

import com.jbidwatcher.util.db.ActiveRecord;

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
abstract class EntryCorralTemplate<T extends ActiveRecord> {
  private Map<String, Reference<T>> mEntryList;
  private final Map<String, Lock> mLockList;

  protected EntryCorralTemplate() {
    mEntryList = new HashMap<String, Reference<T>>();
    mLockList = new HashMap<String, Lock>();
  }

  private T get(String identifier) {
    Reference<T> r = mEntryList.get(identifier);
    if(r != null) return r.get();
    return null;
  }

  abstract public T getItem(String param);

  public ActiveRecord takeForWrite(String identifier) {
    T result = get(identifier);
    if(result == null) {
      result = getItem(identifier);
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

  public T takeForRead(String identifier) {
    T result = get(identifier);
    if (result == null) {
      result = getItem(identifier);
      if(result != null) mEntryList.put(identifier, new WeakReference<T>(result));
    }
    return result;
  }

  public T put(T ae) {
    T result = chooseLatest(ae, ae.getUnique());
    mEntryList.put(ae.getUnique(), new SoftReference<T>(result));

    return result;
  }

  public T putWeakly(T ae) {
    return chooseLatest(ae, ae.getUnique());
  }

  protected T chooseLatest(T ae, String identifier) {
    T chosen;
    T existing = get(identifier);
    final Date inputDate = ae.getDate("updated_at");
    final Date existingDate = (existing == null ? null : existing.getDate("updated_at"));
    if(existing == null ||
        (inputDate != null && existingDate == null) ||
        (inputDate != null && inputDate.after(existingDate))) {
      if(mEntryList.get(identifier) instanceof SoftReference) {
        mEntryList.put(identifier, new SoftReference<T>(ae));
      } else {
        mEntryList.put(identifier, new WeakReference<T>(ae));
      }
      chosen = ae;
    } else {
      chosen = existing;
    }
    return chosen;
  }

  public T erase(String identifier) {
    synchronized(mLockList) {
      Lock l = mLockList.remove(identifier);
      Reference<T> rval = mEntryList.remove(identifier);
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

public class EntryCorral extends EntryCorralTemplate<AuctionEntry> {
  @Override
  public AuctionEntry getItem(String param) {
    return AuctionEntry.findByIdentifier(param);
  }

  public List<AuctionEntry> findAllSniped() {
    List<AuctionEntry> sniped = AuctionEntry.findAllSniped();
    if (sniped != null) {
      List<AuctionEntry> results = new ArrayList<AuctionEntry>();
      for (AuctionEntry ae : sniped) {
        results.add(chooseLatest(ae, ae.getIdentifier()));
      }
      return results;
    }
    return null;
  }

  public List<Snipeable> getMultisnipedByGroup(String multisnipeIdentifier) {
    List<? extends Snipeable> entries = AuctionEntry.findAllBy("multisnipe_id", multisnipeIdentifier);
    List<Snipeable> rval = new ArrayList<Snipeable>(entries.size());
    for (Snipeable entry : entries) {
      Snipeable ae = takeForRead(entry.getIdentifier());
      if (!ae.isComplete()) rval.add(ae);
    }
    return rval;
  }

  //  Singleton stuff
  private static EntryCorral sInstance = null;
  private EntryCorral() { super(); }
  public static EntryCorral getInstance() {
    if (sInstance == null) sInstance = new EntryCorral();
    return sInstance;
  }
}