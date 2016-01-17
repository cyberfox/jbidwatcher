package com.jbidwatcher.ui.config;
/*
 * Copyright (c) 2000-2007, CyberFOX Software, Inc. All Rights Reserved.
 *
 * Developed by mrs (Morgan Schweers)
 */

import com.jbidwatcher.ui.util.JPasteListener;
import com.jbidwatcher.util.config.JConfig;

import javax.swing.*;
import javax.swing.text.JTextComponent;
import javax.swing.event.*;
import java.awt.*;

public abstract class JConfigTab extends JPanel {
  protected JPasteListener pasteListener;
  public abstract String getTabName();
  public abstract void cancel();
  public abstract void apply();

  public abstract void updateValues();

  public static Box makeLine(JComponent first, JComponent second) {
    Box onelineBox = Box.createHorizontalBox();

    onelineBox.add(first);
    onelineBox.add(second);

    return(onelineBox);
  }

  public void adjustField(JComponent jc, String accessibleName, DocumentListener dl) {
    if(jc == null) return;

    jc.addMouseListener(pasteListener);
    tweakTextField(jc, accessibleName, dl);
  }

  public static void tweakTextField(JComponent jc, String accessibleName, DocumentListener dl) {
    if(jc instanceof JTextField) {
      if(dl != null) {
        ((JTextComponent) jc).getDocument().addDocumentListener(dl);
      }
      ((JTextComponent) jc).setEditable(true);
    }
    jc.getAccessibleContext().setAccessibleName(accessibleName);
  }

  public static JPanel panelPack(JPanel jp) {
    JPanel outer = new JPanel();

    outer.setLayout(new BorderLayout());
    outer.add(jp, "North");

    return outer;
  }

  //
  //  If the radio button is selected, return either the default
  //  value, or if that value is null, the correct value from the
  //  configuration file, or if THAT value is also null, an empty
  //  string.
  //
  //  If the radio button is NOT selected, return "<disabled>", so
  //  there's some marker in the text field that it's not editable
  //  right now.
  //
  String getConfigValue(JToggleButton jrb, String configValue, String defaultValue) {
    String outputValue;

    if (jrb.isSelected()) {
      if (defaultValue == null) {
        outputValue = JConfig.queryConfiguration(configValue);
        if (outputValue == null) {
          return "";
        } else {
          return outputValue;
        }
      }
      return defaultValue;
    } else {
      return "<disabled>";
    }
  }
}
