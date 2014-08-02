package com.jbidwatcher.platform;
/*
 * Copyright (C) 2004 Sun Microsystems, Inc. All rights reserved. Use is
 * subject to license terms.
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the Lesser GNU General Public License as
 * published by the Free Software Foundation; either version 2 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307
 * USA.
 */

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.net.URL;

import com.jbidwatcher.util.queue.MQFactory;
import com.jbidwatcher.util.queue.MessageQueue;
import com.jbidwatcher.util.config.JConfig;
import com.jbidwatcher.util.Constants;

public class Tray implements ItemListener, MessageQueue.Listener {
  private JMenuItem hideRestore;
  private SystemTray systemTray = null;
  private TrayIcon trayIcon = null;
  private TrayIcon.MessageType infoMessageType = null;

  public void itemStateChanged(ItemEvent e) {
    //  TODO(cyberfox) - It's unclear why this is necessary.
  }

  private class TrayMenuAction implements ActionListener {
    public void actionPerformed(ActionEvent e) {
      String cmd = e.getActionCommand();

      if(cmd.startsWith("SEARCH")) {
        MQFactory.getConcrete("user").enqueue("SEARCH");
        return;
      } else if(cmd.startsWith("CONFIGURE")) {
        MQFactory.getConcrete("user").enqueue("Configure");
        return;
      }
      //  If there are no spaces, it's a command to pass straight to the Swing code.
      if(cmd.indexOf(' ') == -1) {
        MQFactory.getConcrete("Swing").enqueue(cmd);
      } else {
        String s = "Action event detected." + '\n' + "    Event source: " + e.getSource() + " (an instance of " + e.getSource().getClass().getCanonicalName() + ")\n    ActionCommand: " + e.getActionCommand();
        JConfig.log().logDebug(s);
      }
    }
  }

  private Tray() {
    TrayMenuAction tma = new TrayMenuAction();
    System.setProperty("javax.swing.adjustPopupLocationToFit", "false");
    JPopupMenu menu = new JPopupMenu("JBidwatcher Tray Menu");

    // a group of JMenuItems
    hideRestore = new JMenuItem("Hide");
    hideRestore.getAccessibleContext().setAccessibleDescription("Restores JBidwatcher window to the display.");
    hideRestore.addActionListener(tma);
    hideRestore.setActionCommand("HIDE");
    menu.add(hideRestore);

    menu.addSeparator();
    JMenuItem searchMenuItem = new JMenuItem("Search...");
    searchMenuItem.addActionListener(tma);
    searchMenuItem.setActionCommand("SEARCH");
    menu.add(searchMenuItem);

    JMenuItem configMenuItem = new JMenuItem("Configure...");
    configMenuItem.addActionListener(tma);
    configMenuItem.setActionCommand("CONFIGURE");
    menu.add(configMenuItem);
    menu.addSeparator();

    JMenuItem quitMenuItem = new JMenuItem("Quit", KeyEvent.VK_Q);
    quitMenuItem.setActionCommand("QUIT");
    quitMenuItem.getAccessibleContext().setAccessibleDescription("Close JBidwatcher.");
    quitMenuItem.addActionListener(tma);
    menu.add(quitMenuItem);

    // ImageIcon jbw_icon = new ImageIcon("duke.gif");
    URL iconURL = JConfig.getResource(JConfig.queryConfiguration("icon", "/jbidwatch64.jpg"));
    ImageIcon jbw_icon = new ImageIcon(iconURL);

    setSystemTray(menu, jbw_icon);
  }

  private boolean setSystemTray(final JPopupMenu menu, ImageIcon jbw_icon) {
    systemTray = SystemTray.getSystemTray();
    trayIcon = new TrayIcon(jbw_icon.getImage(), "JBidwatcher", null);
    trayIcon.addMouseListener(new MouseAdapter() {
      public void mouseReleased(MouseEvent e) {
        if (e.isPopupTrigger()) {
          menu.setLocation(e.getX(), e.getY());
          menu.setInvoker(menu);
          menu.setVisible(true);
        }
      }
    });


    trayIcon.setImageAutoSize(true);
    trayIcon.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        MQFactory.getConcrete("Swing").enqueue("VISIBILITY");
      }
    });

    infoMessageType = TrayIcon.MessageType.INFO;
    return true;
  }

  public void messageAction(Object deQ) {
    String msg = (String)deQ;
    if(msg.startsWith("TOOLTIP ")) {
      String msgText = Constants.PROGRAM_NAME + ' ' + Constants.PROGRAM_VERS + '\n' + msg.substring(8);
      trayIcon.setToolTip(msgText);
    } else if(msg.startsWith("NOTIFY ")) {
      trayIcon.displayMessage("JBidwatcher Alert", msg.substring(7), infoMessageType);
    } else if(msg.startsWith("HIDDEN")) {
      hideRestore.setText("Restore");
      hideRestore.setActionCommand("RESTORE");
    } else if(msg.startsWith("RESTORED")) {
      hideRestore.setText("Hide");
      hideRestore.setActionCommand("HIDE");
    } else if(msg.startsWith("TRAY")) {
      String onOff = msg.substring(5);
      if(onOff.equals("on")) {
        try {
          systemTray.add(trayIcon);
        } catch (AWTException noTray) {
          JConfig.log().logMessage("Could not add the system tray; it's not currently visible: " + noTray.getMessage());
        }
      } else {
        systemTray.remove(trayIcon);
      }
    }
  }

  public static void start() {
    MQFactory.getConcrete("tray").registerListener(new Tray());
  }
}
