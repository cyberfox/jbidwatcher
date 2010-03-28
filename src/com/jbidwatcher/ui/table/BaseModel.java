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
 * Time: 1:32:34 AM
 *
 * The basic model for table transformations.
 */

public interface BaseModel {
  int getRowCount();
  Object getValueAt(int rowIndex, int columnIndex);
  int compare(int row1, int row2, ColumnStateList columnStateList);
  void delete(int row);
  int insert(Object newObj);
}
