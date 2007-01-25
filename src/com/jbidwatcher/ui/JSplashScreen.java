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

import java.awt.*;
import javax.swing.*;
import javax.swing.border.*;

public class JSplashScreen extends Window {
  JProgressBar statusBar;

  // JNews's constructor
  public JSplashScreen(ImageIcon CoolPicture) {
    super(new Frame());

    // Create a JPanel so we can use a BevelBorder
    JPanel PanelForBorder=new JPanel(new BorderLayout());
    PanelForBorder.setLayout(new BorderLayout());
    PanelForBorder.add(new JLabel(CoolPicture), BorderLayout.CENTER);
    PanelForBorder.add(statusBar=new JProgressBar(0, 100), BorderLayout.SOUTH);
    PanelForBorder.setBorder(new BevelBorder(BevelBorder.RAISED));

    add(PanelForBorder);    
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
}
