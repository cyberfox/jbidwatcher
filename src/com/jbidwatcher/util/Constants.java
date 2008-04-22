package com.jbidwatcher.util;
/*
 * Copyright (c) 2000-2007, CyberFOX Software, Inc. All Rights Reserved.
 *
 * Developed by mrs (Morgan Schweers)
 */

import java.text.SimpleDateFormat;
import java.util.Date;

public class Constants {
/** Program identifaction constants, so we change the version and such
 * in just one place.
 */
  public static final String PROGRAM_NAME = "JBidwatcher";
  public static final String PROGRAM_VERS = "2.0beta1";
/** The clock format to use everywhere, when referring to remote times.
 */
  public static final SimpleDateFormat remoteClockFormat = new SimpleDateFormat("dd-MMM-yyyy HH:mm:ss z");
  public static final SimpleDateFormat localClockFormat = new SimpleDateFormat("dd-MMM-yyyy HH:mm:ss z");
  public static final Date FAR_FUTURE = new Date(Long.MAX_VALUE);
  public static final Date LONG_AGO = new Date(Long.MIN_VALUE);

  /** The URL to use when checking for updates.
 */
  public static final String UPDATE_URL = "http://www.jbidwatcher.com/jbidwatcher2.xml";
/** One second in microseconds.
 */
  public static final int ONE_SECOND = 1000;
/** Thirty seconds in microseconds.
 */
  public static final int THIRTY_SECONDS= (30 * ONE_SECOND);
/** One minute in microseconds.
 */
  public static final int ONE_MINUTE = THIRTY_SECONDS * 2;
/** Thirty minutes in microseconds.
 */
  public static final int THIRTY_MINUTES= 30 * ONE_MINUTE;
/** Forty minutes in microseconds.
 */
  public static final int FORTY_MINUTES = 40 * ONE_MINUTE;
/** One hour in microseconds.
 */
  public static final int ONE_HOUR= 60 * ONE_MINUTE;
/** One day in microseconds.
 */
  public static final long ONE_DAY= 24 * ONE_HOUR;
/** What port to listen on, by default.
 */
  public static final int DEFAULT_SERVER_PORT = 9099;
/** String version of what port to listen on by default.
 */
  public static final String DEFAULT_SERVER_PORT_STRING = "9099";
/** What browser to pretend to be, when talking to the auction servers.
 */
  public static final String FAKE_BROWSER = "Mozilla/5.0 (Windows; U; Windows NT 5.1; en-US; rv:1.8.1.6) Gecko/20070725 Firefox/2.0.0.6";
/** Indicates that there is no popup context for this action.  Used to
 * prepend to menu and button actions which do the same as popup
 * actions, but need to operate on selection lists.;
 */
  public static final String NO_CONTEXT = "NC-";
/**
 * The doctypes for the various XML files we save.  This is useful for
 * changing in a single place where the dtd's are loaded from, if
 * necessary.
 */
  public static final String XML_SAVE_DOCTYPE = "<!DOCTYPE auctions SYSTEM \"http://www.jbidwatcher.com/auctions.dtd\">";
  public static final String XML_SEARCHES_DOCTYPE = "<!DOCTYPE auctions SYSTEM \"http://www.jbidwatcher.com/searches.dtd\">";
  public static final int SYNDICATION_ITEM_COUNT = 15;
}
