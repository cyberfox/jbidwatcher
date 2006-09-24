package com.jbidwatcher.ui;
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

import com.jbidwatcher.platform.Platform;

import java.awt.event.*;
import javax.swing.*;

public class JPasteListener extends MouseAdapter {
  private static JPopupMenu _jpm = null;
  private static JPasteListener _instance = null;
  private static JMenuItem _pasteMenuItem = null;
  private static MouseEvent _me = null;
  private static ActionListener _aep = new ActionListener() {
      public void actionPerformed(ActionEvent ae) {
        JTextField jtfEvent = (JTextField)_me.getComponent();
        jtfEvent.paste();
      }
    };

  private JPasteListener() {
    _jpm = new JPopupMenu();
    _jpm.add("Cut").setEnabled(false);
    _jpm.add("Copy").setEnabled(false);
    _pasteMenuItem = _jpm.add("Paste");
    _pasteMenuItem.setEnabled(true);
    _pasteMenuItem.addActionListener(_aep);
    _jpm.add("Delete").setEnabled(false);
  }

  private boolean isHit(int mouseModifiers, int whatEvent) {
    return (mouseModifiers & whatEvent) == whatEvent;
  }

  public void mousePressed(MouseEvent me) {
    if(me.isPopupTrigger()) {
      _me = me;
      _jpm.show(me.getComponent(), me.getX(), me.getY());
    }
  }

  public void mouseReleased(MouseEvent me) {
    if(me.isPopupTrigger()) {
      _me = me;
      _jpm.show(me.getComponent(), me.getX(), me.getY());
    }
  }

  public void mouseClicked(MouseEvent me) {
    int modifiers = me.getModifiers();

    //  If they hit the middle mouse button, then paste!
    if( isHit(modifiers, MouseEvent.BUTTON2_MASK) && !Platform.isLinux() ) {
      JTextField jtfEvent = (JTextField)me.getComponent();
      jtfEvent.paste();
    } else if(me.isPopupTrigger()) {
      _me = me;
      _jpm.show(me.getComponent(), me.getX(), me.getY());
    }
  }

  public static JPasteListener getInstance() {
    if(_instance == null) {
      _instance = new JPasteListener();
    }

    return _instance;
  }
}
