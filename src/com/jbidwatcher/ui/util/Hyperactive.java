package com.jbidwatcher.ui.util;
/*
 * Copyright (c) 2000-2007, CyberFOX Software, Inc. All Rights Reserved.
 *
 * Developed by mrs (Morgan Schweers)
 */

import com.jbidwatcher.util.queue.MQFactory;
import com.jbidwatcher.util.config.JConfig;

import javax.swing.*;
import javax.swing.event.*;

public class Hyperactive implements HyperlinkListener {
  JEditorPane _pane;
  public Hyperactive(JEditorPane tPane) {
    super();
    _pane = tPane;
  }

  public void hyperlinkUpdate(HyperlinkEvent e) {
    if (e.getEventType().equals(HyperlinkEvent.EventType.ACTIVATED)) {
      String desc = e.getDescription();
      if(desc != null)
      if (desc.startsWith("#")) {
        _pane.scrollToReference(desc.substring(1));
      } else if (desc.startsWith("/")) {
        MQFactory.getConcrete("user").enqueue(desc.substring(1));
      } else {
        try {
          MQFactory.getConcrete("browse").enqueue(e.getDescription());
        } catch(Exception except) {
          JConfig.log().handleException("Launching URL " + e.getDescription() + " failed: " + except, except);
          JOptionPane.showMessageDialog(null, "Failed to launch link.",
                                        "Link error", JOptionPane.PLAIN_MESSAGE);
        }
      }
    }
  }
}
