package com.jbidwatcher.ui.table;

/*
 * Copyright (c) 2000-2007, CyberFOX Software, Inc. All Rights Reserved.
 *
 * Developed by mrs (Morgan Schweers)
 */

public class ColumnState {
	private int mColumn;
	private int mSort = 0;
	private String mHeaderValue = null;

	ColumnState(int column, int sort) {
		mColumn = column;
		mSort = sort;
	}

	ColumnState(int column) {
		mColumn = column;
		mSort = 0;
	}

	public int setNextSortState() {
		mSort = (mSort == 1 ? - 1 : ++mSort);

		return mSort;
	}

  public void setHeaderValue(String headerValue) { mHeaderValue = headerValue; }

  public int getColumn() { return mColumn; }
	public int getSort() { return mSort; }
	public String getHeaderValue() { return mHeaderValue; }

	public boolean equals(Object o) {
		if(o.getClass() != ColumnState.class) {
			return false;
		}

		ColumnState c = (ColumnState)o;
		return c.mColumn == mColumn;
	}

  public void setSortState(int sort) {
    mSort = sort;
  }
}
