package com.jbidwatcher.ui;
/*
 * Copyright (c) 2000-2007, CyberFOX Software, Inc. All Rights Reserved.
 *
 * Developed by mrs (Morgan Schweers)
 */

import javax.swing.table.*;

/*
 * @brief Allows tables to be 'sortable', without knowing anything
 * about the sorting class.
 *
 * Adds two fields to a normal abstract table to get the sort by value
 * (as opposed to the 'real' value), and the column class to use with
 * the sort by value.  The value used by the TableModel could be a
 * string, prettily formatted for the user's viewing, but when you're
 * sorting, you want the actual underlying value, and to know what
 * class it is.
 */

public abstract class AbstractSortableTableModel extends AbstractTableModel {
  public abstract Object getSortByValueAt(int row, int column);
  public abstract Class getSortByColumnClass(int column);
  public abstract int getColumnNumber(String colName);
  public abstract boolean disallowSort(int column);
}
