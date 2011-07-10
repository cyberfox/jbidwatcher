package com.jbidwatcher.ui.config;
/*
 * Copyright (c) 2000-2007, CyberFOX Software, Inc. All Rights Reserved.
 *
 * Developed by mrs (Morgan Schweers)
 */

import com.jbidwatcher.util.config.JConfig;

import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import javax.swing.event.*;

public class JConfigFirewallTab extends JConfigTab {
  private JRadioButton noFirewall;
  private JRadioButton socksFirewall;
  private JRadioButton webProxy;
  private JCheckBox proxyHttps;

  String holdProxyHost=null, holdProxyPort=null;
  String holdHTTPSProxyHost=null, holdHTTPSProxyPort=null;
  String holdProxyUser=null, holdProxyPass=null;
  String holdFireHost=null, holdFirePort=null;

  private JTextField proxyHost, proxyPort;
  private JTextField httpsProxyHost, httpsProxyPort;
  private JTextField proxyUser;
  private JPasswordField proxyPass;
  private JTextField firewallHost, firewallPort;
  private DocumentListener firewallTextFieldListener;

  private class radioAction implements ActionListener {
    public void actionPerformed(ActionEvent ae) {
      boolean proxyP=false, firewallP=false;

      if(noFirewall.isSelected()) {
        firewallP = false;
        proxyP = false;
      } else if(socksFirewall.isSelected()) {
        firewallP = true;
        proxyP = false;
      } else if(webProxy.isSelected()) {
        firewallP = false;
        proxyP = true;
      }

      updateProxyFirewall(proxyP, firewallP, proxyHttps.isSelected());
    }
  }

  private class textAction implements DocumentListener {
    private void anyEvent() {
      if(proxyHost.isEnabled()) {
        holdProxyHost = proxyHost.getText();
      }
      if(proxyPort.isEnabled()) {
        holdProxyPort = proxyPort.getText();
      }
      if(proxyUser.isEnabled()) {
        holdProxyUser = proxyUser.getText();
      }
      if(proxyPass.isEnabled()) {
        holdProxyPass = new String(proxyPass.getPassword());
      }
      if(firewallHost.isEnabled()) {
        holdFireHost = firewallHost.getText();
      }
      if(firewallPort.isEnabled()) {
        holdFirePort = firewallPort.getText();
      }
      if(httpsProxyHost.isEnabled()) {
        holdHTTPSProxyHost = httpsProxyHost.getText();
      }
      if(httpsProxyPort.isEnabled()) {
        holdHTTPSProxyPort = httpsProxyPort.getText();
      }
    }
    public void insertUpdate(DocumentEvent de) {
      anyEvent();
    }
    public void changedUpdate(DocumentEvent de) {
      anyEvent();
    }
    public void removeUpdate(DocumentEvent de) {
      anyEvent();
    }
  }

  public JConfigFirewallTab() {
    super();
    JPanel topPanes = new JPanel();
    JPanel bottomPanes = new JPanel();

    firewallTextFieldListener = new textAction();

    this.setLayout(new BorderLayout());

    topPanes.setLayout(new GridLayout(1,2));
    topPanes.add(panelPack(buildRadioButtons()));
    topPanes.add(panelPack(buildFirewallPrompt()));
    this.add(topPanes, "North");

    bottomPanes.setLayout(new GridLayout(1, 2));
    bottomPanes.add(panelPack(buildProxyPanel()));
    bottomPanes.add(panelPack(buildHTTPSProxyPanel()));
    //this.add(panelPack(buildProxyPanel()), "Center");
    this.add(bottomPanes, "Center");

    updateValues();
  }

  //  This is how the main configuration menu knows what to name this
  //  tab.
  public String getTabName() { return "Firewall"; }

  //
  //  Cancel all modifications.
  //  Reverts to stored configuration values.
  //
  public void cancel() {
    holdProxyHost = holdProxyPort = holdProxyUser = holdProxyPass =
      holdFireHost = holdFirePort = holdHTTPSProxyHost = holdHTTPSProxyPort = null;
    updateValues();
  }

