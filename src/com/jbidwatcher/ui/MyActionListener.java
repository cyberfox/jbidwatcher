package com.jbidwatcher.ui;
/*
 * Copyright (c) 2000-2007, CyberFOX Software, Inc. All Rights Reserved.
 *
 * Developed by mrs (Morgan Schweers)
 */

import com.jbidwatcher.auction.AuctionEntry;

import java.util.ArrayList;
import javax.swing.JFrame;
import java.awt.event.ActionListener;

public abstract class MyActionListener implements ActionListener {
  protected ArrayList<AuctionEntry> mEntries;
  protected JFrame m_within;
  public void setEntries(ArrayList<AuctionEntry> allEntries) { mEntries = allEntries; }
  public void setFrame(JFrame jf) { m_within = jf; }
}
