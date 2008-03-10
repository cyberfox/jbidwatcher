package com.jbidwatcher.ui.config;
/*
 * Copyright (c) 2000-2007, CyberFOX Software, Inc. All Rights Reserved.
 *
 * Developed by mrs (Morgan Schweers)
 */

import com.jbidwatcher.auction.AuctionEntry;
import com.jbidwatcher.ui.util.JPasteListener;
import com.jbidwatcher.util.config.JConfig;

import java.awt.*;
import javax.swing.*;

public class JConfigSnipeTab extends JConfigTab {
  JTextField snipeTime;

  public String getTabName() { return "Sniping"; }
  public void cancel() { }

  public boolean apply() {
    long newSnipeAt = Long.parseLong(snipeTime.getText()) * 1000;

    //  Set the default snipe for everything to be whatever was typed in.
    JConfig.setConfiguration("snipemilliseconds", Long.toString(newSnipeAt));

    return true;
  }

  public void updateValues() {
    String snipeAtCfg = JConfig.queryConfiguration("snipemilliseconds", "30000");
    long snipeAt = Long.parseLong(snipeAtCfg);
    snipeTime.setText(Long.toString(snipeAt / 1000));
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
