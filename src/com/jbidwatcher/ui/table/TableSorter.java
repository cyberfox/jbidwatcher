package com.jbidwatcher.ui.table;
/*
 * @(#)TableSorter.java 1.5 97/12/17
 *
 * Copyright (c) 1997 Sun Microsystems, Inc. All Rights Reserved.
 *
 * This software is the confidential and proprietary information of Sun
 * Microsystems, Inc. ("Confidential Information").  You shall not
 * disclose such Confidential Information and shall use it only in
 * accordance with the terms of the license agreement you entered into
 * with Sun.
 *
 * SUN MAKES NO REPRESENTATIONS OR WARRANTIES ABOUT THE SUITABILITY OF THE
 * SOFTWARE, EITHER EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR
 * PURPOSE, OR NON-INFRINGEMENT. SUN SHALL NOT BE LIABLE FOR ANY DAMAGES
 * SUFFERED BY LICENSEE AS A RESULT OF USING, MODIFYING OR DISTRIBUTING
 * THIS SOFTWARE OR ITS DERIVATIVES.
 *
 */

/**
 * A sorter for AbstractSortableTableModels. The sorter has a model (conforming to AbstractSortableTableModel)
 * and itself implements AbstractSortableTableModel. TableSorter does not store or copy
 * the data in the AbstractSortableTableModel, instead it maintains an array of
 * integers which it keeps the same size as the number of rows in its
 * model. When the model changes it notifies the sorter that something
 * has changed eg. "rowsAdded" so that its internal array of integers
 * can be reallocated. As requests are made of the sorter (like
 * getSortByValueAt(row, col) it redirects them to its model via the mapping
 * array. That way the TableSorter appears to hold another copy of the table
 * with the rows in a different order. The sorting algorthm used is stable
 * which means that it does not move around rows when its comparison
 * function returns 0 to denote that they are equivalent.
 *
 * @version 1.5 12/17/97
 * @author Philip Milne
 */

import com.jbidwatcher.util.config.JConfig;

import java.util.*;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;

// Imports for picking up mouse events from the JTable.

import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.InputEvent;
import java.awt.*;
import javax.swing.table.*;
import javax.swing.*;

public class TableSorter extends Transformation implements TableModelListener {
  private JTable _table = null;
  private ColumnStateList columnStateList;
  private BaseTransformation _model = null;
  private SortTransformation _sorted = null;

  private static final Icon ascend = new ImageIcon(JConfig.getResource("/icons/ascend_10x5.gif"));
  private static final Icon descend = new ImageIcon(JConfig.getResource("/icons/descend_10x5.gif"));

  public TableSorter(String name, String defaultColumn, BaseTransformation tm) {
    _model = tm;
    columnStateList = new ColumnStateList();
    _sorted = new SortTransformation(_model);
    m_tm = _sorted;
    setDefaults(name, defaultColumn);
  }

  public void tableChanged(TableModelEvent e) {
    fireTableChanged(e);
  }

  public void sort() {
    final TableSorter sorter = this;

    SwingUtilities.invokeLater(new Runnable() {
      public void run() {
        Selection save = new Selection(_table, _sorted);
        _sorted.sort();
        _table.tableChanged(new TableModelEvent(sorter));
        restoreSelection(save);
      }
    });
  }

  private void sortByList() {
    _sorted.setSortList(columnStateList);

    if(_table != null) sort();
  }

  private void setDefaults(String inName, String defaultColumn) {
    columnStateList.clear();

	  for(int i=0; ; i++) {
      String sortByColumn;
      String sortDirection;
      if(i==0) {
			  // Initially sort by ending time, ascending.
			  sortByColumn = JConfig.queryDisplayProperty(inName + ".sort_by", defaultColumn);
			  sortDirection = JConfig.queryDisplayProperty(inName + ".sort_direction", "ascending");
		  } else {
			  sortByColumn = JConfig.queryDisplayProperty(inName + ".sort_by" + "_" + i);
			  sortDirection = JConfig.queryDisplayProperty(inName + ".sort_direction" + "_" + i);
		  }

		  if(sortByColumn == null || sortDirection == null) {
			  break;
		  }

      ColumnState cs = new ColumnState(getColumnNumber(sortByColumn), sortDirection.equals("ascending") ? 1 : -1);

      if(columnStateList.indexOf(cs) == -1) columnStateList.add(cs);
	  }

	  sortByList();
  }

