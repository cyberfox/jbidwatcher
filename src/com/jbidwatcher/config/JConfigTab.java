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

import com.jbidwatcher.ui.JPasteListener;

import javax.swing.*;
import javax.swing.text.JTextComponent;
import javax.swing.event.*;
import java.awt.*;

public abstract class JConfigTab extends JPanel {
  public abstract String getTabName();
  public abstract void cancel();
  public abstract boolean apply();

  public abstract void updateValues();

  public static Box makeLine(JComponent first, JComponent second) {
    Box onelineBox = Box.createHorizontalBox();

    onelineBox.add(first);
    onelineBox.add(second);

    return(onelineBox);
  }

  protected Box makeLine(JComponent first, JComponent second, JComponent third) {
    Box onelineBox = Box.createHorizontalBox();

    onelineBox.add(first);
    onelineBox.add(second);
    onelineBox.add(third);

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
