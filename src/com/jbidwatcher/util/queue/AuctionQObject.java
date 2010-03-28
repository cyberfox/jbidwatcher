package com.jbidwatcher.util.queue;
/*
 * Copyright (c) 2000-2007, CyberFOX Software, Inc. All Rights Reserved.
 *
 * Developed by mrs (Morgan Schweers)
 */

public class AuctionQObject extends QObject {
  private int mCmd;

  public static final int LOAD_URL = 0;
  public static final int LOAD_SEARCH = 1;
  public static final int LOAD_SELLER = 2;
  public static final int LOAD_MYITEMS = 3;
  public static final int MENU_CMD = 4;
  public static final int BID = 5;
  public static final int LOAD_TITLE = 6;
  public static final int LOAD_STRINGS = 7;

  public AuctionQObject() { super(); }

  public AuctionQObject(int cmd, Object data, String label) {
    super(data, label);
    mCmd = cmd;
  }

//  public String toString() {
//    return "AuctionQObject{" +
//            super.toString() +
//            "mCmd=" + mCmd +
//            '}';
//  }

  public void setCommand(int cmd) { mCmd = cmd; }
  public int getCommand() { return mCmd; }

  public String getStringData() { return (String) mData; }
}
