package com.jbidwatcher.ui;

import java.util.ArrayList;
import java.util.ListIterator;

public class ColumnStateList {
	
	private ArrayList columnStateList;
	
	public ColumnStateList() {
		columnStateList = new ArrayList();
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
		return (ColumnState)columnStateList.get(index);
	}
	
	public int indexOf(ColumnState columnState) {
		return columnStateList.indexOf(columnState);
	}
	
	public int size() {
		return columnStateList.size();
	}
	
	public ListIterator listIterator() {
		return columnStateList.listIterator();
	}
	
}
