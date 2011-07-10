package com.jbidwatcher.ui.config;
/*
 * Copyright (c) 2000-2007, CyberFOX Software, Inc. All Rights Reserved.
 *
 * Developed by mrs (Morgan Schweers)
 */

import com.jbidwatcher.ui.util.JPasteListener;

import javax.swing.*;
import javax.swing.text.JTextComponent;
import javax.swing.event.*;
import java.awt.*;

public abstract class JConfigTab extends JPanel {
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

  public static void adjustField(JComponent jc, String accessibleName, DocumentListener dl) {
    if(jc == null) return;

    jc.addMouseListener(JPasteListener.getInstance());
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
}
