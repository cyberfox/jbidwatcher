package com.jbidwatcher.ui;
/*
 * Copyright (c) 2000-2007, CyberFOX Software, Inc. All Rights Reserved.
 *
 * Developed by mrs (Morgan Schweers)
 */

import com.jbidwatcher.util.config.JConfig;
import com.jbidwatcher.ui.util.JContext;

import java.awt.*;
import java.awt.event.*;
import javax.swing.*;

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
