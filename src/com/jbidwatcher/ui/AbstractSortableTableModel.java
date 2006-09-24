package com.jbidwatcher.ui;
/*
 *
 *
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
