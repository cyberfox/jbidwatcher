package com.jbidwatcher.config;
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

import com.jbidwatcher.ui.JPasteListener;
import com.jbidwatcher.config.JBConfig;

import java.awt.*;
import java.awt.event.*;
import javax.swing.*;

public class JConfigBrowserTab extends JConfigTab {
  JTextField linuxBrowserLaunchCommand;
  JTextField windowsBrowserLaunchCommand;
  JCheckBox overrideDefault;

  public String getTabName() { return "Browser"; }
  public void cancel() { }

  public boolean apply() {
    JConfig.setConfiguration("browser.launch.Linux", linuxBrowserLaunchCommand.getText());
    JConfig.setConfiguration("browser.launch.Windows", windowsBrowserLaunchCommand.getText());
    JConfig.setConfiguration("browser.override", overrideDefault.isSelected()?"true":"false");

    return true;
  }

  public void updateValues() {
    String overrideOn = JConfig.queryConfiguration("browser.override", "false");

    linuxBrowserLaunchCommand.setText(JConfig.queryConfiguration("browser.launch.Linux", "netscape"));
    windowsBrowserLaunchCommand.setText(JConfig.queryConfiguration("browser.launch.Windows", "start netscape"));
    overrideDefault.setSelected(overrideOn.equals("true"));
  }

  private JPanel buildLinuxBrowserLaunch() {
    JPanel tp = new JPanel();
    tp.setBorder(BorderFactory.createTitledBorder("Browser Command"));
    tp.setLayout(new BoxLayout(tp, BoxLayout.Y_AXIS));

    linuxBrowserLaunchCommand = new JTextField();
    linuxBrowserLaunchCommand.addMouseListener(JPasteListener.getInstance());

    linuxBrowserLaunchCommand.setText(JConfig.queryConfiguration("browser.launch.Linux"));
    linuxBrowserLaunchCommand.setEditable(true);
    linuxBrowserLaunchCommand.getAccessibleContext().setAccessibleName("Command to use to launch the web browser under Linux");

    windowsBrowserLaunchCommand = new JTextField();
	windowsBrowserLaunchCommand.addMouseListener(JPasteListener.getInstance());

    windowsBrowserLaunchCommand.setText(JConfig.queryConfiguration("browser.launch.Windows"));
    windowsBrowserLaunchCommand.setEditable(true);
    windowsBrowserLaunchCommand.getAccessibleContext().setAccessibleName("Command to use to launch the web browser under Windows");

    tp.add(new JLabel("Linux command:"));
    tp.add(linuxBrowserLaunchCommand);
    tp.add(new JLabel("Windows command:"));
    tp.add(windowsBrowserLaunchCommand);
    return(tp);
  }

  private JPanel buildOverridePreference() {
    JPanel tp = new JPanel();
    tp.setBorder(BorderFactory.createTitledBorder("Browser Command"));
    tp.setLayout(new GridLayout(2, 2));
    
    JPanel buttonPanel = new JPanel();
    JButton detectButton = new JButton("Detect Browser");
    String overrideOn = JConfig.queryConfiguration("browser.override", "false");

    buttonPanel.setLayout(new BorderLayout());

    overrideDefault = new JCheckBox("Override 'detected' browser");
    overrideDefault.setSelected(overrideOn.equals("true"));

    detectButton.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent ae) {
          if(ae.getActionCommand().equals("Detect Browser")) {
            if(JConfig.getOS().equalsIgnoreCase("windows")) {
              String browser;

              browser = JBConfig.getBrowserCommand();
              if(browser != null) {
                windowsBrowserLaunchCommand.setText(browser);
              } else {
                JOptionPane.showMessageDialog(null, "This Java Virtual Machine cannot detect the default browser type.\nUpgrading to a post-1.4 version of Java might help.",
                                              "Cannot detect browser", JOptionPane.INFORMATION_MESSAGE);
              }
            } else {
              linuxBrowserLaunchCommand.setText(JBConfig.getBrowserCommand());
            }
          }
        }
      });

    tp.add(buttonPanel, BorderLayout.WEST);
    buttonPanel.add(detectButton, BorderLayout.WEST);
    buttonPanel.add(buildTestButton(), BorderLayout.EAST);
    tp.add(overrideDefault, BorderLayout.SOUTH);

    return tp;
  }

  private JButton buildTestButton() {
    JButton testButton = new JButton("Test Browser");

    testButton.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent ae) {
          if(ae.getActionCommand().equals("Test Browser")) {
            boolean browserWorked = false;
            apply();
            browserWorked = JBConfig.launchBrowser("http://www.jbidwatcher.com");
            if(!browserWorked) {
              JOptionPane.showMessageDialog(null, "<html><body>Failed to launch browser.  The error log contains more details,<br>" +
                                            "but some common problems are:" +
                                            "<ul><li>Your path does not include the binary to launch.</li>" +
                                            "<li>The executable is misnamed.</li>" +
                                            "<li>The executable path contains spaces, but is not quoted.</li></ul>" +
                                            "You should be able to copy the browser line, and execute it\n" +
                                            "at the command line, followed by a URL, and it should go to\n" +
                                            "that page.", "Failed to launch browser", JOptionPane.INFORMATION_MESSAGE);
            }
          }
        }
      });

    return testButton;
  }

  public JConfigBrowserTab() {
    super();
    this.setLayout(new BorderLayout());
    this.add(panelPack(buildLinuxBrowserLaunch()), BorderLayout.NORTH);
    this.add(panelPack(buildOverridePreference()), BorderLayout.CENTER);
  }
}
