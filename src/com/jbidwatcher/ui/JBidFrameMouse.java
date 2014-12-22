package com.jbidwatcher.ui;
/*
 * Copyright (c) 2000-2007, CyberFOX Software, Inc. All Rights Reserved.
 *
 * Developed by mrs (Morgan Schweers)
 */

import com.google.inject.Inject;

import javax.swing.*;
import java.awt.event.MouseEvent;

public class JBidFrameMouse extends JBidTableContext {
  private final JTabManager tabManager;

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
    tabManager.deselect();
  }

  @Inject
  public JBidFrameMouse(JTabManager tabManager, ListManager listManager) {
    super(tabManager, listManager);
    this.tabManager = tabManager;
    localPopup = constructFramePopup();
  }
}
