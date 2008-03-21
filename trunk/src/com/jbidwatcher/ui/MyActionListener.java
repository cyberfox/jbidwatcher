package com.jbidwatcher.ui;
/*
 * Copyright (c) 2000-2007, CyberFOX Software, Inc. All Rights Reserved.
 *
 * Developed by mrs (Morgan Schweers)
 */

import com.jbidwatcher.auction.AuctionEntry;

import java.util.Vector;
import javax.swing.JFrame;
import java.awt.event.ActionListener;

public abstract class MyActionListener implements ActionListener {
  protected Vector<AuctionEntry> m_entries;
  protected JFrame m_within;
  public void setEntries(Vector<AuctionEntry> allEntries) { m_entries = allEntries; }
  public void setFrame(JFrame jf) { m_within = jf; }
}
