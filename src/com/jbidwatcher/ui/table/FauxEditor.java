package com.jbidwatcher.ui.table;

import javax.swing.table.TableCellEditor;
import javax.swing.*;
import javax.swing.event.CellEditorListener;
import java.awt.Component;
import java.awt.event.MouseEvent;
import java.util.EventObject;

/**
 * Created by IntelliJ IDEA.
 * User: mrs
 * Date: Nov 19, 2009
 * Time: 1:59:45 AM
 *
 * This works around a nasty bug in Java 1.6, where it clears the selection if the context/popup menu
 * mouse trigger is clicked.  It needs to refuse the cell selection in the case of a popup trigger.
 *
 * All this is just for the shouldSelectCell override.
 */
public class FauxEditor implements TableCellEditor {
  public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
    return null;
  }

  public Object getCellEditorValue() {
    return null;
  }

  public boolean isCellEditable(EventObject anEvent) {
    return false;
  }

  public boolean shouldSelectCell(EventObject anEvent) {
    MouseEvent me = (MouseEvent) anEvent;
    return !me.isPopupTrigger();
  }

  public boolean stopCellEditing() {
    return false;
  }

  public void cancelCellEditing() { }
  public void addCellEditorListener(CellEditorListener l) { }
  public void removeCellEditorListener(CellEditorListener l) { }
}
