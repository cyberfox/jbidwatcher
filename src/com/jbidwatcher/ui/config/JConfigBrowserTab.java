package com.jbidwatcher.ui.config;
/*
 * Copyright (c) 2000-2007, CyberFOX Software, Inc. All Rights Reserved.
 *
 * Developed by mrs (Morgan Schweers)
 */

import com.jbidwatcher.ui.util.JPasteListener;
import com.jbidwatcher.ui.util.JBEditorPane;
import com.jbidwatcher.ui.util.OptionUI;
import com.jbidwatcher.platform.Browser;
import com.jbidwatcher.util.config.JConfig;

import java.awt.*;
import java.awt.event.*;
import javax.swing.*;

public class JConfigBrowserTab extends JConfigTab {
  JTextField linuxBrowserLaunchCommand;
  JTextField windowsBrowserLaunchCommand;
  JCheckBox overrideDefault;

  public String getTabName() { return "Browser"; }
  public void cancel() { }

  public void apply() {
    JConfig.setConfiguration("browser.launch.Linux", linuxBrowserLaunchCommand.getText());
    JConfig.setConfiguration("browser.launch.Windows", windowsBrowserLaunchCommand.getText());
    JConfig.setConfiguration("browser.override", overrideDefault.isSelected()?"true":"false");

  }

  public void updateValues() {
    String overrideOn = JConfig.queryConfiguration("browser.override", "false");

    linuxBrowserLaunchCommand.setText(JConfig.queryConfiguration("browser.launch.Linux", "firefox"));
    windowsBrowserLaunchCommand.setText(JConfig.queryConfiguration("browser.launch.Windows", "start netscape"));
    overrideDefault.setSelected(overrideOn.equals("true"));
  }

  private JPanel buildLinuxBrowserLaunch(JPasteListener pasteListener) {
    JPanel tp = new JPanel();
    tp.setBorder(BorderFactory.createTitledBorder("Browser Command"));
    tp.setLayout(new BoxLayout(tp, BoxLayout.Y_AXIS));

    linuxBrowserLaunchCommand = new JTextField();
    linuxBrowserLaunchCommand.addMouseListener(pasteListener);

    linuxBrowserLaunchCommand.setText(JConfig.queryConfiguration("browser.launch.Linux"));
    linuxBrowserLaunchCommand.setEditable(true);
    linuxBrowserLaunchCommand.getAccessibleContext().setAccessibleName("Command to use to launch the web browser under Linux");

    windowsBrowserLaunchCommand = new JTextField();
    windowsBrowserLaunchCommand.addMouseListener(pasteListener);

    windowsBrowserLaunchCommand.setText(JConfig.queryConfiguration("browser.launch.Windows"));
    windowsBrowserLaunchCommand.setEditable(true);
    windowsBrowserLaunchCommand.getAccessibleContext().setAccessibleName("Command to use to launch the web browser under Windows");

    tp.add(new JLabel("Linux command:"));
    tp.add(linuxBrowserLaunchCommand);
    tp.add(new JLabel("Windows command:"));
    tp.add(windowsBrowserLaunchCommand);
    if(!JConfig.getOS().equals("Linux") && !JConfig.getOS().equals("Windows")) {
      String otherPlatformNotice = "<html><body><div style=\"margin-left: 10px; font-size: 0.96em;\"><i>To set the browser for other platforms, go to the Advanced tab, and set<br>" +
          "a key of: </i><code>browser.launch." + JConfig.getOS() + "</code><i> to a value of whatever the path to your browser is.</i></div></body></html>";
      JBEditorPane jep = OptionUI.getHTMLLabel(otherPlatformNotice);
      tp.add(jep);
    }
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

              browser = Browser.getBrowserCommand();
              if(browser != null) {
                windowsBrowserLaunchCommand.setText(browser);
              } else {
                JOptionPane.showMessageDialog(null, "This Java Virtual Machine cannot detect the default browser type.\nUpgrading to a post-1.4 version of Java might help.",
                                              "Cannot detect browser", JOptionPane.INFORMATION_MESSAGE);
              }
            } else {
              linuxBrowserLaunchCommand.setText(Browser.getBrowserCommand());
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
            apply();
            if(!Browser.launchBrowser("http://www.jbidwatcher.com")) {
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

  public JConfigBrowserTab(JPasteListener pasteListener) {
    super();
    this.setLayout(new BorderLayout());
    this.add(panelPack(buildLinuxBrowserLaunch(pasteListener)), BorderLayout.NORTH);
    this.add(panelPack(buildOverridePreference()), BorderLayout.CENTER);
  }
}
