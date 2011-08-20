package com.jbidwatcher.util;

import java.util.Date;
import java.util.TimeZone;

/**
 * Created by IntelliJ IDEA.
 * User: Morgan
 * Date: Feb 25, 2007
 * Time: 4:30:02 PM
 *
 * Combines a Date with a TimeZone for better understanding of the time display.
 */
public class ZoneDate {
  private TimeZone mZone;
  private Date mDate;

  public ZoneDate(TimeZone zone, Date date) {
    mZone = zone;
    mDate = date;
  }

  public TimeZone getZone() { return mZone; }
  public void setZone(TimeZone zone) { mZone = zone; }

  public Date getDate() { return mDate; }
  public void setDate(Date date) { mDate = date; }

  public boolean isNull() { return mDate == null && mZone == null; }
}
