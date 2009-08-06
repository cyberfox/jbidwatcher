package com.jbidwatcher.ui.config;
/*
 * Copyright (c) 2000-2007, CyberFOX Software, Inc. All Rights Reserved.
 *
 * Developed by mrs (Morgan Schweers)
 */

import com.jbidwatcher.platform.Platform;
import com.jbidwatcher.util.config.*;
import com.jbidwatcher.util.Constants;
import com.jbidwatcher.ui.util.JBidFrame;
import com.jbidwatcher.ui.util.OptionUI;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.List;
import java.util.ArrayList;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.JLabel;

/**
 * Implements the "Configure" frames. This holds all the configuration options.
 *
 * @version $Revision: 1.38 $
 */
public class JConfigFrame implements ActionListener {
  private JFrame mainFrame;
  private boolean buttonPressed = false;
  private List<JConfigTab> allTabs;
  private static int cfgCount = 1;

  public void spinWait() {
    while(!buttonPressed) {
      try { //noinspection MultiplyOrDivideByPowerOfTwo,BusyWait
        Thread.sleep(Constants.ONE_SECOND/2);
      } catch(InterruptedException ignored) {
        //  We don't care that we caught an exception, just that we woke up.
      }
    }
  }

  public JConfigFrame() {
    mainFrame = createConfigFrame();
    Rectangle rec = OptionUI.findCenterBounds(mainFrame.getPreferredSize());
    mainFrame.setLocation(rec.x, rec.y);
    show();
  }

  public final void show() {
    for (JConfigTab jct : allTabs) {
      jct.updateValues();
    }
    mainFrame.setState(Frame.NORMAL);
    mainFrame.setVisible(true);
  }

  private void applyAll() {
    for (JConfigTab jct : allTabs) {
      jct.apply();
    }
  }

  private void cancelAll() {
    for (JConfigTab jct : allTabs) {
      jct.cancel();
    }
  }

  public void actionPerformed(ActionEvent ae) {
    String actionString = ae.getActionCommand();

    if(actionString.equals("Save")) {
      applyAll();
      JConfig.updateComplete();
      JConfig.saveConfiguration();
    } else if(actionString.equals("Cancel")) {
      cancelAll();
    }

    mainFrame.setVisible(false);
    buttonPressed = true;
  }

  public static JPanel buildButtonPane(ActionListener al) {
    JPanel tp = new JPanel();

    JButton cancelButton = new JButton("Cancel");
    cancelButton.setToolTipText("Cancel any changes made.");
    JButton saveButton = new JButton("Save");
    saveButton.setToolTipText("Apply changes and save settings.");

    tp.add(cancelButton, BorderLayout.WEST);
    tp.add(  saveButton, BorderLayout.CENTER);

    cancelButton.addActionListener(al);
    saveButton.addActionListener(al);

    return(tp);
  }

  private static void anotherConfig() {
    cfgCount++;
  }

  private JFrame createConfigFrame() {
    JTabbedPane jtpAllTabs = new JTabbedPane();
    final JFrame w;

    if(cfgCount == 2) {
      w = new JBidFrame("Configuration Manager (2)");
    } else {
      anotherConfig();
      w = new JBidFrame("Configuration Manager");
    }

    Container contentPane = w.getContentPane();
    contentPane.setLayout(new BorderLayout());
    contentPane.add(jtpAllTabs, BorderLayout.CENTER);

    allTabs = new ArrayList<JConfigTab>();

    //  Add all non-server-specific tabs here.
    allTabs.add(new JConfigGeneralTab());
    allTabs.add(new JConfigEbayTab());

    //  Stub the browser tab under MacOSX, so they don't try to use it.
    if(Platform.isMac()) {
      allTabs.add(new JConfigMacBrowserTab());
    } else {
      allTabs.add(new JConfigBrowserTab());
    }
    allTabs.add(new JConfigFirewallTab());
    allTabs.add(new JConfigSnipeTab());
//    if(JConfig.queryConfiguration("allow.my_jbidwatcher", "false").equals("true"))
      allTabs.add(new JConfigMyJBidwatcherTab());
    allTabs.add(new JConfigFilePathTab());
    allTabs.add(new JConfigWebserverTab());
    allTabs.add(new JConfigDatabaseTab());

    allTabs.add(new JConfigSecurityTab());
    allTabs.add(new JConfigAdvancedTab());

    //  HACKHACK -- Presently all tabs created need to have 3 rows of
    //  GridLayout.  In general, all tabs have to have the same number
    //  of rows.  This is likely to suck, in the long run.  For now,
    //  it's the requirement.  If you have more or less, the display
    //  either for that tab (if it has less), or for all the others
    //  (if it has more) will look somewhat wonky.

    //  Loop over all tabs, and add them to the display.
    for (JConfigTab allTab : allTabs) {
      allTab.setOpaque(true);
      jtpAllTabs.addTab(allTab.getTabName(), allTab);
    }

    jtpAllTabs.setSelectedIndex(0);
    contentPane.add(buildButtonPane(this), BorderLayout.SOUTH);

    w.addWindowListener(new IconifyingWindowAdapter(w));
    w.pack();
    w.setResizable(false);
    return w;
  }

  private class JConfigSecurityTab extends JConfigStubTab {
    public String getTabName() { return("Security"); }
  }

  private final class JConfigMacBrowserTab extends JConfigStubTab {
    public String getTabName() { return("Browser"); }

    JConfigMacBrowserTab() {
      JLabel newLabel = new JLabel("Under MacOSX, the browser does not need to be configured.");
      setLayout(new BorderLayout());
      add(newLabel, BorderLayout.CENTER);
    }
  }

  public static class IconifyingWindowAdapter extends WindowAdapter {
    private final JFrame _window;

    public IconifyingWindowAdapter(JFrame window) {
      _window = window;
    }

    public void windowIconified(WindowEvent we) {
      super.windowIconified(we);
      if(Platform.supportsTray() && Platform.isTrayEnabled()) {
        if(JConfig.queryConfiguration("windows.tray", "true").equals("true") &&
           JConfig.queryConfiguration("windows.minimize", "true").equals("true")) {
          _window.setVisible(false);
        }
      }
    }

    public void windowDeiconified(WindowEvent we) {
      super.windowDeiconified(we);
      if(Platform.supportsTray() && Platform.isTrayEnabled()) {
        if(JConfig.queryConfiguration("windows.tray", "true").equals("true") &&
           JConfig.queryConfiguration("windows.minimize", "true").equals("true")) {
          _window.setState(Frame.NORMAL);
          _window.setVisible(true);
        }
      }
    }
  }
}
