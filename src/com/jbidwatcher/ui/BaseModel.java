package com.jbidwatcher.ui;

/*
 * Copyright (c) 2000-2007, CyberFOX Software, Inc. All Rights Reserved.
 *
 * Developed by mrs (Morgan Schweers)
 */

import com.jbidwatcher.ui.ColumnStateList;

/**
 * Created by IntelliJ IDEA.
 * User: Morgan Schweers
 * Date: Mar 18, 2005
 * Time: 1:32:34 AM
 * To change this template use File | Settings | File Templates.
 */

public interface BaseModel {
  int getRowCount();
  Object getValueAt(int rowIndex, int columnIndex);
  int compare(int row1, int row2, ColumnStateList columnStateList);
  void delete(int row);
  int insert(Object newObj);
}
