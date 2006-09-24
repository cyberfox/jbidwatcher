package com.jbidwatcher.ui;

import com.jbidwatcher.config.JConfigTab;
import com.jbidwatcher.config.JConfig;
import com.jbidwatcher.util.ErrorManagement;
import com.jbidwatcher.util.BrowserLauncher;
import com.jbidwatcher.JBidWatch;
import com.jbidwatcher.FilterManager;

import javax.swing.*;
import java.awt.event.*;
import java.awt.*;
import java.util.Iterator;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Created by IntelliJ IDEA.
 * User: Morgan Schweers
 * Date: Jun 28, 2005
 * Time: 10:41:37 PM
 * To change this template use File | Settings | File Templates.
 */
public class RSSDialog extends JDialog {
  private JPanel contentPane;
  private JButton buttonOK;
  private boolean cancelled=false;
  private JComboBox tabList = new JComboBox();

  public RSSDialog() {
    setupUI();

    setTitle("RSS Feed Information");
    setContentPane(contentPane);
    setModal(true);
    getRootPane().setDefaultButton(buttonOK);
    setLocationRelativeTo(null);

    buttonOK.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        onOK();
      }
    });

    // call onCancel() when cross is clicked
    setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
    addWindowListener(new WindowAdapter() {
      public void windowClosing(WindowEvent e) {
        onCancel();
      }
    });

    // call onCancel() on ESCAPE
    contentPane.registerKeyboardAction(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        onCancel();
      }
    }, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
  }

  private void onOK() {
    cancelled = false;
    dispose();
  }

  public boolean isCancelled() { return cancelled; }

  private void onCancel() {
    cancelled = true;
    setVisible(false);
  }

  public void prepare() {
    buildTabList();
  }

  private String getHost() {
    InetAddress thisIp = null;
    try {
      thisIp = InetAddress.getLocalHost();
    } catch (UnknownHostException e) {
      ErrorManagement.handleException("Local host is unknown?!?", e);
      return "127.0.0.1";
    }
    return thisIp.getHostAddress();
  }

  public void fireFeed(String feedName) {
    String url = "feed://" + getHost() + ":" + JConfig.queryConfiguration("server.port", "9099") + "/syndicate/" + feedName + ".xml";
    try {
      BrowserLauncher.openURL(url);
    } catch (IOException e) {
      ErrorManagement.handleException("Can't browse to: " + url, e);
    }
    onOK();
  }

  public void fireCopy(String feedName) {
    String url = "http://" + getHost() + ":" + JConfig.queryConfiguration("server.port", "9099") + "/syndicate/" + feedName + ".xml";
    JBidMouse.setClipboardString(url);
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
    JButton feedButton = JBidWatch.makeButton("icons/feed.gif", "Launch feed:// URL", "Feed-" + actionString, al, true);
    tmpPanel.add(JConfigTab.makeLine(feedButton, new JLabel(" ")), BorderLayout.EAST);
    return tmpPanel;
  }

  private void setupUI() {
    contentPane = new JPanel(new SpringLayout());

    buttonOK = new JButton();
    buttonOK.setText("Close");

    JPanel bottomPanel = new JPanel();
    bottomPanel.setLayout(new BorderLayout());
    bottomPanel.add(buttonOK, BorderLayout.CENTER);

    JPanel endedPanel = buildLinePanel("Subscribe to the recently ended items list.", "ended");
    JPanel bidPanel = buildLinePanel("Subscribe to items you're bidding or sniping on.", "bid");
    JPanel endingPanel = buildLinePanel("Subscribe to items closest to ending.", "ending");

    contentPane.add(endedPanel);
    contentPane.add(bidPanel);
    contentPane.add(endingPanel);
    //contentPane.add(JConfigTab.makeLine(new JButton("Copy"), JConfigTab.makeLine(new JLabel(" Subscribe to soonest ending in "), tabList)));
    contentPane.add(buttonOK);
    SpringUtilities.makeCompactGrid(contentPane, 4, 1, 6, 6, 6, 6);
  }

  private void buildTabList() {
    tabList.removeAllItems();
    tabList.setEditable(true);
    java.util.List tabs = FilterManager.getInstance().allCategories();
    if(tabs != null) {
      tabs.remove("complete");
      tabs.remove("selling");

      for (Iterator it = tabs.iterator(); it.hasNext();) {
        String tabName = (String) it.next();
        tabList.addItem(tabName);
      }
    }
  }
}
