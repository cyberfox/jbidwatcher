package com.jbidwatcher.auction.server;
/*
 * Copyright (c) 2000-2007, CyberFOX Software, Inc. All Rights Reserved.
 *
 * Developed by mrs (Morgan Schweers)
 */

import java.awt.event.*;
import javax.swing.*;

public abstract class ServerMenu implements ActionListener {
  protected JMenu mMenu;

  public abstract void initialize();

  public ServerMenu(String serverName, char ch) {
    mMenu = new JMenu(serverName);
    mMenu.setMnemonic(ch);
  }

  public void addMenuItem(String inName) {
    JMenuItem constructItem = new JMenuItem(inName);
    constructItem.addActionListener(this);

    mMenu.add(constructItem);
  }

  public void addMenuItem(String inName, char ch) {
    JMenuItem constructItem = new JMenuItem();
    constructItem.setText(inName);
    constructItem.setMnemonic(ch);
    constructItem.addActionListener(this);

    mMenu.add(constructItem);
  }

  public void addMenuItem(String inName, String actionCmd, char ch) {
    JMenuItem constructItem = new JMenuItem();
    constructItem.setText(inName);
    constructItem.setMnemonic(ch);
    constructItem.setActionCommand(actionCmd);
    constructItem.addActionListener(this);

    mMenu.add(constructItem);
  }

  public JMenu getMenu() {
    return mMenu;
  }
}
