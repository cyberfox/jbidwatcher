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

import com.jbidwatcher.config.JConfig;

import java.awt.*;
import java.awt.event.*;
import javax.swing.*;

//  JBidMouse needs to be renamed to JMenuAction.

public abstract class JBidContext extends JContext {
  protected JTable _inTable = null;

  protected JBidContext(JPopupMenu inPop) {
    super(inPop);
  }

  protected JBidContext() { }

  public void setTable(JTable initTable) {
    _inTable = initTable;
  }

  protected int[] getPossibleRows() {
    if(_inTable == null) return null;
    return _inTable.getSelectedRows();
  }

  protected Object getIndexedEntry(int i) {
    return _inTable.getValueAt(i, -1);
  }

  private static Object figureAuction(JTable curTable, int rowPoint) {
    return curTable.getValueAt(rowPoint, -1);
  }

  protected abstract void DoAction(Object src, String actionString, Object whichAuction);

  protected void beforePopup(JPopupMenu inPopup, MouseEvent e) {
    super.beforePopup(inPopup, e);
    if(!(e.getComponent() instanceof JComponent)) return;
    JComponent inComponent = (JComponent)e.getComponent();
    if(inComponent instanceof JTable) {
      _inTable = (JTable)inComponent;
      int row = _inTable.rowAtPoint(e.getPoint());
      if(!_inTable.isRowSelected(row)) _inTable.setRowSelectionInterval(row, row);
    }
  }

  protected void internalDoubleClick(MouseEvent e) {
    super.internalDoubleClick(e);
    if(!(e.getComponent() instanceof JComponent)) return;
    JComponent inComponent = (JComponent) e.getComponent();
    if(inComponent instanceof JTable) {
      JTable thisTable = (JTable) inComponent;

      int rowPoint = thisTable.rowAtPoint(new Point(e.getX(), e.getY()));
      Object whichAuction = figureAuction(thisTable, rowPoint);

      DoAction(thisTable, JConfig.queryConfiguration("doubleclick.action", "Update"), whichAuction);
    }
  }

  public Object resolvePoint() {
    if(_inTable == null) return null;

    Point curRow = new Point(getPopupX(), getPopupY());
    int rowPoint = _inTable.rowAtPoint(curRow);
    return figureAuction(_inTable, rowPoint);
  }

  public void actionPerformed(ActionEvent ae) {
    super.actionPerformed(ae);

    Object whichAuction = resolvePoint();
    String actionString = ae.getActionCommand();

    DoAction(ae.getSource(), actionString, whichAuction);
  }
}
