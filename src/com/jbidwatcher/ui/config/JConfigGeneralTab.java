package com.jbidwatcher.ui.config;
/*
 * Copyright (c) 2000-2007, CyberFOX Software, Inc. All Rights Reserved.
 *
 * Developed by mrs (Morgan Schweers)
 */

import com.cyberfox.util.platform.Platform;
import com.jbidwatcher.util.queue.MQFactory;
import com.jbidwatcher.util.config.JConfig;

import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Arrays;

import javax.swing.BorderFactory;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JOptionPane;

public class JConfigGeneralTab extends JConfigTab {
  private JCheckBox doVersionUpdateCheckBox;
  private JCheckBox debuggingBox;
  private JCheckBox loggingBox;
  private JCheckBox ignoreDeletedBox;
  private JCheckBox allowConfigUpdateBox;
  private JCheckBox allowArchival;
  private JCheckBox timeSyncBox;
  private JCheckBox disableThumbnailBox;
  private JComboBox allowMetricsReporting;

  private JCheckBox macMetalBox = null;
  private JCheckBox winTrayBox = null;
  private JCheckBox minimizeTrayBox = null;

  private String[] metricsChoices = {
      "Ask again later",
      "Yes",
      "Pre-release only",
      "No"
  };

  private String[] metricsValues = { "ask", "true", "pre", "false" };

  private JComboBox dclickAction = null;

  private String[] dclick_choices = {
          "Update the double-clicked auction",
          "Open the chosen auction in the browser",
          "Initiate a snipe on the given auction",
          "Place a bid on the chosen auction",
          "Show information about the double-clicked auction",
          "Make a comment on the auction",
          "Copy useful information about the selected auction"
  };

  private String[] dclick_options = {
          "Update",
          "Browse",
          "Snipe",
          "Bid",
          "Information",
          "Comment",
          "Copy"
  };

  public String getTabName() {
    return "General";
  }

  public void cancel() {}

  public void apply() {
    boolean firstrun = JConfig.queryConfiguration("config.firstrun", "false").equals("true");

    if(!Platform.usingSparkle()) JConfig.setConfiguration("updates.enabled", doVersionUpdateCheckBox.isSelected() ? "true" : "false");
    JConfig.setConfiguration("debugging", debuggingBox.isSelected() ? "true" : "false");
    JConfig.setConfiguration("logging", loggingBox.isSelected() ? "true" : "false");
    if(!loggingBox.isSelected()) {
      JConfig.log().closeLog();
    }
    JConfig.setConfiguration("deleted.ignore", ignoreDeletedBox.isSelected() ? "true" : "false");

    int metricsChoice = allowMetricsReporting.getSelectedIndex();
    if (metricsChoice != -1) {
      JConfig.setConfiguration("metrics.optin", metricsValues[metricsChoice]);
    }

    int clickAction = dclickAction.getSelectedIndex();
    if (clickAction != -1) {
      JConfig.setConfiguration("doubleclick.action", dclick_options[clickAction]);
    }

    if(!Platform.usingSparkle()) JConfig.setConfiguration("updates.allowConfig", allowConfigUpdateBox.isSelected() ? "true" : "false");
    JConfig.setConfiguration("store.auctionHTML", allowArchival.isSelected() ? "true" : "false");

    String restartMessage = null;

    String old_timeSyncBox = JConfig.queryConfiguration("timesync.enabled", "true");
    JConfig.setConfiguration("timesync.enabled", timeSyncBox.isSelected() ? "true" : "false");
    if(!firstrun && !old_timeSyncBox.equals(JConfig.queryConfiguration("timesync.enabled", "true")))
      restartMessage = "You have to close and restart JBidwatcher for the Time Synchronisation\n" +
                       "change to be recognized, as it requires resetting the user interface.";

    JConfig.setConfiguration("display.thumbnail", disableThumbnailBox.isSelected() ? "true" : "false");

    if(Platform.isMac()) {
      if(macMetalBox != null) {
        String old_val = JConfig.queryConfiguration("mac.useMetal", "true");
        JConfig.setConfiguration("mac.useMetal", macMetalBox.isSelected() ? "true":"false");
        String new_val = JConfig.queryConfiguration("mac.useMetal", "true");
        if(!firstrun && !old_val.equals(new_val)) {
          restartMessage = "You have to close and restart JBidwatcher for the Brushed\n" +
                           "Metal change to be recognized, as it requires resetting\n" +
                           "the user interface.";
        }
      }
    }

    if(Platform.supportsTray()) {
      String oldCfg = JConfig.queryConfiguration("windows.tray");
      JConfig.setConfiguration("windows.tray", winTrayBox.isSelected()?"true":"false");
      Platform.setTrayEnabled(winTrayBox.isSelected());
      JConfig.setConfiguration("windows.minimize", minimizeTrayBox.isSelected()?"true":"false");
      if(oldCfg == null || !oldCfg.equals(JConfig.queryConfiguration("windows.tray"))) {
        MQFactory.getConcrete("tray").enqueue("TRAY " + (winTrayBox.isSelected()?"on":"off"));
      }
    }

    if(restartMessage != null) {
      JOptionPane.showMessageDialog(null, restartMessage, "Shut down and restart JBidwatcher", JOptionPane.PLAIN_MESSAGE);
    }

    if(firstrun) {
      JConfig.setConfiguration("config.firstrun", "false");
    }

  }

