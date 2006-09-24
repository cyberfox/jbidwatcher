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

import com.jbidwatcher.ui.JBidMenuBar;

import java.awt.event.*;
import javax.swing.*;

public abstract class ServerMenu implements ActionListener {
  protected JMenu _thisMenu;

  public abstract void initialize();

  public ServerMenu(String serverName, char ch) {
    _thisMenu = new JMenu(serverName);
    _thisMenu.setMnemonic(ch);
    JBidMenuBar.getInstance(null).add(_thisMenu);
  }

  public ServerMenu(String serverName) {
    _thisMenu = new JMenu(serverName);
    JBidMenuBar.getInstance(null).add(_thisMenu);
  }

  public ServerMenu() {
  }

  public void addMenuItem(String inName) {
    JMenuItem constructItem = new JMenuItem(inName);
    constructItem.addActionListener(this);

    _thisMenu.add(constructItem);
  }

  public void addMenuItem(String inName, char ch) {
    JMenuItem constructItem = new JMenuItem();
    constructItem.setText(inName);
    constructItem.setMnemonic(ch);
    constructItem.addActionListener(this);

    _thisMenu.add(constructItem);
  }

  public void addMenuItem(String inName, String actionCmd, char ch) {
    JMenuItem constructItem = new JMenuItem();
    constructItem.setText(inName);
    constructItem.setMnemonic(ch);
    constructItem.setActionCommand(actionCmd);
    constructItem.addActionListener(this);

    _thisMenu.add(constructItem);
  }
}
