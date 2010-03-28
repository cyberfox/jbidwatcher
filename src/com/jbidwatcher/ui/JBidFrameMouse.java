package com.jbidwatcher.ui;
/*
 * Copyright (c) 2000-2007, CyberFOX Software, Inc. All Rights Reserved.
 *
 * Developed by mrs (Morgan Schweers)
 */

import javax.swing.*;
import java.awt.event.MouseEvent;

public class JBidFrameMouse extends JBidTableContext {
  private JPopupMenu constructFramePopup() {
    JPopupMenu myPopup = new JPopupMenu();

    myPopup.add(makeMenuItem("Add")).addActionListener(this);
    myPopup.add(makeMenuItem("Paste Auction", "Paste")).addActionListener(this);
    myPopup.add(new JPopupMenu.Separator());
    myPopup.add(makeMenuItem("Configure")).addActionListener(this);
    myPopup.add(makeMenuItem("About")).addActionListener(this);
    myPopup.add(new JPopupMenu.Separator());
    myPopup.add(makeMenuItem("Exit")).addActionListener(this);

    return myPopup;
  }

  protected void internalDoubleClick(MouseEvent e) {
    JTabManager.getInstance().deselect();
  }

  public JBidFrameMouse() {
    localPopup = constructFramePopup();
  }
}
