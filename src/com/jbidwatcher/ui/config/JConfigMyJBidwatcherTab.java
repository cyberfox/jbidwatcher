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
import javax.swing.*;

public class JConfigMyJBidwatcherTab extends JConfigTab {
  private JCheckBox mEnable;
  private JTextField mEmail;
  private JTextField mPassword;
  private JButton mCreateOrUpdate;

  public String getTabName() { return "My JBidwatcher"; }
  public void cancel() { }

  public boolean apply() {
    boolean enabled = mEnable.isSelected();
    JConfig.setConfiguration("my.jbidwatcher.enabled", Boolean.toString(enabled));

    String email = mEmail.getText();
    String password = mPassword.getText();

    if(email != null && password != null) {
      if(MyJBidwatcher.getInstance().getAccountInfo()) {
        JConfig.setConfiguration("my.jbidwatcher.id", email);
        JConfig.setConfiguration("my.jbidwatcher.key", password);
      }
    }

    return true;
  }

  public void updateValues() {
    String email = JConfig.queryConfiguration("my.jbidwatcher.id", "");
    String pass = JConfig.queryConfiguration("my.jbidwatcher.key", "");
    boolean enabled = JConfig.queryConfiguration("my.jbidwatcher.enabled", "false").equals("true");
//    String id = JConfig.queryConfiguration("my.jbidwatcher.id");

    mEmail.setText(email);
    mPassword.setText(pass);
    mEnable.setSelected(enabled);

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
      }
    });
    JLabel enableLabel = new JLabel("Enable My JBidwatcher");
    enableLabel.setLabelFor(mEnable);

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
//    jp.add(buildExtraSettings(), BorderLayout.SOUTH);
    this.add(panelPack(jp), BorderLayout.NORTH);
    updateValues();
  }
}