  public int getRowCount() {  return m_tm.getRowCount(); }
  public int getColumnCount() { return m_tm.getColumnCount(); }
  public synchronized Object getValueAt(int row, int col) { return m_tm.getValueAt(row, col); }

  public boolean delete(Object o) {
    final int myRow;
    synchronized(this) {
      myRow = m_tm.findRow(o);
    }

    final TableSorter sorter = this;
    if(myRow == -1) return false;
    final Selection save = new Selection(_table, _sorted);
    save.delete(myRow);
    synchronized (this) {
      m_tm.delete(myRow);
    }
    try {
      SwingUtilities.invokeAndWait(new Runnable() {
        public void run() {
          _table.tableChanged(new TableModelEvent(sorter, myRow, myRow, TableModelEvent.ALL_COLUMNS, TableModelEvent.DELETE));
          restoreSelection(save);
        }
      });
    } catch (Exception e) {
      e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
    }
    return true;
  }

  private static class Selection {
    protected   final int[] selected;   // used ONLY within save/restoreSelection();
    protected   int     lead = -1;
    protected SortTransformation sorter;

    protected void delete(int viewRow) {
      int modelRow = sorter.convertRowIndexToModel(viewRow);
      for(int i=0; i<selected.length; i++) {
        if(selected[i] > modelRow) {
          selected[i]--;
        } else if(selected[i] == modelRow) {
          selected[i] = -1;
        }
      }
      if(lead > modelRow) lead--; else if(lead == modelRow) lead = -1;
    }

    protected Selection(JTable table, SortTransformation sortBy) {
      sorter = sortBy;
      selected = table.getSelectedRows(); // in view coordinates
      for (int i = 0; i < selected.length; i++) {
        int view = selected[i];
        selected[i] = sorter.convertRowIndexToModel(view);    // model coordinates
      }

      if (selected.length > 0) {
        // convert lead selection index to model coordinates
        lead = sorter.convertRowIndexToModel(table.getSelectionModel().getLeadSelectionIndex());
      }
    }
  }

  private void restoreSelection(Selection selection) {
    _table.clearSelection();   // call overridden version

    boolean lead_selected = false;
    for (int i = 0; i < selection.selected.length; i++) {
      int selected = selection.selected[i];
      if(selected != selection.lead && selected != -1) {
          int index = _sorted.convertRowIndexToView(selected);
          if(index != -1) _table.getSelectionModel().addSelectionInterval(index, index);
      }
      if(selected == selection.lead) lead_selected = true;
    }

    if(selection.lead >= 0) {
      int new_lead = _sorted.convertRowIndexToView(selection.lead);
      if(new_lead != -1) {
        if(lead_selected) {
          _table.getSelectionModel().addSelectionInterval(new_lead, new_lead);
        } else {
          _table.getSelectionModel().removeSelectionInterval(new_lead, new_lead);
        }
      }
    }
  }

  public int insert(Object o) {
    final Selection save = getSelectionSafely();

    int myRow;
    synchronized(this) {
      myRow = m_tm.insert(o);
    }
    if(myRow == -1) return -1;

    final TableSorter sorter = this;
    final int count = m_tm.getRowCount();

    SwingUtilities.invokeLater(new Runnable() {
      public void run() {
        _table.tableChanged(new TableModelEvent(sorter, 0, count, TableModelEvent.ALL_COLUMNS, TableModelEvent.INSERT));
        if(save != null) restoreSelection(save);
      }
    });
    return myRow;
  }

  private Selection getSelectionSafely() {
    Selection save;
    try {
      save = new Selection(_table, _sorted);
    } catch(Exception e) {
      save = null;
    }
    return save;
  }

  public boolean update(final Object updated) {
    final int myRow;
    synchronized(this) {
      myRow = m_tm.findRow(updated);
    }
    final TableSorter sorter = this;
    if (myRow != -1) {
      SwingUtilities.invokeLater(new Runnable() {
        public void run() {
          Selection save = new Selection(_table, _sorted);
          _table.tableChanged(new TableModelEvent(sorter, myRow));
          restoreSelection(save);
        }
      });
    }
    return myRow != -1;
  }

