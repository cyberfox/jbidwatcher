package com.jbidwatcher.queue;
/*
 * Copyright (c) 2000-2005 CyberFOX Software, Inc. All Rights Reserved.
 *
 * This library is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Library General Public License as published
 * by the Free Software Foundation; either version 2 of the License, or (at
 * your option) any later version.
 *
 * This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Library General Public License for more
 * details.
 *
 * You should have received a copy of the GNU Library General Public License
 * along with this library; if not, write to the
 *  Free Software Foundation, Inc.
 *  59 Temple Place
 *  Suite 330
 *  Boston, MA 02111-1307
 *  USA
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
