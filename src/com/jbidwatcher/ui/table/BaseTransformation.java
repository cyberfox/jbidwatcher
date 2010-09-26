package com.jbidwatcher.ui.table;
/*
 * Copyright (c) 2000-2007, CyberFOX Software, Inc. All Rights Reserved.
 *
 * Developed by mrs (Morgan Schweers)
 */

import com.jbidwatcher.util.Currency;
import com.jbidwatcher.util.config.JConfig;

import javax.swing.table.AbstractTableModel;
import java.util.*;

/**
 * Created by IntelliJ IDEA.
 * User: Morgan Schweers
 * Date: Dec 2, 2004
 * Time: 6:48:24 PM
 *
 * A basic transformation class, if instantiated acts simply as a pass-through.
 * @noinspection AssignmentToCollectionOrArrayFieldFromParameter
 */
public abstract class BaseTransformation extends AbstractTableModel implements BaseModel
{
  protected BaseTransformation m_tm;
  protected List<Integer> m_row_xform;
  protected List<Integer> m_col_xform;

  protected BaseTransformation() {
    m_tm = null;
    m_row_xform = Collections.synchronizedList(new ArrayList<Integer>(0));
    m_col_xform = Collections.synchronizedList(new ArrayList<Integer>(0));
  }

  protected BaseTransformation(BaseTransformation chain) {
    m_tm = chain;
    initializeRows(chain);
    initializeColumns(chain);
  }

  protected synchronized void initializeColumns(BaseTransformation chain) {
    int chain_cols = chain.getColumnCount();
    m_col_xform = Collections.synchronizedList(new ArrayList<Integer>(chain_cols));
    for(int i=0; i<chain_cols; i++) m_col_xform.add(i);
  }

  protected synchronized void initializeRows(BaseTransformation chain) {
    int chain_rows = chain.getRowCount();
    m_row_xform = Collections.synchronizedList(new ArrayList<Integer>(chain_rows));
    for(int i=0; i<chain_rows; i++) m_row_xform.add(i);
  }

  protected synchronized boolean checkRowModel() {
    return m_row_xform.size() == m_tm.getRowCount();
  }

  protected synchronized boolean checkColumnModel() {
    return m_col_xform.size() == m_tm.getColumnCount();
  }

  public synchronized int getColumnCount() {
    if (!checkColumnModel()) {
      initializeColumns(m_tm);
    }
    return m_col_xform.size();
  }

  protected synchronized void setRowTransform(List<Integer> newRows) { m_row_xform = newRows; }
  protected synchronized void setColumnTransform(List<Integer> newCols) { m_col_xform = newCols; }

  protected synchronized void postInitialize() { }

  protected static int getInt(List<Integer> l, int index) {
    return l.get(index);
  }

  public synchronized int findRow(Object o) {
    for(int i=0; i<getRowCount(); i++) {
      if(getValueAt(i, -1).equals(o)) return i;
    }
    return -1;
  }

  public synchronized int getColumnNumber(String colName) {
    return m_tm.getColumnNumber(colName);
  }

  public synchronized String getColumnName(int aColumn) {
    return m_tm.getColumnName(aColumn);
  }

  public synchronized Class getColumnClass(int aColumn) {
    return m_tm.getColumnClass(aColumn);
  }

  public synchronized int convertRowIndexToView(int row) {
    if(m_tm != null) {
      int new_row = m_tm.convertRowIndexToView(row);
      if(new_row == -1) return -1;
      for(int i=0; i<=getRowCount(); i++) {
        Integer xlate = m_row_xform.get(i);
        if(xlate == new_row) return i;
      }
    } else {
      if(row < getRowCount()) return row;
    }

    return -1;
  }

  public synchronized int convertRowIndexToModel(int row) {
    if(row == -1) return -1;
    if(m_tm != null) {
      Integer xlate = m_row_xform.get(row);
      return m_tm.convertRowIndexToModel(xlate);
    } else {
      if(row < getRowCount()) return row;
    }

    return -1;
  }

  public synchronized boolean isCellEditable(int row, int column) {
    return m_tm.isCellEditable(row, column);
  }

  protected static int compareByClass(Object o1, Object o2, Class type) {
    // If both values are null return 0, they are after all, equal.
    if (o1 == null && o2 == null) {
      return 0;
    } else if (o1 == null) { // Define null less than everything.
      return -1;
    } else if (o2 == null) {
      return 1;
    }

    if (type == Integer.class) {
      return compareInt(o1, o2);
    } else if (type.getSuperclass() == java.lang.Number.class) {
      return compareNumber(o1, o2);
    } else if (type == java.util.Date.class) {
      return compareDate(o1, o2);
    } else if (type == Integer.class) {
      return compareInt(o1, o2);
    } else if (type == String.class) {
      return compareString(o1, o2);
    } else if (type == Currency.class) {
      return compareCurrency(o1, o2);
    } else if (type == Boolean.class) {
      return compareBoolean(o1, o2);
    } else {
      return compareUnknownAsString(o1, o2, type);
    }
  }

  private static int compareUnknownAsString(Object o1, Object o2, Class type) {
    String s1 = o1.toString();
    String s2 = o2.toString();
    int result = s1.compareTo(s2);

    JConfig.log().logDebug("Being asked to compare unknown type: " + type);

    return (result < 0) ? -1 : ((result > 0) ? 1 : 0);
  }

  private static int compareBoolean(Object o1, Object o2) {
    boolean b1 = (Boolean) o1;
    boolean b2 = (Boolean) o2;

    // Define false < true
    return (b1 == b2) ? 0 : (b1 ? 1 : -1);
  }

  private static int compareCurrency(Object o1, Object o2) {
    Currency c1 = (Currency) o1;
    Currency c2 = (Currency) o2;
    int result = c1.compareTo(c2);

    return (result < 0) ? -1 : ((result > 0) ? 1 : 0);
  }

  private static int compareString(Object o1, Object o2) {
    String s1 = o1.toString();
    String s2 = o2.toString();
    int result = s1.compareToIgnoreCase(s2);

    return (result < 0) ? -1 : ((result > 0) ? 1 : 0);
  }

  private static int compareDate(Object o1, Object o2) {
    long n1 = ((Date) o1).getTime();
    long n2 = ((Date) o2).getTime();

    return (n1 < n2) ? -1 : ((n1 > n2) ? 1 : 0);
  }

  private static int compareNumber(Object o1, Object o2) {
    double d1 = ((Number) o1).doubleValue();
    double d2 = ((Number) o2).doubleValue();

    return (d1 < d2) ? -1 : ((d1 > d2) ? 1 : 0);
  }

  private static int compareInt(Object o1, Object o2) {
    int n1 = (Integer) o1;
    int n2 = (Integer) o2;

    return (n1 < n2) ? -1 : ((n1 > n2) ? 1 : 0);
  }
}
