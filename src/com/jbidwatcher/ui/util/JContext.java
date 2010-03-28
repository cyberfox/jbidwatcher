package com.jbidwatcher.ui.util;
/*
 * Copyright (c) 2000-2007, CyberFOX Software, Inc. All Rights Reserved.
 *
 * Developed by mrs (Morgan Schweers)
 */

import com.jbidwatcher.util.Constants;

import javax.swing.*;
import java.util.TreeMap;
import java.awt.event.MouseEvent;

/** @noinspection AbstractClassExtendsConcreteClass*/
public abstract class JContext extends JMouseAdapter {
  TreeMap<String, JMenuItem> m_menuToText;

  protected JContext(JPopupMenu inPop) {
    super(inPop);
    m_menuToText = new TreeMap<String, JMenuItem>();
  }

  protected JContext() {
    m_menuToText = new TreeMap<String, JMenuItem>();
  }

  protected void beforePopup(JPopupMenu inPopup, MouseEvent e) {
    super.beforePopup(inPopup, e);
    enableAll();
  }

  protected void enableAll() {
    for (JMenuItem jmi : m_menuToText.values()) {
      jmi.setEnabled(true);
    }
  }

  protected void enable(String menuText) {
    setEnabled(menuText, true);
  }

  protected void disable(String menuText) {
    setEnabled(menuText, false);
  }

  protected void setEnabled(String menuText, boolean enabled) {
    JMenuItem pick = m_menuToText.get(menuText);
    if(pick != null) {
      pick.setEnabled(enabled);
    }
  }

  protected void rename(String menuText, String newMenuText) {
    JMenuItem pick = m_menuToText.get(menuText);
    if(pick != null) {
      pick.setText(newMenuText);
      m_menuToText.remove(menuText);
      m_menuToText.put(newMenuText, pick);
    }
  }

  public JButton makeButton(String name) {
    return makeButton(name, name);
  }

  public JButton makeButton(String name, String cmd) {
    JButton jb = new JButton(name);
    jb.setActionCommand(Constants.NO_CONTEXT + cmd);
    jb.addActionListener(this);

    return jb;
  }

  protected JMenuItem makeMenuItem(String menuText) {
    return makeMenuItem(menuText, menuText);
  }

  protected JMenuItem makeMenuItem(String menuText, String actionCmd) {
    JMenuItem newItem = new JMenuItem();
    newItem.setText(menuText);
    newItem.setActionCommand(actionCmd);

    m_menuToText.put(menuText, newItem);
    return newItem;
  }

  public static JMenuItem makeGeneralMenuItem(String menuText, String actionCmd) {
    JMenuItem newItem = new JMenuItem();
    newItem.setText(menuText);
    newItem.setActionCommand(actionCmd);

    return newItem;
  }
}