  public void updateValues() {
    String doUpdates = JConfig.queryConfiguration("updates.enabled", "true");
    String doDebugging = JConfig.queryConfiguration("debugging", "false");
    String doLogging = JConfig.queryConfiguration("logging", "true");
    String doIgnoreDeleted = JConfig.queryConfiguration("deleted.ignore", "true");
    String doAllowConfigUpdates = JConfig.queryConfiguration("updates.allowConfig", "true");
    String doMacMetal = JConfig.queryConfiguration("mac.useMetal", "true");
    String doWinTray = JConfig.queryConfiguration("windows.tray", "true");
    String doMinimize= JConfig.queryConfiguration("windows.minimize", "true");
    String doArchival = JConfig.queryConfiguration("store.auctionHTML", "true");
    String doTimeSync = JConfig.queryConfiguration("timesync.enabled", "true");
    String doDisableThumbnails = JConfig.queryConfiguration("display.thumbnail", "true");

    if(!Platform.usingSparkle()) doVersionUpdateCheckBox.setSelected(doUpdates.equals("true"));
    debuggingBox.setSelected(doDebugging.equals("true"));
    loggingBox.setSelected(doLogging.equals("true"));
    ignoreDeletedBox.setSelected(doIgnoreDeleted.equals("true"));
    if (!Platform.usingSparkle()) allowConfigUpdateBox.setSelected(doAllowConfigUpdates.equals("true"));
    allowArchival.setSelected(doArchival.equals("true"));
    timeSyncBox.setSelected(doTimeSync.equals("true"));
    disableThumbnailBox.setSelected(doDisableThumbnails.equals("true"));
    if(Platform.supportsTray() && winTrayBox != null) {
      winTrayBox.setSelected(doWinTray.equals("true"));
      minimizeTrayBox.setEnabled(doWinTray.equals("true"));
      minimizeTrayBox.setSelected(doMinimize.equals("true"));
    }
    if(Platform.isMac() && macMetalBox != null) {
      macMetalBox.setSelected(doMacMetal.equals("true"));
    }
  }

