package com.jbidwatcher.util.db;

import java.util.List;
import java.util.Date;

/**
 * Created by IntelliJ IDEA.
 * User: Morgan
 * Date: Apr 4, 2009
 * Time: 10:40:23 PM
 *
 *
 */
@SuppressWarnings({"unchecked"})
public class DBTimeQueue extends ActiveRecord {
  private void setRepeatCount(int howMany) { setInteger("repeat_count", howMany); }
  public int getRepeatCount() { return getInteger("repeat_count"); }
  private void setInterval(int repeat) { setInteger("repeat_interval", repeat); }
  public Integer getInterval() { return getInteger("repeat_interval"); }
  private void setFireAt(long when) { setDate("fire_at", new Date(when)); }
  private void setPayload(String payload) { setString("payload", payload); }
  public String getPayload() { return getString("payload"); }
  private void setDestination(String destination) { setString("destination", destination); }
  public String getDestination() { return getString("destination"); }

  public String toString() {
    StringBuffer sb = new StringBuffer("{ ");
    sb.append("destination => \"").append(getDestination());
    sb.append("\", ");
    sb.append("payload => \"").append(getPayload());
    sb.append("\", ");
    sb.append("fire_at => ").append(getDate("fire_at"));
    if (getInterval() != null && getInterval() != 0) {
      sb.append("\", ");
      sb.append("interval => ").append(getInterval());
      sb.append("\", ");
      sb.append("repeat_count => ").append(getRepeatCount());
    }
    sb.append(" }");
    return sb.toString();
  }

  public static DBTimeQueue addEvent(String destination, String payload, long when, int repeat, int howmany) {
    DBTimeQueue rval = new DBTimeQueue();
    rval.setDestination(destination);
    rval.setPayload(payload);
    rval.setFireAt(when);
    rval.setInterval(repeat);
    rval.setRepeatCount(howmany);
    rval.saveDB();

    return rval;
  }

  /*-*********************-*/
  /* Database access stuff */
  /*-*********************-*/
  private static Table sDB = null;
  protected static String getTableName() { return "time_queue"; }

  protected Table getDatabase() {
    if (sDB == null) sDB = openDB(getTableName());
    return sDB;
  }

  public static List<DBTimeQueue> getSorted() {
    return (List<DBTimeQueue>) findAllBy(DBTimeQueue.class, null, null, "fire_at");
  }

  public static List<DBTimeQueue> getUnsorted() {
    return (List<DBTimeQueue>) findAllBy(DBTimeQueue.class, null, null);
  }

  public static boolean erase(Object destination, Object payload) {
    String[] keys = { "destination", "payload" };
    String[] values = { destination.toString(), payload.toString() };
    List<DBTimeQueue> targets = (List<DBTimeQueue>)findAllMulti(DBTimeQueue.class, keys, values, null);
    boolean allDeleted = true;
    for(DBTimeQueue target : targets) {
      allDeleted = target.delete() && allDeleted;
    }
    return allDeleted;
  }

  public static List<DBTimeQueue> allBefore(long fire_at) {
    return (List<DBTimeQueue>) findAllComparator(DBTimeQueue.class, "fire_at", "<=", formatDate(new Date(fire_at)), "fire_at ASC");
  }
}
