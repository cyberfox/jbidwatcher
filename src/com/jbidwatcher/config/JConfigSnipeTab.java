package com.jbidwatcher.config;
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
