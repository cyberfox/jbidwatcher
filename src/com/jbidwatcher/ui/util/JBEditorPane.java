package com.jbidwatcher.ui.util;
/*
 * Copyright (c) 2000-2007, CyberFOX Software, Inc. All Rights Reserved.
 *
 * Developed by mrs (Morgan Schweers)
 */

import javax.swing.*;

public class JBEditorPane extends JEditorPane {
  public void scrollToReference(String reference) {
    super.scrollToReference(reference);
  }
  public JBEditorPane(String mimetype, String data) {
    super(mimetype, data);
  }
}