  //
  //  Apply all changes made to the firewall options.  This does NOT
  //  immediately open a SOCKS server, or a web proxy for all future
  //  transactions.  It should.  HACKHACK -- mrs: 14-August-2001 01:44
  //
  public void apply() {
    String firewallState = "none";

    if(noFirewall.isSelected()) {
      firewallState = "none";
    } else if(socksFirewall.isSelected()) {
      firewallState = "firewall";
    } else if(webProxy.isSelected()) {
      firewallState = "proxy";
    }

    JConfig.setConfiguration("proxyfirewall", firewallState);
    if(holdProxyHost != null) {
      JConfig.setConfiguration("proxy.host", holdProxyHost);
    }
    if(holdProxyPort != null) {
      JConfig.setConfiguration("proxy.port", holdProxyPort);
    }
    if(holdProxyUser != null) {
      JConfig.setConfiguration("proxy.user", holdProxyUser);
    }
    if(holdProxyPass != null) {
      JConfig.setConfiguration("proxy.pass", holdProxyPass);
    }
    if(holdFireHost != null) {
      JConfig.setConfiguration("firewall.host", holdFireHost);
    }
    if(holdFirePort != null) {
      JConfig.setConfiguration("firewall.port", holdFirePort);
    }
    if(proxyHttps.isSelected()) {
      JConfig.setConfiguration("proxy.https.set", "true");
      if(holdHTTPSProxyHost != null) {
        JConfig.setConfiguration("proxy.https.host", holdHTTPSProxyHost);
      }
      if(holdHTTPSProxyPort != null) {
        JConfig.setConfiguration("proxy.https.port", holdHTTPSProxyPort);
      }
    } else {
      JConfig.setConfiguration("proxy.https.set", "false");
    }
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

    if(jrb.isSelected()) {
      if(defaultValue == null) {
        outputValue = JConfig.queryConfiguration(configValue);
        if(outputValue == null) {
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

  private void setAllProxyText() {
    proxyHost.setText(getConfigValue(webProxy, "proxy.host", holdProxyHost));
    proxyPort.setText(getConfigValue(webProxy, "proxy.port", holdProxyPort));
    proxyUser.setText(getConfigValue(webProxy, "proxy.user", holdProxyUser));
    proxyPass.setText(getConfigValue(webProxy, "proxy.pass", holdProxyPass));
  }

  private void setAllProxyStatus(boolean proxyP) {
    proxyHost.setEnabled(proxyP);
    proxyPort.setEnabled(proxyP);
    proxyUser.setEnabled(proxyP);
    proxyPass.setEnabled(proxyP);
  }

  private void setAllFirewallText() {
    firewallHost.setText(getConfigValue(socksFirewall, "firewall.host", holdFireHost));
    firewallPort.setText(getConfigValue(socksFirewall, "firewall.port", holdFirePort));
  }

  private void setAllFirewallStatus(boolean firewallP) {
    firewallHost.setEnabled(firewallP);
    firewallPort.setEnabled(firewallP);
  }

  private void setAllHTTPSText() {
    httpsProxyHost.setText(getConfigValue(proxyHttps, "proxy.https.host", holdHTTPSProxyHost));
    httpsProxyPort.setText(getConfigValue(proxyHttps, "proxy.https.port", holdHTTPSProxyPort));
  }

  private void setAllHTTPSStatus(boolean httpsP) {
    httpsProxyHost.setEnabled(httpsP);
    httpsProxyPort.setEnabled(httpsP);
  }

  private void updateProxyFirewall(boolean proxyP, boolean firewallP, boolean httpsP) {
    if(!proxyP) {
      setAllProxyStatus(proxyP);
      setAllProxyText();
    } else {
      setAllProxyText();
      setAllProxyStatus(proxyP);
    }

    if(!firewallP) {
      setAllFirewallStatus(firewallP);
      setAllFirewallText();
    } else {
      setAllFirewallText();
      setAllFirewallStatus(firewallP);
    }

    if(!httpsP) {
      setAllHTTPSStatus(httpsP);
      setAllHTTPSText();
    } else {
      setAllHTTPSText();
      setAllHTTPSStatus(httpsP);
    }
  }

  public final void updateValues() {
    String proxyFirewall = JConfig.queryConfiguration("proxyfirewall");
    boolean proxyP, firewallP;

    if(proxyFirewall == null) {
      noFirewall.setSelected(true);
      firewallP = false;
      proxyP = false;
    } else if(proxyFirewall.equals("proxy")) {
      webProxy.setSelected(true);
      firewallP = false;
      proxyP = true;
    } else if(proxyFirewall.equals("firewall")) {
      socksFirewall.setSelected(true);
      firewallP = true;
      proxyP = false;
    } else {
      //  HACKHACK --  Should make a note that it's an invalid value.
      noFirewall.setSelected(true);
      firewallP = false;
      proxyP = false;
    }

    if(JConfig.queryConfiguration("proxy.https.set", "false").equals("true")) {
      proxyHttps.setSelected(true);
    }

    updateProxyFirewall(proxyP, firewallP, proxyHttps.isSelected());
  }

  private JPanel buildRadioButtons() {
    ActionListener rad = new radioAction();
    JPanel buttonPanel = new JPanel();
    Box buttonBox = Box.createVerticalBox();

    noFirewall = new JRadioButton("No firewall or proxy");
    socksFirewall = new JRadioButton("SOCKS 4/5 Firewall");
    webProxy = new JRadioButton("HTTP Web Proxy");

    ButtonGroup allButtons = new ButtonGroup();
    allButtons.add(socksFirewall);
    allButtons.add(webProxy);
    allButtons.add(noFirewall);
    socksFirewall.addActionListener(rad);
    webProxy.addActionListener(rad);
    noFirewall.addActionListener(rad);

    buttonPanel.setBorder(BorderFactory.createTitledBorder("Firewall/Proxy"));
    buttonPanel.setLayout(new BorderLayout());

    buttonBox.add(socksFirewall);
    buttonBox.add(webProxy);
    buttonBox.add(noFirewall);

    buttonPanel.add(buttonBox, "North");

    return buttonPanel;
  }

  private JPanel buildFirewallPrompt() {
    JPanel firewallPanel = new JPanel();
    Box updownBox;

    firewallPanel.setBorder(BorderFactory.createTitledBorder("SOCKS Settings"));
    firewallPanel.setLayout(new BoxLayout(firewallPanel, BoxLayout.Y_AXIS));

    firewallHost = new JTextField();
    firewallPort = new JTextField();

    setAllFirewallStatus(false);

    adjustField(firewallHost, "Host name or IP address of SOCKS firewall", firewallTextFieldListener);
    adjustField(firewallPort, "Port number for SOCKS firewall", firewallTextFieldListener);

    updownBox = Box.createVerticalBox();
    updownBox.add(makeLine(new JLabel("SOCKS Host: "), firewallHost));
    updownBox.add(makeLine(new JLabel("SOCKS Port:  "), firewallPort));

    firewallPanel.add(updownBox);

    return firewallPanel;
  }

  private JPanel buildProxyPanel() {
    JPanel proxyPanel = new JPanel();

    proxyPanel.setBorder(BorderFactory.createTitledBorder("HTTP/Web Proxy Settings"));
    proxyPanel.setLayout(new BoxLayout(proxyPanel, BoxLayout.Y_AXIS));

    proxyHost = new JTextField();
    proxyPort = new JTextField();
    proxyUser = new JTextField();
    proxyPass = new JPasswordField();

    setAllProxyStatus(false);
    adjustField(proxyHost, "Host name or IP address of web proxy server", firewallTextFieldListener);
    adjustField(proxyPort, "Port number that a web proxy server runs on", firewallTextFieldListener);
    adjustField(proxyUser, "Username (if needed) for web proxy server", firewallTextFieldListener);
    adjustField(proxyPass, "Password (if needed) for web proxy server", firewallTextFieldListener);

    proxyPanel.add(makeLine(new JLabel("Host: "), proxyHost));
    proxyPanel.add(makeLine(new JLabel("Port:  "), proxyPort));
    proxyPanel.add(makeLine(new JLabel("Username: "), proxyUser));
    proxyPanel.add(makeLine(new JLabel("Password:  "), proxyPass));

    return proxyPanel;
  }

  private JPanel buildHTTPSProxyPanel() {
    JPanel proxyPanel = new JPanel();
    radioAction rad = new radioAction();

    proxyPanel.setBorder(BorderFactory.createTitledBorder("HTTPS/Secure Proxy Settings"));
    proxyPanel.setLayout(new BoxLayout(proxyPanel, BoxLayout.Y_AXIS));

    httpsProxyHost = new JTextField();
    httpsProxyPort = new JTextField();
    setAllHTTPSStatus(false);
    adjustField(httpsProxyHost, "Host name or IP address of HTTPS proxy server", firewallTextFieldListener);
    adjustField(httpsProxyPort, "Port number that the HTTPS proxy server runs on", firewallTextFieldListener);

    proxyHttps = new JCheckBox("Enable HTTPS (secure http) proxy?");
    proxyHttps.addActionListener(rad);
    JPanel checkboxPanel = new JPanel(new BorderLayout());
    checkboxPanel.add(proxyHttps, BorderLayout.WEST);
    proxyPanel.add(checkboxPanel);

    proxyPanel.add(makeLine(new JLabel("HTTPS Host: "), httpsProxyHost));
    proxyPanel.add(makeLine(new JLabel("HTTPS Port: "), httpsProxyPort));

    return proxyPanel;
  }
}
