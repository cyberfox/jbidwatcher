package com.jbidwatcher.util;

import com.jbidwatcher.auction.ActiveRecord;
import com.jbidwatcher.util.db.AuctionDB;

import java.util.Date;

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
class EventStatus extends ActiveRecord {
  public String mMessage;
  public Date mLoggedAt;
  public int mRepeatCount;
  private String mId;
  private String mTitle;

  public void setId(String id) {
    mId = id;
  }

  public void setTitle(String title) {
    mTitle = title;
  }

  public EventStatus(String what, Date when) {
    mMessage = what;
    mLoggedAt = when;
    mRepeatCount = 1;
  }

  public EventStatus(String what, Date when, String id, String title) {
    mMessage = what;
    mLoggedAt = when;
    mRepeatCount = 1;
    mId = id;
    mTitle = title;
  }

  public String toBulkString() {
    String outStatus;
    String count = "";
    if(mRepeatCount > 1) count = " (" + mRepeatCount + ")";
    outStatus = mLoggedAt + ": " + mMessage + count;

    return(outStatus);
  }

  public String toString() {
    String outStatus;
    String count = "";
    if (mRepeatCount > 1) count = " (" + mRepeatCount + ")";
    outStatus = mLoggedAt + ": " + mId + " (" + mTitle + ") - " + mMessage + count;

    return(outStatus);
  }

  /*************************/
  /* Database access stuff */
  /**
   * *********************
   */

  private static AuctionDB sDB = null;

  protected static String getTableName() { return "events"; }

  protected AuctionDB getDatabase() {
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
}
