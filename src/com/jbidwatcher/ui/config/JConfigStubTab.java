package com.jbidwatcher.ui.config;
/*
 * Copyright (c) 2000-2007, CyberFOX Software, Inc. All Rights Reserved.
 *
 * Developed by mrs (Morgan Schweers)
 */

import java.awt.*;
import javax.swing.*;

public abstract class JConfigStubTab extends JConfigTab {
  public void cancel() { }
  public void apply() { }
  public void updateValues() { }

  public JConfigStubTab() {
    super();

    JLabel newLabel;

    newLabel = new JLabel("This space intentionally left blank!");

    this.setLayout(new BorderLayout());
    this.add(newLabel, BorderLayout.CENTER);
  }
}
