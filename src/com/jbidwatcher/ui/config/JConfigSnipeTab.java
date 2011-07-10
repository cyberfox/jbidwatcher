package com.jbidwatcher.ui.config;
/*
 * Copyright (c) 2000-2007, CyberFOX Software, Inc. All Rights Reserved.
 *
 * Developed by mrs (Morgan Schweers)
 */

import com.jbidwatcher.ui.util.JPasteListener;
import com.jbidwatcher.util.config.JConfig;

import java.awt.*;
import javax.swing.*;

public class JConfigSnipeTab extends JConfigTab {
  private JCheckBox autoSubtractShippingBox;
  private JTextField snipeTime;

  public String getTabName() { return "Sniping"; }
  public void cancel() { }

  public void apply() {
    long newSnipeAt = Long.parseLong(snipeTime.getText()) * 1000;

    //  Set the default snipe for everything to be whatever was typed in.
    JConfig.setConfiguration("snipemilliseconds", Long.toString(newSnipeAt));
    JConfig.setConfiguration("snipe.subtract_shipping", autoSubtractShippingBox.isSelected() ? "true" : "false");

  }

  public void updateValues() {
    String snipeAtCfg = JConfig.queryConfiguration("snipemilliseconds", "30000");
    String autoSubtractShipping = JConfig.queryConfiguration("snipe.subtract_shipping", "false");
    long snipeAt = Long.parseLong(snipeAtCfg);
    snipeTime.setText(Long.toString(snipeAt / 1000));
    autoSubtractShippingBox.setSelected(autoSubtractShipping.equals("true"));
  }

  private JPanel buildSnipeSettings() {
    JPanel tp = new JPanel();
    JLabel jl = new JLabel("How close to snipe (in seconds):");

    tp.setBorder(BorderFactory.createTitledBorder("Snipe Timing"));
    tp.setLayout(new BorderLayout());

    snipeTime = new JTextField();
    snipeTime.addMouseListener(JPasteListener.getInstance());
    snipeTime.setToolTipText("Number of seconds prior to auction end to fire a snipe.");

    snipeTime.setEditable(true);
    snipeTime.getAccessibleContext().setAccessibleName("Default number of seconds prior to auction end to fire a snipe.");
    tp.add(jl, BorderLayout.NORTH);
    tp.add(snipeTime, BorderLayout.SOUTH);

    return(tp);
  }

  private JPanel buildExtraSettings() {
    JPanel tp = new JPanel();
    tp.setBorder(BorderFactory.createTitledBorder("Snipe Settings"));
    tp.setLayout(new BorderLayout());

    autoSubtractShippingBox = new JCheckBox("Subtract shipping/insurance from bid amounts by default");
    autoSubtractShippingBox.setToolTipText("Determines the default behaviour of deducting shipping/insurance from bid amounts. This behaviour can be overridden on a per-bid basis.");

    tp.add(autoSubtractShippingBox);
    return tp;
  }

  public JConfigSnipeTab() {
    super();
    this.setLayout(new BorderLayout());
    JPanel jp = new JPanel();
    jp.setLayout(new BorderLayout());
    jp.add(buildSnipeSettings(), BorderLayout.NORTH);
    jp.add(buildExtraSettings(), BorderLayout.SOUTH);
    this.add(panelPack(jp), BorderLayout.NORTH);
    updateValues();
  }
}
