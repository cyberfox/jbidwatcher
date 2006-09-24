package com.jbidwatcher.util;
/*
 * Copyright (c) 2000-2005 CyberFOX Software, Inc. All Rights Reserved.
 *
 * This library is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Library General Public License as published
 * by the Free Software Foundation; either version 2 of the License, or (at
 * your option) any later version.
 *
 * This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Library General Public License for more
 * details.
 *
 * You should have received a copy of the GNU Library General Public License
 * along with this library; if not, write to the
 *  Free Software Foundation, Inc.
 *  59 Temple Place
 *  Suite 330
 *  Boston, MA 02111-1307
 *  USA
 */

import com.jbidwatcher.queue.MQFactory;
import com.jbidwatcher.ui.JBEditorPane;

import javax.swing.*;
import javax.swing.event.*;

public class Hyperactive implements HyperlinkListener {
  JBEditorPane _pane;
  public Hyperactive(JBEditorPane tPane) {
    super();
    _pane = tPane;
  }

  public void hyperlinkUpdate(HyperlinkEvent e) {
    if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
      String desc = e.getDescription();
      if(desc != null && desc.startsWith("#")) {
        _pane.scrollToReference(desc.substring(1));
      } else {
        try {
          MQFactory.getConcrete("browse").enqueue(e.getDescription());
        } catch(Exception except) {
          ErrorManagement.handleException("Launching URL " + e.getDescription() + " failed: " + except, except);
          JOptionPane.showMessageDialog(null, "Failed to launch link.",
                                        "Link error", JOptionPane.PLAIN_MESSAGE);
        }
      }
    }
  }
}
