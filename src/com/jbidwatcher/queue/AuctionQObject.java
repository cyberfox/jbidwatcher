package com.jbidwatcher.queue;
/*
 * Copyright (c) 2000-2007, CyberFOX Software, Inc. All Rights Reserved.
 *
 * Developed by mrs (Morgan Schweers)
 */

public class AuctionQObject extends QObject {
  int m_cmd;

  public static final int LOAD_URL = 0;
  public static final int LOAD_SEARCH = 1;
  public static final int LOAD_SELLER = 2;
  public static final int LOAD_MYITEMS = 3;
  public static final int MENU_CMD = 4;
  public static final int BID = 5;
  public static final int LOAD_TITLE = 6;
  public static final int LOAD_STRINGS = 7;
  public static final int SET_SNIPE = 8;
  public static final int SNIPE = 9;
  public static final int CANCEL_SNIPE = 10;

  public AuctionQObject(int cmd, Object data, String label) {
    super(data, label);
    m_cmd = cmd;
  }

  public String toString() {
    return "AuctionQObject{" +
            super.toString() +
            "m_cmd=" + m_cmd +
            '}';
  }

  public int getCommand() { return m_cmd; }
  public String getStringData() { return (String)m_data; }
}
