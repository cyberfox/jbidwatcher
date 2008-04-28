package com.jbidwatcher.auction.event;

import com.jbidwatcher.util.db.ActiveRecord;
import com.jbidwatcher.util.db.Table;

import java.util.Date;
import java.util.List;
import java.util.ArrayList;

/**
 * Created by IntelliJ IDEA.
* User: Morgan
* Date: Jan 28, 2008
* Time: 10:07:20 AM
* To change this template use File | Settings | File Templates.
*/

/*!@class EventLogger
 *
 * @brief A single 'event'.
 *
 * This contains a single event, a string saying 'what' happened, a
 * date for when it happened first, and a count of times that it has
 * happened.
 *
 */
public class EventStatus extends ActiveRecord {
  //  For ActiveRecord construction.
  public EventStatus() { }

  public EventStatus(String what, Date when) {
    setMessage(what);
    setLoggedAt(when);
    setRepeatCount(1);
  }

  public EventStatus(String what, Date when, String id, String title) {
    setMessage(what);
    setLoggedAt(when);
    setRepeatCount(1);
    setEntryId(id);
    setTitle(title);
  }

  public String toBulkString() {
    String count = "";
    if(getRepeatCount() > 1) count = " (" + getRepeatCount() + ")";
    String outStatus = getLoggedAt() + ": " + getMessage() + count;

    return(outStatus);
  }

  public String toString() {
    String count = "";
    if (getRepeatCount() > 1) count = " (" + getRepeatCount() + ")";
    String outStatus = getLoggedAt() + ": " + getEntryId() + " (" + getTitle() + ") - " + getMessage() + count;

    return(outStatus);
  }

  /*************************/
  /* Database access stuff */
  /**
   * *********************
   */
  private static Table sDB = null;
  protected static String getTableName() { return "events"; }
  protected Table getDatabase() {
    if (sDB == null) {
      sDB = openDB(getTableName());
    }
    return sDB;
  }

  public static EventStatus findFirstBy(String key, String value) {
    return (EventStatus) ActiveRecord.findFirstBy(EventStatus.class, key, value);
  }

  public static EventStatus find(Integer id) {
    return (EventStatus) ActiveRecord.findFirstBy(EventStatus.class, "id", Integer.toString(id));
  }

  public static List<EventStatus> findAllByEntry(String entry_id) {
    List<ActiveRecord> records = ActiveRecord.findAllBy(EventStatus.class, "entry_id", entry_id, "logged_at ASC");

    if(records != null) {
      List<EventStatus> results = new ArrayList<EventStatus>(records.size());
      for(ActiveRecord record : records) {
        results.add((EventStatus)record);
      }

      return results;
    }
    return null;
  }

  public String getMessage() { return getString("message"); }
  public Date getLoggedAt() { return getDate("logged_at"); }
  public int getRepeatCount() { return getInteger("repeat_count"); }
  public String getTitle() { return getString("title"); }
  public String getEntryId() { return getString("entry_id"); }

  public void setMessage(String message) { setString("message", message); }
  public void setLoggedAt(Date loggedAt) { setDate("logged_at", loggedAt); }
  public void setRepeatCount(int repeatCount) { setInteger("repeat_count", repeatCount); }
  public void setTitle(String title) { setString("title", title); }
  public void setEntryId(String entryId) { setString("entry_id", entryId); }
}
