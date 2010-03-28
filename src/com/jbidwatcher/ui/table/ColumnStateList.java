package com.jbidwatcher.ui.table;

/*
 * Copyright (c) 2000-2007, CyberFOX Software, Inc. All Rights Reserved.
 *
 * Developed by mrs (Morgan Schweers)
 */

import java.util.ArrayList;
import java.util.ListIterator;

public class ColumnStateList {
	
	private ArrayList<ColumnState> columnStateList;
	
	public ColumnStateList() {
		columnStateList = new ArrayList<ColumnState>();
	}
	
	public void clear() {
		columnStateList.clear();
	}
	
	public boolean add(ColumnState columnState) {
		return columnStateList.add(columnState);
	}
	
	public boolean remove(ColumnState columnState) {
		return columnStateList.remove(columnState);
	}
	
	public ColumnState get(int index) {
		return columnStateList.get(index);
	}
	
	public int indexOf(ColumnState columnState) {
		return columnStateList.indexOf(columnState);
	}
	
	public int size() {
		return columnStateList.size();
	}
	
	public ListIterator<ColumnState> listIterator() {
		return columnStateList.listIterator();
	}
	
}
