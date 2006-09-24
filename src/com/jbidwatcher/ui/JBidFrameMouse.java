package com.jbidwatcher.ui;
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

import javax.swing.*;

public class JBidFrameMouse extends JBidMouse {
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

  public JBidFrameMouse() {
    localPopup = constructFramePopup();
  }
}
