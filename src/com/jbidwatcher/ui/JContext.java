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

import com.jbidwatcher.Constants;

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

  protected JButton makeButton(String name) {
    return makeButton(name, name);
  }

  protected JButton makeButton(String name, String cmd) {
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

  protected static JMenuItem makeGeneralMenuItem(String menuText, String actionCmd) {
    JMenuItem newItem = new JMenuItem();
    newItem.setText(menuText);
    newItem.setActionCommand(actionCmd);

    return newItem;
  }
}
