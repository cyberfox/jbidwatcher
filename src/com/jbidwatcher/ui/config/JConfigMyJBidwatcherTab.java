package com.jbidwatcher.ui.config;
/*
 * Copyright (c) 2000-2007, CyberFOX Software, Inc. All Rights Reserved.
 *
 * Developed by mrs (Morgan Schweers)
 */

import com.jbidwatcher.ui.util.JPasteListener;
import com.jbidwatcher.ui.util.SpringUtilities;
import com.jbidwatcher.util.config.JConfig;
import com.jbidwatcher.my.MyJBidwatcher;

import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;
import java.util.Map;
import java.util.HashMap;
import javax.swing.*;

public class JConfigMyJBidwatcherTab extends JConfigTab {
  private JCheckBox mEnable;
  private JTextField mEmail;
  private JTextField mPassword;
  private JButton mCreateOrUpdate;
  private Map<JCheckBox,String> mConfigurationMap = new HashMap<JCheckBox,String>();
  private Map<JCheckBox,String> mEnabledMap = new HashMap<JCheckBox,String>();
  private JLabel mListingStats;
  private JLabel mCategoryStats;
  private JLabel mSSLEnabled;

  public String getTabName() { return "My JBidwatcher"; }
  public void cancel() { }

  public boolean apply() {
    String email = mEmail.getText();
    String password = mPassword.getText();

    if(email != null && password != null) {
      if(MyJBidwatcher.getInstance().getAccountInfo()) {
        JConfig.setConfiguration("my.jbidwatcher.id", email);
        JConfig.setConfiguration("my.jbidwatcher.key", password);
      }
    }

    if(JConfig.queryConfiguration("my.jbidwatcher.allow.sync") != null)
      for(JCheckBox cb : mConfigurationMap.keySet()) {
        String cfg = mConfigurationMap.get(cb);
        JConfig.setConfiguration(cfg, Boolean.toString(cb.isSelected()));
      }
    return true;
  }

  public void updateValues() {
    String email = JConfig.queryConfiguration("my.jbidwatcher.id", "");
    String pass = JConfig.queryConfiguration("my.jbidwatcher.key", "");

    mEnable.setSelected(JConfig.queryConfiguration("my.jbidwatcher.enabled", "false").equals("true"));

    for (JCheckBox cb : mEnabledMap.keySet()) {
      String cfg = mEnabledMap.get(cb);
      cb.setEnabled(mEnable.isSelected() && JConfig.queryConfiguration(cfg, "false").equals("true"));
    }

    mEmail.setText(email);
    mPassword.setText(pass);

    mListingStats.setText(left("listings"));
    mCategoryStats.setText(left("categories"));
    mSSLEnabled.setText(sslState());

    for(ActionListener al : mEnable.getActionListeners()) {
      al.actionPerformed(new ActionEvent(mEnable, ActionEvent.ACTION_PERFORMED, "Redraw"));
    }
  }

  private void setComponentTooltip(JComponent comp, String text) {
    comp.setToolTipText(text);
    comp.getAccessibleContext().setAccessibleDescription(text);
  }

  private static final ImageIcon successIcon = new ImageIcon(JConfig.getResource("/icons/status_green_16.png"));
  private static final ImageIcon failIcon = new ImageIcon(JConfig.getResource("/icons/status_red_16.png"));