  public void updateTime() {
    final TableSorter sorter = this;
    SwingUtilities.invokeLater(new Runnable() {
      public void run() {
        _table.tableChanged(new TableModelEvent(sorter, 0, _sorted.getRowCount(), getColumnNumber("Time left")));
      }
    });
  }

  private static class SortHeaderRenderer extends JLabel implements TableCellRenderer {
    public SortHeaderRenderer() {
      setHorizontalTextPosition(LEFT);
      setHorizontalAlignment(CENTER);
    }

    public Component getTableCellRendererComponent(JTable table, Object value,
                                                   boolean isSelected, boolean hasFocus,
                                                   int row, int col) {
      if (table != null) {
        JTableHeader header = table.getTableHeader();
        if (header != null) {
          setForeground(header.getForeground());
          setBackground(header.getBackground());
          setFont(header.getFont());
        }
      }

      setText((value == null) ? "" : value.toString());
      setBorder(UIManager.getBorder("TableHeader.cellBorder"));

      return this;
    }
  }

  public Properties getSortProperties(String prefix, Properties outProps) {
    TableColumnModel tableColumnModel = _table.getColumnModel();
    for(int i=0; i < columnStateList.size(); i++) {
      ColumnState columnState = columnStateList.get(i);

      // Restore original header
      int viewCol = _table.convertColumnIndexToView(columnState.getColumn());
      if(viewCol != -1) {
        TableColumn tableColumn = tableColumnModel.getColumn(viewCol);
        tableColumn.setHeaderValue(columnState.getHeaderValue());

        outProps.setProperty(prefix + ".sort_by" + (i > 0 ? "_" + i : ""), _model.getColumnName(columnState.getColumn()));
        outProps.setProperty(prefix + ".sort_direction" + (i > 0 ? "_" + i : ""), columnState.getSort() == 1 ? "ascending" : "descending");
      }
    }

    return outProps;
  }

  private void setArrow(TableColumnModel tcm, int col, int direction) {
    if(col == -1) return;
    //col = _table.convertColumnIndexToModel(col);
    TableColumn tc = tcm.getColumn(col);
    TableCellRenderer tcr = tc.getHeaderRenderer();

    if (tcr == null || !(tcr instanceof SortHeaderRenderer)) {
      tcr = new SortHeaderRenderer();
      tc.setHeaderRenderer(tcr);
    }

    SortHeaderRenderer shr = (SortHeaderRenderer) tcr;

    switch (direction) {
      case -1:
        shr.setIcon(ascend);
        break;
      case 0:
        shr.setIcon(null);
        break;
      case 1:
        shr.setIcon(descend);
        break;
        // Can't happen, because the result set is only -1,
        //  0, 1, but static analysis can't determine that.
      default:
        break;
    }
  }

  // There is no-where else to put this.
  // Add a mouse listener to the Table to trigger a table sort
  // when a column heading is clicked in the JTable.
  public void addMouseListenerToHeaderInTable(JTable table) {
    TableColumnModel tableColumnModel = table.getColumnModel();

    _table = table;

    // Restore the header as it was saved
    for(int i=0; i < columnStateList.size(); i++) {
      ColumnState columnState = columnStateList.get(i);
      int viewCol = table.convertColumnIndexToView(columnState.getColumn());
      if(viewCol != -1) {
        TableColumn tableColumn = tableColumnModel.getColumn(viewCol);

        // Save original header
        String headerValue = (String) tableColumn.getHeaderValue();
        columnState.setHeaderValue(headerValue);
        // Set new header
        tableColumn.setHeaderValue(headerValue + (i > 0 ? " (" + (i + 1) + ")" : ""));
        tableColumn.setIdentifier(headerValue);
        // Set arrow
        setArrow(tableColumnModel, table.convertColumnIndexToView(columnState.getColumn()), columnState.getSort());
      }
    }

    table.setColumnSelectionAllowed(false);
    MouseAdapter listMouseListener = new SortMouseAdapter(table, this);
    table.getTableHeader().addMouseListener(listMouseListener);
  }

