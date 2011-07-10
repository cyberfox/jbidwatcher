package com.jbidwatcher.ui.config;

/*
 * Copyright (c) 2000-2007, CyberFOX Software, Inc. All Rights Reserved.
 *
 * Developed by mrs (Morgan Schweers)
 */

import com.jbidwatcher.util.config.*;

import javax.swing.*;
import java.awt.*;

public class JConfigWebserverTab extends JConfigTab
{
  private JCheckBox localServerBrowseBox;
  private JCheckBox openSyndication;

  public void cancel() {}

  public void apply() {
    JConfig.setConfiguration("server.enabled", localServerBrowseBox.isSelected() ? "true" : "false");
    JConfig.setConfiguration("allow.syndication", openSyndication.isSelected() ? "true" : "false");

  }

  public void updateValues() {
    String doLocalServer = JConfig.queryConfiguration("server.enabled", "false");
    String doAllowSyndication = JConfig.queryConfiguration("allow.syndication", "true");

    localServerBrowseBox.setSelected(doLocalServer.equals("true"));
    openSyndication.setSelected(doAllowSyndication.equals("true"));
  }

  private JPanel buildCheckboxPanel() {
    JPanel tp = new JPanel();

    tp.setBorder(BorderFactory.createTitledBorder("Webserver Options"));
    tp.setLayout(new GridLayout(1, 2));
    String doLocalServer = JConfig.queryConfiguration("server.enabled", "false");
    String doAllowSyndication = JConfig.queryConfiguration("allow.syndication", "true");

    localServerBrowseBox = new JCheckBox("Use internal web server");
    localServerBrowseBox.setToolTipText("<html><body>Turning this on enables JBidwatchers internal web server; 'Show in Browser' will go through JBidwatcher<br>first, in order to allow it to show old/deleted auctions,and to avoid the need to log in regularly.<br>The internal web server is password protected with your auction server username/password.</body></html>");
    //localServerBrowseBox.setToolTipText("Turning this on enables JBidwatchers internal web server; 'Show in Browser' will go through JBidwatcher first, in order to allow it to show old/deleted auctions, and to avoid the need to log in regularly.  The internal web server is password protected with your auction server username/password.");
    localServerBrowseBox.setSelected(doLocalServer.equals("true"));
    tp.add(localServerBrowseBox);

    openSyndication = new JCheckBox("Allow syndication to bypass authentication");
    openSyndication.setToolTipText("Allows syndication requests and thumbnail requests to be resolved without requiring a username/password.");
    openSyndication.setSelected(doAllowSyndication.equals("true"));
    tp.add(openSyndication);

    return tp;
  }


  public JConfigWebserverTab() {
    setLayout(new BorderLayout());
    JPanel jp = new JPanel();
    jp.setLayout(new BorderLayout());
    jp.add(panelPack(buildCheckboxPanel()), BorderLayout.NORTH);
    add(jp, BorderLayout.NORTH);
  }

  public String getTabName() { return("Webserver"); }
}