  private JPanel buildCheckboxPanel() {
    JPanel tp = new JPanel();

    tp.setBorder(BorderFactory.createTitledBorder("General Options"));
    tp.setLayout(new GridLayout(0, 2));

    if(!Platform.usingSparkle()) {
      doVersionUpdateCheckBox = new JCheckBox("Regularly check for new versions");
      doVersionUpdateCheckBox.setToolTipText("Once a day check for updates, config changes or notices necessary to keep JBidwatcher running smoothly.");
      tp.add(doVersionUpdateCheckBox);

      allowConfigUpdateBox = new JCheckBox("Allow live configuration updates");
      allowConfigUpdateBox.setToolTipText("<html><body>Some eBay changes may be fixable with simple updates to configuration values,<br>" +
          "or search strings.  This option allows JBidwatcher to look for those updates<br>" +
          "during the new version check.  This only works if 'Regularly check for new versions'<br>" +
          "is enabled.  This is <b>strongly</b> recommended.</body></html>");
      tp.add(allowConfigUpdateBox);
      //  allowConfigUpdate relies on doVersionUpdate.
      doVersionUpdateCheckBox.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          allowConfigUpdateBox.setEnabled(doVersionUpdateCheckBox.isSelected());
        }
      });
    }

    debuggingBox = new JCheckBox("Debugging");
    debuggingBox.setToolTipText("Enable tracking a lot more information about the state of the program as it's working.");
    tp.add(debuggingBox);

    loggingBox = new JCheckBox("Logging");
    loggingBox.setToolTipText("Enable logging to error[.###].log files in the JBidwatcher home directory.");
    tp.add(loggingBox);

    ignoreDeletedBox = new JCheckBox("Ignore deleted items in search results");
    ignoreDeletedBox.setToolTipText("<html><body>Ignore previously deleted items when loading in the results from a search.<br>This prevents having to delete the same items repeatedly, every time a search executes.<br>STRONGLY recommended.</body></html>");
    tp.add(ignoreDeletedBox);

    allowArchival = new JCheckBox("Allow archival storage of auctions");
    allowArchival.setToolTipText("JBidwatcher will save auctions in compressed format, readable even after eBay has removed the item.");
    tp.add(allowArchival);

    timeSyncBox = new JCheckBox("Time Synchronisation");
    timeSyncBox.setToolTipText("Enable/disable time synchronisation");
    tp.add(timeSyncBox);

    disableThumbnailBox = new JCheckBox("Display Thumbnails");
    disableThumbnailBox.setToolTipText("Display thumbnails when the cursor hovers over an item.");
    tp.add(disableThumbnailBox);

    if(Platform.isMac()) {
      macMetalBox = new JCheckBox("Use Brushed Metal UI");
      macMetalBox.setToolTipText("Turn on / off brushed metal look under MacOSX.");
      tp.add(macMetalBox);
    }

    if(Platform.supportsTray()) {
      winTrayBox = new JCheckBox("Use System Tray");
      winTrayBox.setToolTipText("Allow JBidwatcher to put an icon in the system tray with some statistics on hover and some messages will use balloon-style popups.");
      tp.add(winTrayBox);
      minimizeTrayBox = new JCheckBox("Minimize to System Tray");
      minimizeTrayBox.setToolTipText("If checked, minimizing the window will send JBidwatcher to the tray instead of the task bar.");
      winTrayBox.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          minimizeTrayBox.setEnabled(winTrayBox.isSelected());
        }
      });
      tp.add(minimizeTrayBox);
    }

    if(tp.getComponentCount() % 2 == 1) {
      //  Add a placeholder to make the configuration wrap.
      tp.add(new JLabel(""));
    }

    String curMetricsChoice = JConfig.queryConfiguration("metrics.optin", "ask");
    int curIndex = Arrays.asList(metricsValues).indexOf(curMetricsChoice);
    allowMetricsReporting = new JComboBox(metricsChoices);
    allowMetricsReporting.setSelectedIndex(curIndex);
    JLabel metricsLabel = new JLabel("Report anonymized usage");
    String metricsTooltip = "Reports anonymized usage statistics to me, to be used for improving the software.  Usage data is for statistical purposes only.";
    metricsLabel.setToolTipText(metricsTooltip);
//    tp.add(metricsLabel);
    allowMetricsReporting.setToolTipText(metricsTooltip);
//    tp.add(allowMetricsReporting);
    tp.add(makeLine(metricsLabel, allowMetricsReporting));

    updateValues();
    return (tp);
  }

  private JPanel buildDropdownPanel() {
    JPanel tp = new JPanel();

    tp.setBorder(BorderFactory.createTitledBorder("Doubleclick setting"));
    tp.setLayout(new BorderLayout());
    //tp.setLayout(new GridLayout(1, 2));

    String curClickAction = JConfig.queryConfiguration("doubleclick.action", "Update");

    dclickAction = new JComboBox(dclick_choices);
    for (int i = 0; i < dclick_options.length; i++) {
      if (curClickAction.equals(dclick_options[i])) {
        dclickAction.setSelectedIndex(i);
      }
    }

    tp.add(makeLine(new JLabel("Action: "), dclickAction), BorderLayout.NORTH);

    return tp;
  }

  public JConfigGeneralTab() {
    super.setLayout(new BorderLayout());
    JPanel jp = new JPanel();
    jp.setLayout(new BorderLayout());
    jp.add(panelPack(buildCheckboxPanel()), BorderLayout.NORTH);
    jp.add(panelPack(buildDropdownPanel()), BorderLayout.CENTER);
    super.add(jp, BorderLayout.NORTH);
  }
}