  public void removeColumn(String colId, JTable table) {
    for(int i=0; i<columnStateList.size(); i++) {
      ColumnState cs = columnStateList.get(i);
      if(cs.getHeaderValue().equals(colId)) {
        columnStateList.remove(cs);
        i--;
      }
      refreshColumns(table, false);
    }
  }

  private void refreshColumns(JTable table, boolean resetHeaders) {
    int skipped = 0;
    TableColumnModel columnModel = table.getColumnModel();
    for(int i=0; i<columnStateList.size(); i++) {
      ColumnState cs = columnStateList.get(i);
      int view = table.convertColumnIndexToView(cs.getColumn());
      if(view != -1) {
        TableColumn tc = columnModel.getColumn(view);

        if(resetHeaders) {
          setArrow(columnModel, view, 0);
          tc.setHeaderValue(cs.getHeaderValue());
        } else {
          tc.setHeaderValue(cs.getHeaderValue() + (i > 0 ? " (" + (i + 1 - skipped) + ")" : ""));
        }
      } else {
        skipped++;
      }
    }
  }

  public Object getObjectAt(int x, int y) {
    if (_table != null) {
      int rowPoint = _table.rowAtPoint(new Point(x, y));

      //  A menu item has been selected, instead of a context menu.
      //  This is NOT a valid test, because the popup locations aren't
      //  reset!
      if (x == 0 && y == 0) {
        rowPoint = _table.getSelectedRow();
      }

      if (rowPoint != -1) {
        return getValueAt(rowPoint, -1);
      }
    }
    return null;
  }

  public boolean select(Selector s) {
    return s.select(_table);
  }

  public int[] getSelectedRows() {
    return _table.getSelectedRows();
  }

  public JTable getTable() { return _table; }

  private class SortMouseAdapter extends MouseAdapter
  {
    private final JTable mTable;
    private final TableSorter mSorter;

    public SortMouseAdapter(JTable table, TableSorter sorter) {
      mTable = table;
      mSorter = sorter;
    }

    public void mouseClicked(MouseEvent e) {
      TableColumnModel columnModel = mTable.getColumnModel();
      int viewColumn = columnModel.getColumnIndexAtX(e.getX());
      TableColumn tc = columnModel.getColumn(viewColumn);
      int modelColumn = mTable.convertColumnIndexToModel(viewColumn);

      if(e.getClickCount() == 1) {
        ColumnState columnState = new ColumnState(modelColumn);
        int csidx = columnStateList.indexOf(columnState);
        if ((e.getModifiers() & InputEvent.CTRL_MASK) != InputEvent.CTRL_MASK) {
          if(columnStateList.size() > 1 || csidx == -1) {
            refreshColumns(mTable, true);
            columnStateList.clear();
            columnState.setSortState(1);
          } else {
            if(columnStateList.size() == 1) {
              columnState = columnStateList.get(0);
              columnState.setSortState(columnState.getSort()==1?-1:1);
            }
          }
          columnState.setHeaderValue((String) tc.getHeaderValue());
          setArrow(columnModel, viewColumn, columnState.getSort());
          columnStateList.add(columnState);
        } else {
          if (csidx == -1) {
            // Not yet sorted by this column, add to list
            columnState.setHeaderValue((String) tc.getHeaderValue());
            columnStateList.add(columnState);
          } else {
            columnState = columnStateList.get(csidx);
          }

          // Transition to next sort (undefined -> asc, asc -> desc, desc -> undefined
          int state = columnState.setNextSortState();

          setArrow(columnModel, viewColumn, state);

          if (state == 0) {
            // Restore original header
            tc.setHeaderValue(columnState.getHeaderValue());
            // Reached undef state again so remove from sort list
            columnStateList.remove(columnState);
          }

          // Renumber new / rest of headers accordingly
          refreshColumns(mTable, false);
        }

        mSorter.sortByList();
        mTable.getTableHeader().repaint();
      }
    }
  }

  public void enableInsertionSorting() {
    _sorted.sortOnInsert();
  }
}
