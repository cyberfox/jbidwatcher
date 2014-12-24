package com.jbidwatcher.auction.event;
/*
 * Copyright (c) 2000-2007, CyberFOX Software, Inc. All Rights Reserved.
 *
 * Developed by mrs (Morgan Schweers)
 */

import com.jbidwatcher.util.config.JConfig;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/*!@class EventLogger
 *
 * @brief Encapsulates a super-simple event log for display purposes.
 *
 * Each EventLogger object has a 'title' and an 'id' which will be
 * shown once for all the events.
 *
 * Arbitrary number of events can be added, and when they are, it
 * time-stamps when they happened.  It keeps track of the 'last
 * message', and the number of times it happened, only tracking the
 * first time it happened.
 *
 * It also allows for saving and loading to/from the DB, so any
 * given object can have its own event log as well.
 *
 * The naming isn't quite right, it should probably be 'addEvent' and
 * 'getEvents'.
 *
 */
public class EventLogger {
  /** Records all status messages that are added.
   */
  private String mIdentifier =null;
  private String mTitle =null;
  private Integer mEntryId = null;
  private List<EventStatus> mAllEvents;
  private final EventStatus mNullEvent = new EventStatus("Nothing has happened.", new Date());

  public EventLogger(String identifier, Integer entryId, String title) {
    mIdentifier = identifier;
    mEntryId = entryId;
    mTitle = title;
    mNullEvent.setEntryId(mEntryId);
    mNullEvent.setAuctionIdentifier(mIdentifier);
    mNullEvent.setTitle(mTitle);

    mAllEvents = EventStatus.findAllByEntry(mEntryId, mIdentifier);
    if(mAllEvents == null) mAllEvents = new ArrayList<EventStatus>();
  }

  /** Store the status for the most recent event to occur, and format it with the date
   * in front, the item identifier, the title, and the status all in the log line.  This
   * eventually needs to log to a file, or something similar.
   * 
   * @param inStatus A piece of text describing this, the most recent event 
   *     occuring to this auction entry.
   */
  public void setLastStatus(String inStatus) {
    if(inStatus != null) {
      EventStatus lastStatus;
      if (mAllEvents.isEmpty()) {
        lastStatus = mNullEvent;
      } else {
        lastStatus = mAllEvents.get(mAllEvents.size()-1);
      }
      if(inStatus.equals(lastStatus.getMessage())) {
        lastStatus.setRepeatCount(lastStatus.getRepeatCount() + 1);
        lastStatus.saveDB();
      } else {
        EventStatus whatHappened = new EventStatus(inStatus, new Date(), mEntryId, mIdentifier, mTitle);
        whatHappened.saveDB();

        mAllEvents.add(whatHappened);
        JConfig.log().logMessage(whatHappened.toString());
      }
    }
  }

  public int getStatusCount() { return mAllEvents.size(); }

  /** What is the most recent thing that happened to this particular auction?
   *
   * @return A string, formatted, that details the most recent event in plain words.
   */
  public String getLastStatus() {
    if(mAllEvents.isEmpty()) {
      return(mNullEvent.toString());
    } else {
      return mAllEvents.get(mAllEvents.size()-1).toString();
    }
  }

  /** What is the most recent thing that happened to this particular auction?
   *
   * @return A string, formatted, that details the most recent event in plain words.
   */
  public String getAllStatuses() {
    if(mAllEvents.isEmpty()) {
        return(mNullEvent.toBulkString() + "<br>");
    } else {
      StringBuilder sb = new StringBuilder();

      for(EventStatus lastStatus : mAllEvents) {
        sb.append(lastStatus.toBulkString());
        sb.append("<br>");
      }
      return(sb.toString());
    }
  }

  public void save() {
    for(EventStatus step : mAllEvents) {
      step.saveDB();
    }
  }
}