  private JPanel buildUserSettings() {
    JPanel jp = new JPanel(new BorderLayout());
    jp.setBorder(BorderFactory.createTitledBorder("My JBidwatcher User Settings"));

    JPanel innerPanel = new JPanel();
    innerPanel.setLayout(new SpringLayout());

    mEmail = new JTextField();
    mEmail.addMouseListener(JPasteListener.getInstance());
    setComponentTooltip(mEmail, "Email address to use for your My JBidwatcher account.");
    final JLabel emailLabel = new JLabel("Email Address:");
    emailLabel.setLabelFor(mEmail);
    innerPanel.add(emailLabel);
    innerPanel.add(mEmail);

    mPassword = new JTextField();
    mPassword.addMouseListener(JPasteListener.getInstance());
    setComponentTooltip(mPassword, "My JBidwatcher access key");
    final JLabel passwordLabel = new JLabel("Access Key:");
    passwordLabel.setLabelFor(mPassword);
    innerPanel.add(passwordLabel);
    innerPanel.add(mPassword);

    Box button = Box.createHorizontalBox();
    final JLabel statusLabel = new JLabel("");
    mCreateOrUpdate = new JButton("Test Access");
    mCreateOrUpdate.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent event) {
        String action = event.getActionCommand();
        if(action == null) return;

        String oldId = JConfig.queryConfiguration("my.jbidwatcher.id");
        String oldKey= JConfig.queryConfiguration("my.jbidwatcher.key");

        JConfig.setConfiguration("my.jbidwatcher.id", mEmail.getText());
        JConfig.setConfiguration("my.jbidwatcher.key", mPassword.getText());

        if(MyJBidwatcher.getInstance().getAccountInfo()) {
          statusLabel.setIcon(successIcon);
          statusLabel.setText("success!");
          if(JConfig.queryConfiguration("my.jbidwatcher.sync") == null) JConfig.setConfiguration("my.jbidwatcher.sync", "true");
          updateValues();
        } else {
          statusLabel.setIcon(failIcon);
          statusLabel.setText("failed.");
        }

        JConfig.setConfiguration("my.jbidwatcher.id", oldId);
        JConfig.setConfiguration("my.jbidwatcher.key", oldKey);
      }
    });
    button.add(mCreateOrUpdate);
    button.add(statusLabel);
    innerPanel.add(new JLabel(""));
    innerPanel.add(button);

    SpringUtilities.makeCompactGrid(innerPanel, 3, 2, 6, 6, 6, 1);

    mEnable = new JCheckBox();
    mEnable.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent event) {
        boolean selected = mEnable.isSelected();

        emailLabel.setEnabled(selected);
        mEmail.setEnabled(selected);
        mEmail.setEditable(selected);

        passwordLabel.setEnabled(selected);
        mPassword.setEnabled(selected);
        mPassword.setEditable(selected);

        mCreateOrUpdate.setEnabled(selected);
        for(JCheckBox cb : mEnabledMap.keySet()) {
          cb.setEnabled(selected && JConfig.queryConfiguration(mEnabledMap.get(cb), "false").equals("true"));
        }
      }
    });
    JLabel enableLabel = new JLabel("Enable My JBidwatcher");
    enableLabel.setLabelFor(mEnable);
    mConfigurationMap.put(mEnable, "my.jbidwatcher.enabled");

    jp.add(makeLine(mEnable, enableLabel), BorderLayout.NORTH);
    jp.add(innerPanel, BorderLayout.CENTER);

    return(jp);
  }

  public JConfigMyJBidwatcherTab() {
    super();
    this.setLayout(new BorderLayout());
    JPanel jp = new JPanel();
    jp.setLayout(new BorderLayout());
    jp.add(buildUserSettings(), BorderLayout.NORTH);
    jp.add(buildExtraSettings(), BorderLayout.SOUTH);
    this.add(panelPack(jp), BorderLayout.NORTH);
    updateValues();
  }

  private JPanel buildExtraSettings() {
    JPanel jp = new JPanel(new BorderLayout());
    jp.setBorder(BorderFactory.createTitledBorder("My JBidwatcher Settings"));

    JPanel innerPanel = new JPanel();
    innerPanel.setLayout(new SpringLayout());

    innerPanel.add(createSettingsCheckbox("Upload item info to My JBidwatcher", "sync"));
    innerPanel.add(createSettingsCheckbox("Upload item HTML", "uploadhtml"));
//    innerPanel.add(createSettingsCheckbox("Upload snipes to Gixen", "gixen"));
    innerPanel.add(createSettingsCheckbox("Allow setting snipes in My JBidwatcher", "snipes"));
    innerPanel.add(createSettingsCheckbox("Use My JBidwatcher as a fallback parser", "parser"));

    mListingStats = new JLabel(left("listings"));
    mListingStats.setFont(mListingStats.getFont().deriveFont(Font.BOLD));
    mCategoryStats = new JLabel(left("categories"));
    mCategoryStats.setFont(mCategoryStats.getFont().deriveFont(Font.BOLD));

    innerPanel.add(mListingStats);
    innerPanel.add(mCategoryStats);
    innerPanel.add(new JLabel(""));
    mSSLEnabled = new JLabel();
    mSSLEnabled.setText(sslState());
    mSSLEnabled.setFont(mSSLEnabled.getFont().deriveFont(Font.BOLD | Font.ITALIC));
    JPanel tmp = new JPanel(new BorderLayout());
    tmp.add(mSSLEnabled, BorderLayout.EAST);
    innerPanel.add(tmp);
    SpringUtilities.makeCompactGrid(innerPanel, 4, 2, 6, 6, 6, 1);

    jp.add(innerPanel, BorderLayout.CENTER);
    return jp;
  }

  private String sslState() {return JConfig.queryConfiguration("my.jbidwatcher.allow.ssl", "false").equals("true") ? "SSL Enabled" : "SSL Disabled";}

  private JCheckBox createSettingsCheckbox(String text, String identifier) {
    String cfgAllowed = "my.jbidwatcher.allow." + identifier;
    String cfgSetting = "my.jbidwatcher." + identifier;
    final JCheckBox cb = new JCheckBox(text);
    cb.setEnabled(JConfig.queryConfiguration(cfgAllowed, "false").equals("true"));
    mConfigurationMap.put(cb, cfgSetting);
    mEnabledMap.put(cb, cfgAllowed);
    return cb;
  }

  private String left(String type) {
    String listings = JConfig.queryConfiguration("my.jbidwatcher.allow." + type, "unknown");
    if(listings.equals("-1")) listings = "unlimited";
    return  listings + " " + type + " remaining";
  }
}
