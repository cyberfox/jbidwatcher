package com.jbidwatcher.ui.table;

/*
 * Copyright (c) 2000-2007, CyberFOX Software, Inc. All Rights Reserved.
 *
 * Developed by mrs (Morgan Schweers)
 */

import java.util.ArrayList;
import java.util.ListIterator;

public class ColumnStateList {
	
	private ArrayList<com.jbidwatcher.ui.table.ColumnState> columnStateList;
	
	public ColumnStateList() {
		columnStateList = new ArrayList<com.jbidwatcher.ui.table.ColumnState>();
	}
	
	public void clear() {
		columnStateList.clear();
	}
	
	public boolean add(com.jbidwatcher.ui.table.ColumnState columnState) {
		return columnStateList.add(columnState);
	}
	
	public boolean remove(com.jbidwatcher.ui.table.ColumnState columnState) {
		return columnStateList.remove(columnState);
	}
	
	public com.jbidwatcher.ui.table.ColumnState get(int index) {
		return columnStateList.get(index);
	}
	
	public int indexOf(com.jbidwatcher.ui.table.ColumnState columnState) {
		return columnStateList.indexOf(columnState);
	}
	
	public int size() {
		return columnStateList.size();
	}
	
	public ListIterator<com.jbidwatcher.ui.table.ColumnState> listIterator() {
		return columnStateList.listIterator();
	}
	
}
