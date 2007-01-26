package com.jbidwatcher.ui;
/*
 * Copyright (c) 2000-2007, CyberFOX Software, Inc. All Rights Reserved.
 *
 * Developed by mrs (Morgan Schweers)
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
