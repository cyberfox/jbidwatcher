package com.jbidwatcher.util;
/*
 * Copyright (c) 2000-2007, CyberFOX Software, Inc. All Rights Reserved.
 *
 * Developed by mrs (Morgan Schweers)
 */

import com.jbidwatcher.util.xml.XMLElement;
import com.jbidwatcher.util.xml.XMLParseException;
import com.jbidwatcher.util.xml.XMLSerialize;

import java.util.*;

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
 * It also allows for saving and loading to/from XML, so that any
 * given object can have its own event log as well.
 *
 * The naming isn't quite right, it should probably be 'addEvent' and
 * 'getEvents'.
 *
 */
public class EventLogger implements XMLSerialize {
  /** Records all status messages that are added.
   * 
   */
  private String mId =null;
  private String mTitle =null;
  private List<EventStatus> mAllEvents;
  private final EventStatus mNullEvent = new EventStatus("Nothing has happened.", new Date());

  /**
   * @brief Load events from XML.
   *
   * BUGBUG -- Fix this to work with XMLSerializeSimple if possible.  -- mrs: 23-February-2003 21:00
   * 
   * @param curElement - The current element to load the events from.
   */
  public void fromXML(XMLElement curElement) {
    Iterator<XMLElement> logStep = curElement.getChildren();
    EventStatus newEvent;

    while(logStep.hasNext()) {
      XMLElement curEntry = logStep.next();

      if(curEntry.getTagName().equals("entry")) {
        long msgtime = System.currentTimeMillis();
        String msg = "Nothing has happened.";
        int curCount = Integer.parseInt(curEntry.getProperty("COUNT"));

        Iterator<XMLElement> entryStep = curEntry.getChildren();
        while(entryStep.hasNext()) {
          XMLElement entryField = entryStep.next();
          if(entryField.getTagName().equals("message")) msg = entryField.getContents();
          if(entryField.getTagName().equals("date")) msgtime = Long.parseLong(entryField.getContents());
        } 

        newEvent = new EventStatus(msg, new Date(msgtime), mId, mTitle);
        newEvent.setRepeatCount(curCount);
        newEvent.saveDB();

        mAllEvents.add(newEvent);
      } else {
        throw new XMLParseException(curEntry.getTagName(), "Expected 'entry' tag!");
      }
    }
  }

  public XMLElement toXML() {
    XMLElement xmlLog;

    if(mAllEvents.size() == 0) return null;

    xmlLog = new XMLElement("log");

    for (EventStatus curEvent : mAllEvents) {
      XMLElement xmlResult;
      XMLElement xmsg, xdate;

      xmlResult = new XMLElement("entry");
      xmlResult.setProperty("count", Integer.toString(curEvent.getRepeatCount()));

      xmsg = new XMLElement("message");
      xmsg.setContents(curEvent.getMessage());
      xmlResult.addChild(xmsg);

      xdate = new XMLElement("date");
      xdate.setContents(Long.toString(curEvent.getLoggedAt().getTime()));
      xmlResult.addChild(xdate);

      xmlLog.addChild(xmlResult);
    }
    return(xmlLog);
  }

  public EventLogger(String identifier, String title) {
    mId = identifier;
    mTitle = title;
    mNullEvent.setEntryId(mId);
    mNullEvent.setTitle(mTitle);

    mAllEvents = EventStatus.findAllByEntry(mId);
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
    EventStatus whatHappened;
    EventStatus lastStatus;

    if(inStatus != null) {
      if(mAllEvents.size() > 0) {
        lastStatus = mAllEvents.get(mAllEvents.size()-1);
      } else {
        lastStatus = mNullEvent;
      }
      if(inStatus.equals(lastStatus.getMessage())) {
        lastStatus.setRepeatCount(lastStatus.getRepeatCount() + 1);
      } else {
        whatHappened = new EventStatus(inStatus, new Date(), mId, mTitle);

        mAllEvents.add(whatHappened);
        ErrorManagement.logMessage(whatHappened.toString());
      }
    }
  }

  public String getLastStatus() { return getLastStatus(false); }
  public int getStatusCount() { return mAllEvents.size(); }

  /** What is the most recent thing that happened to this particular auction?
   *
   * @param bulk - Whether to return them as a bulk set of entries, or not.
   * 
   * @return A string, formatted, that details the most recent event in plain words.
   */
  public String getLastStatus(boolean bulk) {
    if(mAllEvents.size() == 0) {
      if(bulk) {
        return(mNullEvent.toBulkString() + "<br>");
      } else {
        return(mNullEvent.toString() + "<br>");
      }
    } else {
      StringBuffer sb = new StringBuffer();

      for(EventStatus lastStatus : mAllEvents) {
        if(bulk) {
          sb.append(lastStatus.toBulkString());
        } else {
          sb.append(lastStatus.toString());
        }
        sb.append("<br>");
      }
      return(sb.toString());
    }
  }
}
