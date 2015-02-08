package com.jbidwatcher.ui.config;
/*
 * Copyright (c) 2000-2007, CyberFOX Software, Inc. All Rights Reserved.
 *
 * Developed by mrs (Morgan Schweers)
 */

import com.jbidwatcher.ui.util.JBEditorPane;
import com.jbidwatcher.ui.util.JPasteListener;
import com.jbidwatcher.ui.util.OptionUI;
import com.jbidwatcher.util.Constants;
import com.jbidwatcher.util.config.JConfig;

import java.awt.*;
import javax.swing.*;

public class JConfigSnipeTab extends JConfigTab {
  private JComboBox gixenMode;
  private JCheckBox autoSubtractShippingBox;
  private JTextField snipeTime;
  private JBEditorPane gixenDisabledWarning;

  private enum GixenModes {
    NO("Not at all"),
    ONLY("Exclusively"),
    ADDITIONAL("In addition to JBidwatcher");

    private final String plainText;

    GixenModes(String subtitle) {
      this.plainText = subtitle;
    }

    public final String toString() { return plainText; }
  }

  public String getTabName() { return "Sniping"; }
  public void cancel() { }

  public void apply() {
    long newSnipeAt = Long.parseLong(snipeTime.getText()) * 1000;

    //  Set the default snipe for everything to be whatever was typed in.
    JConfig.setConfiguration("snipemilliseconds", Long.toString(newSnipeAt));
    JConfig.setConfiguration("snipe.subtract_shipping", autoSubtractShippingBox.isSelected() ? "true" : "false");
    JConfig.setConfiguration("snipe.gixen", ((Enum) gixenMode.getSelectedItem()).name());
  }

  public void updateValues() {
    String snipeAtCfg = JConfig.queryConfiguration("snipemilliseconds", "30000");
    String autoSubtractShipping = JConfig.queryConfiguration("snipe.subtract_shipping", "false");
    long snipeAt = Long.parseLong(snipeAtCfg);
    snipeTime.setText(Long.toString(snipeAt / 1000));
    autoSubtractShippingBox.setSelected(autoSubtractShipping.equals("true"));
    gixenMode.setSelectedItem(GixenModes.valueOf(JConfig.queryConfiguration("snipe.gixen", "NO")));
  }

  private JPanel buildSnipeSettings(JPasteListener pasteListener) {
    JPanel tp = new JPanel();
    JLabel jl = new JLabel("How close to snipe (in seconds):");

    tp.setBorder(BorderFactory.createTitledBorder("Snipe Timing"));
    tp.setLayout(new BorderLayout());

    snipeTime = new JTextField();
    snipeTime.addMouseListener(pasteListener);
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
    tp.setLayout(new BoxLayout(tp, BoxLayout.Y_AXIS));

    autoSubtractShippingBox = new JCheckBox("Subtract shipping/insurance from bid amounts by default");
    autoSubtractShippingBox.setToolTipText("Determines the default behaviour of deducting shipping/insurance from bid amounts. This behaviour can be overridden on a per-bid basis.");

    Box shippingBox = Box.createHorizontalBox();
    shippingBox.add(autoSubtractShippingBox);
    shippingBox.add(Box.createHorizontalGlue());
    tp.add(shippingBox);

    return tp;
  }

  private JPanel buildGixenSettings() {
    JPanel tp = new JPanel();
    tp.setBorder(BorderFactory.createTitledBorder("Gixen Settings (optional)"));
    tp.setLayout(new BoxLayout(tp, BoxLayout.Y_AXIS));

    Box label = Box.createHorizontalBox();
    label.add(new JLabel("Submit snipes to the Gixen web service"));
    label.add(Box.createHorizontalGlue());
    tp.add(label);

    gixenMode = new JComboBox(GixenModes.values());

    Box gixenBox = Box.createHorizontalBox();
    gixenBox.add(gixenMode);
    gixenBox.add(Box.createHorizontalGlue());
    tp.add(gixenBox);

    return tp;
  }

  private static final String GIXEN_DISABLED = "Gixen support is disabled until an eBay username and password are set.";
  private final String DISABLED_HTML = "<html><body><div style=\"font-size: 0.96em;\"><i>" + GIXEN_DISABLED + "</div></body></html>";

  private void checkGixenEligibility() {
    String user = JConfig.queryConfiguration(Constants.EBAY_SERVER_NAME + ".user");
    if(user == null || user.equals("default")) {
      gixenMode.disable();
      gixenDisabledWarning.setText(DISABLED_HTML);
      gixenDisabledWarning.setVisible(true);
    } else {
      gixenMode.enable();
      gixenDisabledWarning.setText("");
      gixenDisabledWarning.setVisible(false);
    }
    this.repaint();
  }

  public JConfigSnipeTab(JPasteListener pasteListener) {
    super();

    this.setLayout(new BorderLayout());
    JPanel jp = new JPanel();
    jp.setLayout(new BoxLayout(jp, BoxLayout.Y_AXIS));
    jp.add(buildSnipeSettings(pasteListener));
    jp.add(buildExtraSettings());
    Box spareBox = Box.createHorizontalBox();
    spareBox.add(new JLabel(""));
    jp.add(spareBox);
    jp.add(buildGixenSettings());
    this.add(panelPack(jp), BorderLayout.CENTER);

    gixenDisabledWarning = OptionUI.getHTMLLabel(DISABLED_HTML);
    checkGixenEligibility();
    this.add(gixenDisabledWarning, BorderLayout.SOUTH);

    JConfig.registerListener(new com.cyberfox.util.config.JConfig.ConfigListener() {
      public void updateConfiguration() {
        checkGixenEligibility();
      }
    });
    updateValues();
  }
}
