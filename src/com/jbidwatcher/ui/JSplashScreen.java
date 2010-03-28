package com.jbidwatcher.ui;
/*
 * Copyright (c) 2000-2007, CyberFOX Software, Inc. All Rights Reserved.
 *
 * Developed by mrs (Morgan Schweers)
 */

import com.jbidwatcher.util.queue.MQFactory;
import com.jbidwatcher.util.queue.MessageQueue;

import javax.swing.*;
import javax.swing.border.BevelBorder;
import java.awt.*;

public class JSplashScreen extends Window implements MessageQueue.Listener {
  JProgressBar statusBar;

  public JSplashScreen(ImageIcon coolPicture) {
    super(new Frame());

    MQFactory.getConcrete("splash").registerListener(this);
    // Create a JPanel so we can use a BevelBorder
    JPanel panelForBorder=new JPanel(new BorderLayout());
    panelForBorder.setLayout(new BorderLayout());
    panelForBorder.add(new JLabel(coolPicture), BorderLayout.CENTER);
    panelForBorder.add(statusBar = new JProgressBar(0, 100), BorderLayout.SOUTH);
    panelForBorder.setBorder(new BevelBorder(BevelBorder.RAISED));

    add(panelForBorder);
    pack();

    // Plonk it on center of screen
    Dimension WindowSize=getSize(), ScreenSize=Toolkit.getDefaultToolkit().getScreenSize();

    setBounds((ScreenSize.width-WindowSize.width)/2,
              (ScreenSize.height-WindowSize.height)/2, WindowSize.width,
              WindowSize.height);
    setVisible(true);
  }

  public void showStatus(int currentStatus) {
    // Update Splash-Screen's status bar in AWT thread
    statusBar.setValue(currentStatus);
  }

  public void setWidth(int maxCount) {
    statusBar.setMaximum(maxCount);
  }

  public void setWidthValue(int maxCount, int currentStatus) {
    statusBar.setMaximum(maxCount);
    statusBar.setValue(currentStatus);
  }

  public void close() {
    // Close and dispose Window in AWT thread
    SwingUtilities.invokeLater(new CloseJNSplash());
  }

  class CloseJNSplash implements Runnable {
    public synchronized void run() {
      setVisible(false);
      dispose();
    }
  }

  private int parseInt(String s) {
    try {
      return Integer.parseInt(s);
    } catch (NumberFormatException nfe) {
      //  don't really do anything, since this isn't critical.
    }
    return 0;
  }

  public void message(String msg) {
    if (msg.equals("OFF")) {
      statusBar.setStringPainted(false);
      statusBar.setString("");
    } else {
      statusBar.setStringPainted(true);
      statusBar.setString(msg);
    }
  }

  public void messageAction(Object deQ) {
    String msg = (String) deQ;
    if(msg.startsWith("SET ")) {
      int amount = parseInt(msg.substring(4));
      showStatus(amount);
    } else if(msg.startsWith("WIDTH ")) {
      int width = parseInt(msg.substring(6));
      setWidth(width);
    } else if(msg.equals("CLOSE")) {
      close();
    } else if(msg.equals("MESSAGE")) {
      message(msg.substring(8));
    }
  }
}
