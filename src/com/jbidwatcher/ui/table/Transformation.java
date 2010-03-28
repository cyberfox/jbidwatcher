package com.jbidwatcher.ui.table;

/*
 * Copyright (c) 2000-2007, CyberFOX Software, Inc. All Rights Reserved.
 *
 * Developed by mrs (Morgan Schweers)
 */

/**
 * Created by IntelliJ IDEA.
 * User: Morgan Schweers
 * Date: Mar 18, 2005
 * Time: 1:40:30 AM
 *
 */
public class Transformation extends BaseTransformation
{
  Transformation() { }

  Transformation(BaseTransformation chain) {
    super(chain);
  }

  public synchronized int getRowCount() {
    if (!checkRowModel()) {
      initializeRows(m_tm);
      postInitialize();
    }
    return m_row_xform.size();
  }

  public synchronized Object getValueAt(int row, int col) {
    if (!checkRowModel()) {
      initializeRows(m_tm);
      postInitialize();
    }

    //  If the row is later than the current max rows, then ignore it; it shouldn't be rendered anyway.  This may be a bug. :(
    if(row >= m_row_xform.size()) return "";

    int newrow = m_row_xform.get(row);
    int newcol = col;
    if(col != -1) {
      if(!checkColumnModel()) {
        initializeColumns(m_tm);
      }
      newcol = m_col_xform.get(col);
    }

    return m_tm.getValueAt(newrow, newcol);
  }

  public synchronized int compare(int row1, int row2, ColumnStateList columnStateList) {
    if (!checkRowModel()) {
      initializeRows(m_tm);
      postInitialize();
    }
    int newrow1 = m_row_xform.get(row1);
    int newrow2 = m_row_xform.get(row2);

    return m_tm.compare(newrow1, newrow2, columnStateList);
  }

  public synchronized void delete(int row) {
    if (!checkRowModel()) {
      initializeRows(m_tm);
      postInitialize();
    }
    m_tm.delete(getInt(m_row_xform, row));
    m_row_xform.remove(row);
    initializeRows(m_tm);
    postInitialize();
  }

  public synchronized int insert(Object newObj) {
    if (!checkRowModel()) {
      initializeRows(m_tm);
      postInitialize();
    }
    int newRow = m_tm.insert(newObj);
    m_row_xform.add(newRow);
    return m_row_xform.size()-1;
  }
}
