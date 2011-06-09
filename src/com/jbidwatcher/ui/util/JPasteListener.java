package com.jbidwatcher.ui.util;
/*
 * Copyright (c) 2000-2007, CyberFOX Software, Inc. All Rights Reserved.
 *
 * Developed by mrs (Morgan Schweers)
 */

import com.cyberfox.util.platform.Platform;

import java.awt.event.*;
import javax.swing.*;

public class JPasteListener extends MouseAdapter {
  private static JPopupMenu _jpm = null;
  private static JPasteListener _instance = null;
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
    JMenuItem paste = _jpm.add("Paste");
    paste.setEnabled(true);
    paste.addActionListener(_aep);
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
