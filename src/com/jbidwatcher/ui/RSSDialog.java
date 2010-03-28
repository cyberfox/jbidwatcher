package com.jbidwatcher.ui;

/*
 * Copyright (c) 2000-2007, CyberFOX Software, Inc. All Rights Reserved.
 *
 * Developed by mrs (Morgan Schweers)
 */

import com.jbidwatcher.ui.config.JConfigTab;
import com.jbidwatcher.ui.util.*;
import com.jbidwatcher.util.config.JConfig;
import com.jbidwatcher.util.browser.BrowserLauncher;
import com.jbidwatcher.util.Constants;
import com.jbidwatcher.auction.Category;

import javax.swing.*;
import java.awt.event.*;
import java.awt.*;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: Morgan Schweers
 * Date: Jun 28, 2005
 * Time: 10:41:37 PM
 *
 */
public class RSSDialog extends BasicDialog {
  private JComboBox tabList = new JComboBox();

  public RSSDialog() {
    setBasicContentPane(new JPanel(new SpringLayout()));
    addBehavior();
    setupUI();

    setTitle("RSS Feed Information");
    setModal(true);
//    }, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
  }

  protected void onOK() {
    dispose();
  }

  protected void onCancel() {
    setVisible(false);
  }

  public void prepare() {
    buildTabList();
  }

  private String getHost() {
    InetAddress thisIp;
    try {
      thisIp = InetAddress.getLocalHost();
    } catch (UnknownHostException e) {
      JConfig.log().handleException("Local host is unknown?!?", e);
      return "127.0.0.1";
    }
    return thisIp.getHostAddress();
  }

  public void fireFeed(String feedName) {
    String url = "feed://" + getHost() + ":" + JConfig.queryConfiguration("server.port", Constants.DEFAULT_SERVER_PORT_STRING) + "/syndicate/" + feedName + ".xml";
    try {
      BrowserLauncher.openURL(url);
    } catch (IOException e) {
      JConfig.log().handleException("Can't browse to: " + url, e);
    }
    onOK();
  }

  public void fireCopy(String feedName) {
    String url = "http://" + getHost() + ":" + JConfig.queryConfiguration("server.port", Constants.DEFAULT_SERVER_PORT_STRING) + "/syndicate/" + feedName + ".xml";
    Clipboard.setClipboardString(url);
    onOK();
  }

  private JPanel buildLinePanel(String label, String actionString) {
    JPanel tmpPanel = new JPanel(new BorderLayout());
    JButton endedButton = new JButton("Copy");
    endedButton.setActionCommand("Copy-" + actionString);
    tmpPanel.add(endedButton, BorderLayout.WEST);
    tmpPanel.add(new JLabel(" " + label), BorderLayout.CENTER);

    ActionListener al = new ActionListener() {
      public void actionPerformed(ActionEvent ae) {
        if(ae.getActionCommand().startsWith("BT-Feed-")) {
          fireFeed(ae.getActionCommand().substring(8));
        } else if(ae.getActionCommand().startsWith("Copy-")) {
          fireCopy(ae.getActionCommand().substring(5));
        }
      }
    };

    endedButton.addActionListener(al);
    JButton feedButton = ButtonMaker.makeButton("icons/feed.gif", "Launch feed:// URL", "Feed-" + actionString, al, true);
    tmpPanel.add(JConfigTab.makeLine(feedButton, new JLabel(" ")), BorderLayout.EAST);
    return tmpPanel;
  }

  private void setupUI() {
    getButtonOK().setText("Close");

    JPanel bottomPanel = new JPanel();
    bottomPanel.setLayout(new BorderLayout());
    bottomPanel.add(getButtonOK(), BorderLayout.CENTER);

    JPanel endedPanel = buildLinePanel("Subscribe to the recently ended items list.", "ended");
    JPanel bidPanel = buildLinePanel("Subscribe to items you're bidding or sniping on.", "bid");
    JPanel endingPanel = buildLinePanel("Subscribe to items closest to ending.", "ending");

    getBasicContentPane().add(endedPanel);
    getBasicContentPane().add(bidPanel);
    getBasicContentPane().add(endingPanel);
    //basicContentPane.add(JConfigTab.makeLine(new JButton("Copy"), JConfigTab.makeLine(new JLabel(" Subscribe to soonest ending in "), tabList)));
    getBasicContentPane().add(getButtonOK());
    SpringUtilities.makeCompactGrid(getBasicContentPane(), 4, 1, 6, 6, 6, 6);
  }

  private void buildTabList() {
    tabList.removeAllItems();
    tabList.setEditable(true);
    List<String> tabs = Category.categories();
    if(tabs != null) {
      tabs.remove("complete");
      tabs.remove("selling");

      for (String tabName : tabs) {
        if(tabName != null) tabList.addItem(tabName);
      }
    }
  }
}
