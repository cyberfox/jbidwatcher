package com.jbidwatcher.auction;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.jbidwatcher.auction.event.EventStatus;
import com.jbidwatcher.util.Constants;
import com.jbidwatcher.util.HashBacked;
import com.jbidwatcher.util.config.JConfig;
import com.jbidwatcher.util.db.ActiveRecord;

import java.lang.ref.Reference;
import java.lang.ref.SoftReference;
import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
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

@Singleton
public class EntryCorral extends EntryCorralTemplate<AuctionEntry> {
  static final String snipeFinder = "(snipe_id IS NOT NULL OR multisnipe_id IS NOT NULL) AND (entries.ended != 1 OR entries.ended IS NULL)";
  private static Date updateSince = new Date();
  private static Date endingSoon = new Date();
  private static Date hourAgo = new Date();
  private static SimpleDateFormat mDateFormat = new SimpleDateFormat(HashBacked.DB_DATE_FORMAT);

  //  private static Table sDB = null;
  public static AuctionEntry findFirstBy(String key, String value) {
    return (AuctionEntry) ActiveRecord.findFirstBy(AuctionEntry.class, key, value);
  }

  @SuppressWarnings({"unchecked"})
  public static List<AuctionEntry> findActive() {
    String notEndedQuery = "SELECT e.* FROM entries e JOIN auctions a ON a.id = e.auction_id WHERE (e.ended != 1 OR e.ended IS NULL) ORDER BY a.ending_at ASC";
    return (List<AuctionEntry>) ActiveRecord.findAllBySQL(AuctionEntry.class, notEndedQuery);
  }

  @SuppressWarnings({"unchecked"})
  public static List<AuctionEntry> findEnded() {
    return (List<AuctionEntry>) ActiveRecord.findAllBy(AuctionEntry.class, "ended", "1");
  }

  @SuppressWarnings({"unchecked"})
  public static List<AuctionEntry> findAllNeedingUpdates(long since) {
    long timeRange = System.currentTimeMillis() - since;
    updateSince.setTime(timeRange);
    return (List<AuctionEntry>) ActiveRecord.findAllByPrepared(AuctionEntry.class,
        "SELECT e.* FROM entries e" +
            "  JOIN auctions a ON a.id = e.auction_id" +
            "  WHERE (e.ended != 1 OR e.ended IS NULL)" +
            "    AND (e.last_updated_at IS NULL OR e.last_updated_at < ?)" +
            "  ORDER BY a.ending_at ASC", mDateFormat.format(updateSince));
  }

  @SuppressWarnings({"unchecked"})
  public static List<AuctionEntry> findEndingNeedingUpdates(long since) {
    long timeRange = System.currentTimeMillis() - since;
    updateSince.setTime(timeRange);

    //  Update more frequently in the last 25 minutes.
    endingSoon.setTime(System.currentTimeMillis() + 25 * Constants.ONE_MINUTE);
    hourAgo.setTime(System.currentTimeMillis() - Constants.ONE_HOUR);

    return (List<AuctionEntry>) ActiveRecord.findAllByPrepared(AuctionEntry.class,
        "SELECT e.* FROM entries e JOIN auctions a ON a.id = e.auction_id" +
            "  WHERE (e.last_updated_at IS NULL OR e.last_updated_at < ?)" +
            "    AND (e.ended != 1 OR e.ended IS NULL)" +
            "    AND a.ending_at < ? AND a.ending_at > ?" +
            "  ORDER BY a.ending_at ASC", mDateFormat.format(updateSince),
        mDateFormat.format(endingSoon), mDateFormat.format(hourAgo));
  }

  @SuppressWarnings({"unchecked"})
  public static List<AuctionEntry> findAll() {
    return (List<AuctionEntry>) ActiveRecord.findAllBySQL(AuctionEntry.class, "SELECT * FROM entries");
  }

  public static AuctionEntry nextSniped() {
    String sql = "SELECT entries.* FROM entries, auctions WHERE " + snipeFinder +
        " AND (entries.auction_id = auctions.id) ORDER BY auctions.ending_at ASC";
    return (AuctionEntry) ActiveRecord.findFirstBySQL(AuctionEntry.class, sql);
  }

  /**
   * Locate an AuctionEntry by first finding an AuctionInfo with the passed
   * in auction identifier, and then looking for an AuctionEntry which
   * refers to that AuctionInfo row.
   *
   * TODO EntryCorral callers? (Probably!)
   *
   * @param identifier - The auction identifier to search for.
   * @return - null indicates that the auction isn't in the database yet,
   * otherwise an AuctionEntry will be loaded and returned.
   */
  public static AuctionEntry findByIdentifier(String identifier) {
    AuctionEntry ae = (AuctionEntry)ActiveRecord.findFirstBy(AuctionEntry.class, "identifier", identifier);
    AuctionInfo ai;

    if(ae != null) {
      ai = AuctionInfo.findByIdOrIdentifier(ae.getAuctionId(), identifier);
      if(ai == null) {
        JConfig.log().logMessage("Error loading auction #" + identifier + ", entry found, auction missing.");
        ae = null;
      }
    }

    if(ae == null) {
      ai = AuctionInfo.findByIdOrIdentifier(null, identifier);

      if(ai != null) {
        ae = findFirstBy("auction_id", ai.getString("id"));
        if (ae != null) ae.setAuctionInfo(ai);
      }
    }

    return ae;
  }

