package com.jbidwatcher.ui.table;
/*
 * Copyright (c) 2000-2007, CyberFOX Software, Inc. All Rights Reserved.
 *
 * Developed by mrs (Morgan Schweers)
 */

import java.util.List;
import java.util.ArrayList;

/**
 * Created by IntelliJ IDEA.
 * User: Administrator
 * Date: Dec 2, 2004
 * Time: 7:54:23 PM
 *
 */
public class SortTransformation extends Transformation {
  private ColumnStateList mColumnStateList;

  SortTransformation(BaseTransformation chain) {
    super(chain);
  }

  public synchronized void sort() {
    if(mColumnStateList == null || mColumnStateList.size() == 0)
      return;

    shuttlesort(new ArrayList<Integer>(m_row_xform), m_row_xform, 0, m_row_xform.size());
  }

  public void setSortList(ColumnStateList columnStateList) {
    mColumnStateList = columnStateList;
  }

  protected void postInitialize() {
    sort();
  }

  // This is a home-grown implementation which we have not had time
  // to research - it may perform poorly in some circumstances. It
  // requires twice the space of an in-place algorithm and makes
  // NlogN assigments shuttling the values between the two
  // arrays. The number of compares appears to vary between N-1 and
  // NlogN depending on the initial order but the main reason for
  // using it here is that, unlike qsort, it is stable.
  private void shuttlesort(List<Integer> from, List<Integer> to, int low, int high) {
    if (high - low < 2) {
      return;
    }
    int middle = (low + high) / 2;
    shuttlesort(to, from, low, middle);
    shuttlesort(to, from, middle, high);

    int p = low;
    int q = middle;

    /* This is an optional short-cut; at each recursive call,
    check to see if the elements in this subset are already
    ordered.  If so, no further comparisons are needed; the
    sub-array can just be copied.  The array must be copied rather
    than assigned otherwise sister calls in the recursion might
    get out of sinc.  When the number of elements is three they
    are partitioned so that the first set, [low, mid), has one
    element and and the second, [mid, high), has two. We skip the
    optimisation when the number of elements is three or less as
    the first compare in the normal merge will produce the same
    sequence of steps. This optimisation seems to be worthwhile
    for partially ordered lists but some analysis is needed to
    find out how the performance drops to Nlog(N) as the initial
    order diminishes - it may drop very quickly.  */

    if (high - low >= 4 && adjustCompare(BaseTransformation.getInt(from, middle - 1), BaseTransformation.getInt(from, middle)) <= 0) {
      for (int i = low; i < high; i++) {
        to.set(i, from.get(i));
      }
      return;
    }

    // A normal merge.
    for (int i = low; i < high; i++) {
      if (q >= high || (p < middle && adjustCompare(BaseTransformation.getInt(from, p), BaseTransformation.getInt(from, q)) <= 0)) {
        to.set(i, from.get(p++));
      } else {
        to.set(i, from.get(q++));
      }
    }
  }

  private int adjustCompare(int r1, int r2) {
    return m_tm.compare(r1, r2, mColumnStateList);
  }

  private boolean mSortOnInsert = false;

  public void sortOnInsert() {
    mSortOnInsert = true;
  }

  public synchronized int insert(Object newObj) {
    super.insert(newObj);
    if (mSortOnInsert) sort();
    return findRow(newObj);
  }
}
