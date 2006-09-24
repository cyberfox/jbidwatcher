package com.jbidwatcher.ui;
/*
 * Copyright (c) 2000-2005 CyberFOX Software, Inc. All Rights Reserved.
 *
 * This library is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Library General Public License as published
 * by the Free Software Foundation; either version 2 of the License, or (at
 * your option) any later version.
 *
 * This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Library General Public License for more
 * details.
 *
 * You should have received a copy of the GNU Library General Public License
 * along with this library; if not, write to the
 *  Free Software Foundation, Inc.
 *  59 Temple Place
 *  Suite 330
 *  Boston, MA 02111-1307
 *  USA
 */

import com.jbidwatcher.ui.ColumnStateList;

import java.util.List;
import java.util.ArrayList;

/**
 * Created by IntelliJ IDEA.
 * User: Administrator
 * Date: Dec 2, 2004
 * Time: 7:54:23 PM
 * To change this template use File | Settings | File Templates.
 */
public class SortTransformation extends Transformation {
  private ColumnStateList mColumnStateList;

  SortTransformation(BaseTransformation chain) {
    super(chain);
  }

  public synchronized void sort() {
    if(mColumnStateList == null || mColumnStateList.size() == 0)
      return;

    shuttlesort(new ArrayList(m_row_xform), m_row_xform, 0, m_row_xform.size());
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
  private void shuttlesort(List from, List to, int low, int high) {
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

    if (high - low >= 4 && adjustCompare(getInt(from, middle - 1), getInt(from, middle)) <= 0) {
      for (int i = low; i < high; i++) {
        to.set(i, (Integer)from.get(i));
      }
      return;
    }

    // A normal merge.
    for (int i = low; i < high; i++) {
      if (q >= high || (p < middle && adjustCompare(getInt(from, p), getInt(from, q)) <= 0)) {
        to.set(i, from.get(p++));
      } else {
        to.set(i, from.get(q++));
      }
    }
  }

  private int adjustCompare(int r1, int r2) {
    return m_tm.compare(r1, r2, mColumnStateList);
  }

  public synchronized int insert(Object newObj) {
    super.insert(newObj);
    sort();
    return findRow(newObj);
  }
}
