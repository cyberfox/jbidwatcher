package com.jbidwatcher.ui.config;

import com.jbidwatcher.util.config.JConfig;

import javax.swing.*;
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;
import java.awt.BorderLayout;
import java.awt.GridLayout;

/*
 * Copyright (c) 2000-2007, CyberFOX Software, Inc. All Rights Reserved.
 *
 * Developed by mrs (Morgan Schweers)
 * Date: Apr 10, 2009
 * Time: 2:43:57 AM
 */

public class JConfigDatabaseTab extends JConfigTab {
  private JRadioButton defaultDerbyDB;
  private JRadioButton mysqlDB;

  private JTextField mysqlHost, mysqlPort;
  private JTextField mysqlDatabase;
  private JTextField mysqlUser;
  private JPasswordField mysqlPassword;

  private class radioAction implements ActionListener {
    public void actionPerformed(ActionEvent ae) {
      updateValues();
    }
  }

  public JConfigDatabaseTab() {
    super();
    JPanel topPanes = new JPanel();
    JPanel bottomPanes = new JPanel();

    this.setLayout(new BorderLayout());

    topPanes.setLayout(new GridLayout(1, 2));
    topPanes.add(panelPack(buildRadioButtons()));
    this.add(topPanes, "North");

    bottomPanes.setLayout(new GridLayout(1, 2));
    bottomPanes.add(panelPack(buildMySQLPanel()));
    this.add(bottomPanes, "Center");

    updateValues();
  }

  //  This is how the main configuration menu knows what to name this
  //  tab.
  public String getTabName() { return "Database"; }

  //
  //  Cancel all modifications.
  //  Reverts to stored configuration values.
  //
  public void cancel() {
    updateValues();
  }

  //
  //  Apply all changes made to the firewall options.  This does NOT
  //  immediately open a SOCKS server, or a web proxy for all future
  //  transactions.  It should.  HACKHACK -- mrs: 14-August-2001 01:44
  //
  public boolean apply() {
    if (defaultDerbyDB.isSelected()) {
      JConfig.kill("db.protocol");
      JConfig.kill("db.driver");
      JConfig.kill("db.user");
      JConfig.kill("db.pass");
    } else if (mysqlDB.isSelected()) {
      String host = mysqlHost.getText();
      JConfig.setConfiguration("db.mysql.host", host);
      String port = mysqlPort.getText();
      JConfig.setConfiguration("db.mysql.port", port);
      Integer portNum;
      try { portNum = Integer.parseInt(port); } catch(Exception e) { portNum = -1; }
      String db = mysqlDatabase.getText();
      JConfig.setConfiguration("db.mysql.database", db);
      String connectURL = "jdbc:mysql://" + host;
      if(portNum != -1) connectURL += ":" + portNum;
      connectURL += "/";

      JConfig.setConfiguration("db.framework", "remote");
      JConfig.setConfiguration("db.protocol", connectURL);
      JConfig.setConfiguration("db.driver", "com.mysql.jdbc.Driver");
      JConfig.setConfiguration("db.user", mysqlUser.getText());
      JConfig.setConfiguration("db.pass", new String(mysqlPassword.getPassword()));
    }

    return true;
  }

  //
  //  If the radio button is selected, return either the default
  //  value, or if that value is null, the correct value from the
  //  configuration file, or if THAT value is also null, an empty
  //  string.
  //
  //  If the radio button is NOT selected, return "<disabled>", so
  //  there's some marker in the text field that it's not editable
  //  right now.
  //
  private String getConfigValue(JToggleButton jrb, String configValue, String defaultValue) {
    String outputValue;

    if (jrb.isSelected()) {
      if (defaultValue == null) {
        outputValue = JConfig.queryConfiguration(configValue);
        if (outputValue == null) {
          return "";
        } else {
          return outputValue;
        }
      }
      return defaultValue;
    } else {
      return "<disabled>";
    }
  }

  public final void updateValues() {
    mysqlHost.setEnabled(mysqlDB.isSelected());
    mysqlHost.setText(getConfigValue(mysqlDB, "db.mysql.host", "localhost"));
    mysqlPort.setEnabled(mysqlDB.isSelected());
    mysqlPort.setText(getConfigValue(mysqlDB, "db.mysql.port", "3306"));
    mysqlDatabase.setEnabled(mysqlDB.isSelected());
    mysqlDatabase.setText(getConfigValue(mysqlDB, "db.mysql.database", "jbidwatcher"));
    mysqlUser.setEnabled(mysqlDB.isSelected());
    mysqlUser.setText(getConfigValue(mysqlDB, "db.user", "root"));
    mysqlPassword.setEnabled(mysqlDB.isSelected());
    mysqlPassword.setText(getConfigValue(mysqlDB, "db.pass", ""));
  }

  private JPanel buildRadioButtons() {
    ActionListener rad = new radioAction();
    JPanel buttonPanel = new JPanel();
    Box buttonBox = Box.createVerticalBox();

    defaultDerbyDB = new JRadioButton("Use default embedded Derby DB");
    mysqlDB =        new JRadioButton("Use remote MySQL database");

    String protocol = JConfig.queryConfiguration("db.protocol");
    if(protocol == null || !protocol.toLowerCase().contains("mysql")) {
      defaultDerbyDB.setSelected(true);
      mysqlDB.setSelected(false);
    } else {
      defaultDerbyDB.setSelected(false);
      mysqlDB.setSelected(true);
    }

    ButtonGroup allButtons = new ButtonGroup();
    allButtons.add(defaultDerbyDB);
    allButtons.add(mysqlDB);
    defaultDerbyDB.addActionListener(rad);
    mysqlDB.addActionListener(rad);

    buttonPanel.setBorder(BorderFactory.createTitledBorder("Database Settings"));
    buttonPanel.setLayout(new BorderLayout());

    buttonBox.add(defaultDerbyDB);
    buttonBox.add(mysqlDB);

    buttonPanel.add(buttonBox, "North");

    return buttonPanel;
  }

  private JPanel buildMySQLPanel() {
    JPanel proxyPanel = new JPanel();

    proxyPanel.setBorder(BorderFactory.createTitledBorder("MySQL Settings"));
    proxyPanel.setLayout(new BoxLayout(proxyPanel, BoxLayout.Y_AXIS));

    mysqlHost = new JTextField();
    mysqlPort = new JTextField();
    mysqlDatabase = new JTextField();
    mysqlUser = new JTextField();
    mysqlPassword = new JPasswordField();

    adjustField(mysqlHost, "Host name or IP address of MySQL server", null);
    adjustField(mysqlPort, "Port number for MySQL server (default: 3306)", null);
    adjustField(mysqlDatabase, "The database on the server to use (default: jbidwatcher)", null);
    adjustField(mysqlUser, "Username (if needed) for MySQL server", null);
    adjustField(mysqlPassword, "Password (if needed) for MySQL server", null);

    proxyPanel.add(makeLine(new JLabel("MySQL Host: "), mysqlHost));
    proxyPanel.add(makeLine(new JLabel("MySQL Port: "), mysqlPort));
    proxyPanel.add(makeLine(new JLabel("Database:   "), mysqlDatabase));
    proxyPanel.add(makeLine(new JLabel("Username:   "), mysqlUser));
    proxyPanel.add(makeLine(new JLabel("Password:   "), mysqlPassword));

    return proxyPanel;
  }
}
