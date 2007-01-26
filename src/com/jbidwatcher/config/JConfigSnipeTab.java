package com.jbidwatcher.config;
/*
 * Copyright (c) 2000-2007, CyberFOX Software, Inc. All Rights Reserved.
 *
 * Developed by mrs (Morgan Schweers)
 */

import com.jbidwatcher.auction.AuctionEntry;
import com.jbidwatcher.ui.JPasteListener;

import java.awt.*;
import javax.swing.*;

public class JConfigSnipeTab extends JConfigTab {
  JTextField snipeTime;

  public String getTabName() { return "Sniping"; }
  public void cancel() { }

  public boolean apply() {
    //  Set the default snipe for everything to be whatever was typed in.
    AuctionEntry.setDefaultSnipeTime(Long.parseLong(snipeTime.getText()) * 1000);

    return true;
  }

  public void updateValues() {
    snipeTime.setText(Long.toString(AuctionEntry.getDefaultSnipeTime() / 1000));
  }

  private JPanel buildSnipeSettings() {
    JPanel tp = new JPanel();
	JLabel jl = new JLabel("How close to snipe (in seconds):");

    tp.setBorder(BorderFactory.createTitledBorder("Snipe Timing"));
    tp.setLayout(new BorderLayout());

    snipeTime = new JTextField();
    snipeTime.addMouseListener(JPasteListener.getInstance());
	snipeTime.setToolTipText("Number of seconds prior to auction end to fire a snipe.");

    updateValues();

    snipeTime.setEditable(true);
    snipeTime.getAccessibleContext().setAccessibleName("Default number of seconds prior to auction end to fire a snipe.");
	tp.add(jl, BorderLayout.NORTH);
    tp.add(snipeTime, BorderLayout.SOUTH); 

    return(tp);
  }

  public JConfigSnipeTab() {
    super();
    this.setLayout(new BorderLayout());
    this.add(panelPack(buildSnipeSettings()), BorderLayout.NORTH);
  }
}
