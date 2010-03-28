package com.jbidwatcher.util.queue;
/*
  File: Heap.java

  Originally written by Doug Lea and released into the public domain.
  This may be used for any purposes whatsoever without acknowledgment.
  Thanks for the assistance and support of Sun Microsystems Labs,
  and everyone contributing, testing, and using this code.

  History:
  Date       Who                What
  29Aug1998  dl               Refactored from BoundedPriorityQueue
*/

import java.util.*;

/**
 * A heap-based priority queue, without any concurrency control
 * (i.e., no blocking on empty/full states).
 * This class provides the data structure mechanics for BoundedPriorityQueue.
 * <p>
 * The class currently uses a standard array-based heap, as described
 * in, for example, Sedgewick's Algorithms text. All methods
 * are fully synchronized. In the future,
 * it may instead use structures permitting finer-grained locking.
 * <p>[<a href="http://gee.cs.oswego.edu/dl/classes/EDU/oswego/cs/dl/util/concurrent/intro.html"> Introduction to this package. </a>]
 **/

@SuppressWarnings({"JavaDoc"})
public class Heap  {
  protected Object[] mNodes;  // the tree nodes, packed into an array
  protected int mCount = 0;   // number of used slots
  protected final Comparator mCmp;  // for ordering

  /**
   * Create a Heap with the given initial capacity and comparator
   *
   * @param capacity
   * @param cmp
   *
   * @exception IllegalArgumentException if capacity less or equal to zero
   */
  public Heap(int capacity, Comparator cmp)
   throws IllegalArgumentException {
    if (capacity <= 0) throw new IllegalArgumentException();
    mNodes = new Object[capacity];
    mCmp = cmp;
  }

  /**
   * Create a Heap with the given capacity,
   * and relying on natural ordering.
   *
   * @param capacity
   */
  public Heap(int capacity) {
    this(capacity, null);
  }


/**
  * perform element comaprisons using comparator or natural ordering
  *
  * @param a
  * @param b
  *
  * @return
  */
  @SuppressWarnings({"unchecked"})
  protected int compare(Object a, Object b) {
    if (mCmp == null)
      return ((Comparable)a).compareTo(b);
    else
      return mCmp.compare(a, b);
  }

  // indexes of heap parents and children
  protected final int parent(int k) { return (k - 1) / 2;  }
  protected final int left(int k)   { return 2 * k + 1; }
  protected final int right(int k)  { return 2 * (k + 1); }

  /**
   * insert an element, resize if necessary
   *
   * @param x
   */
  public synchronized void insert(Object x) {
    if (mCount >= mNodes.length) {
      int newcap =  3 * mNodes.length / 2 + 1;
      Object[] newnodes = new Object[newcap];
      System.arraycopy(mNodes, 0, newnodes, 0, mNodes.length);
      mNodes = newnodes;
    }

    int k = mCount;
    ++mCount;
    while (k > 0) {
      int par = parent(k);
      if (compare(x, mNodes[par]) < 0) {
        mNodes[k] = mNodes[par];
        k = par;
      }
      else break;
    }
    mNodes[k] = x;
  }

  /**
   * Return and remove least element, or null if empty
   *
   * @return
   */
  public synchronized Object extract() {
    if (mCount < 1) return null;

    int k = 0; // take element at root;
    return extractElementAt(k);
  }

  private Object extractElementAt(int k) {
    Object least = mNodes[k];
    --mCount;
    Object x = mNodes[mCount];
    for (;;) {
      int l = left(k);
      if (l >= mCount)
        break;
      else {
        int r = right(k);
        int child = (r >= mCount || compare(mNodes[l], mNodes[r]) < 0)? l : r;
        if (compare(x, mNodes[child]) > 0) {
          mNodes[k] = mNodes[child];
          k = child;
        }
        else break;
      }
    }
    mNodes[k] = x;
    //  Prevent leakage...?
    mNodes[mCount] = null;
    return least;
  }

  /** Return least element without removing it, or null if empty **/
  public synchronized Object peek() {
    if (mCount > 0)
      return mNodes[0];
    else
      return null;
  }

  /** Return number of elements **/
  public synchronized int size() {
    return mCount;
  }

  /** remove all elements **/
  public synchronized void clear() {
    //  Clear out the array, to avoid keeping references around.
    for(int i=0; i< mCount; i++) mNodes[i] = null;
    mCount = 0;
  }

  public List getUnsorted() {
    List rval = Arrays.asList(mNodes);
    return rval.subList(0, mCount);
  }

  public List getSorted() {
    List base = getUnsorted();
    Collections.sort(base);
    return base;
  }

  public boolean erase(Object o) {
    for (int i = 0; i < mNodes.length; i++) {
      if(mNodes[i] == o) {
        extractElementAt(i);
        return true;
      }
    }

    return false;
  }
}
