package com.jbidwatcher.util.html;

/*
 * Copyright (c) 2000-2007, CyberFOX Software, Inc. All Rights Reserved.
 *
 * Developed by mrs (Morgan Schweers)
 */

import java.util.ListIterator;
import java.util.NoSuchElementException;

public class URLPagerIterator implements ListIterator {

	private AbstractURLPager urlPager;
	private int index;

	public URLPagerIterator(AbstractURLPager pager, int idx) {
		urlPager = pager;
		index = idx;
	}

	public boolean hasNext() {
		return index < urlPager.size();
	}

	public Object next() {
		if(hasNext()) {
			index = nextIndex();
			return urlPager.getPage(index);
		} else {
			throw new NoSuchElementException();
		}
	}

	public boolean hasPrevious() {
		return index > 0;
	}

	public Object previous() {
		if(hasPrevious()) {
			index = previousIndex();
			return urlPager.getPage(index);
		} else {
			throw new NoSuchElementException();
		}
	}

	public int nextIndex() {
		return hasNext() ? (index + 1) : urlPager.size();
	}

	public int previousIndex() {
		return index - 1;
	}

	public void remove() {
		// Do nothing
	}

	public void set(Object o) {
		// Do nothing
	}
	
	public void add(Object o) {
		// Do nothing
	}
}