  /**
   * TODO: Clear from the entry corral?
   * @param toDelete List of AuctionEntries to delete from the database.
   *
   * @return true if the entries were all deleted, or the list was empty; false if an error occurred during deletion.
   */
  public static boolean deleteAll(List<AuctionEntry> toDelete) {
    if(toDelete.isEmpty()) return true;

    String entries = ActiveRecord.makeCommaList(toDelete);
    List<Integer> auctions = new ArrayList<Integer>();
    List<AuctionSnipe> snipes = new ArrayList<AuctionSnipe>();

    for(AuctionEntry entry : toDelete) {
      auctions.add(entry.getInteger("auction_id"));
      if(entry.isSniped()) snipes.add(entry.getSnipe());
    }

    boolean success = new EventStatus().deleteAllEntries(entries);
    if(!snipes.isEmpty()) success &= AuctionSnipe.deleteAll(snipes);
    success &= AuctionInfo.deleteAll(auctions);
    success &= EntryTable.getRealDatabase().deleteBy("id IN (" + entries + ")");

    return success;
  }

  public static int countByCategory(Category c) {
    if(c == null) return 0;
    return EntryTable.getRealDatabase().countBySQL("SELECT COUNT(*) FROM entries WHERE category_id=" + c.getId());
  }

  @SuppressWarnings({"unchecked"})
  public static List<AuctionEntry> findManualUpdates() {
    return (List<AuctionEntry>) ActiveRecord.findAllBySQL(AuctionEntry.class, "SELECT e.* FROM entries e JOIN auctions a ON a.id = e.auction_id WHERE e.last_updated_at IS NULL ORDER BY a.ending_at ASC");
  }

  @SuppressWarnings({"unchecked"})
  public static List<AuctionEntry> findRecentlyEnded(int itemCount) {
    return (List<AuctionEntry>) ActiveRecord.findAllBySQL(AuctionEntry.class, "SELECT e.* FROM entries e JOIN auctions a ON a.id = e.auction_id WHERE e.ended = 1 ORDER BY a.ending_at DESC", itemCount);
  }

  @SuppressWarnings({"unchecked"})
  public static List<AuctionEntry> findEndingSoon(int itemCount) {
    return (List<AuctionEntry>) ActiveRecord.findAllBySQL(AuctionEntry.class, "SELECT e.* FROM entries e JOIN auctions a ON a.id = e.auction_id WHERE (e.ended != 1 OR e.ended IS NULL) ORDER BY a.ending_at ASC", itemCount);
  }

  @SuppressWarnings({"unchecked"})
  public static List<AuctionEntry> findBidOrSniped(int itemCount) {
    return (List<AuctionEntry>) ActiveRecord.findAllBySQL(AuctionEntry.class, "SELECT e.* FROM entries e JOIN auctions a ON a.id = e.auction_id WHERE (e.snipe_id IS NOT NULL OR e.multisnipe_id IS NOT NULL OR e.bid_amount IS NOT NULL) ORDER BY a.ending_at ASC", itemCount);
  }

  public static void forceUpdateActive() {
    EntryTable.getRealDatabase().execute("UPDATE entries SET last_updated_at=NULL WHERE ended != 1 OR ended IS NULL");
  }

  public static void trueUpEntries() {
    EntryTable.getRealDatabase().execute("UPDATE entries SET auction_id=(SELECT max(id) FROM auctions WHERE auctions.identifier=entries.identifier)");
    EntryTable.getRealDatabase().execute("DELETE FROM entries e WHERE id != (SELECT max(id) FROM entries e2 WHERE e2.auction_id = e.auction_id)");
  }

  @SuppressWarnings({"unchecked"})
  public static List<AuctionEntry> findAllBy(String column, String value) {
    return (List<AuctionEntry>)ActiveRecord.findAllBy(AuctionEntry.class, column, value);
  }

  public static int count() {
    return ActiveRecord.count(AuctionEntry.class);
  }

  public static int activeCount() {
    return EntryTable.getRealDatabase().countBy("(ended != 1 OR ended IS NULL)");
  }

  public static int completedCount() {
    return EntryTable.getRealDatabase().countBy("ended = 1");
  }

  public static int uniqueCount() {
    return EntryTable.getRealDatabase().countBySQL("SELECT COUNT(DISTINCT(identifier)) FROM entries WHERE identifier IS NOT NULL");
  }

  public static int snipedCount() {
    return EntryTable.getRealDatabase().countBy(snipeFinder);
  }

  @Override
  public AuctionEntry getItem(String param) {
    return findByIdentifier(param);
  }

  @SuppressWarnings({"unchecked"})
  public List<AuctionEntry> findAllSniped() {
    List<AuctionEntry> sniped = (List<AuctionEntry>) ActiveRecord.findAllBySQL(AuctionEntry.class, "SELECT * FROM " + EntryTable.getTableName() + " WHERE (snipe_id IS NOT NULL OR multisnipe_id IS NOT NULL)");

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
    List<? extends Snipeable> entries = findAllBy("multisnipe_id", multisnipeIdentifier);
    List<Snipeable> rval = new ArrayList<Snipeable>(entries.size());
    for (Snipeable entry : entries) {
      Snipeable ae = takeForRead(entry.getIdentifier());
      if (!ae.isComplete()) rval.add(ae);
    }
    return rval;
  }

  @Inject
  private EntryCorral() { super(); }
}
