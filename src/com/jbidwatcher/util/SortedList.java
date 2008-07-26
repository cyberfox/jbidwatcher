package com.jbidwatcher.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;

/**
 * An ArrayList that further guarantees that its iterator will traverse the set
 * in ascending element order, sorted according to the natural ordering of its
 * elements (see Comparable).
 * All elements inserted into a SortedList must implement the Comparable
 * interface. Furthermore, all such elements must be mutually comparable:
 * e1.compareTo(e2) (or comparator.compare(e1, e2)) must not throw a
 * ClassCastException for any elements e1 and e2 in the sorted ArrayList.
 * Attempts to add an object violating these restrictions will throw a
 * ClassCastException.
 * Duplicate elements can optionally be allowed in this ArrayList.
 *
 * @author Sean Connolly
 */
public class SortedList<E> extends ArrayList<E> {

    boolean duplicates = false;

    /**
     * Constructs an empty ArrayList with the specified initial capacity.
     *
     * @param   initialCapacity   the initial capacity of the list.
     * @exception IllegalArgumentException if the specified initial capacity
     *            is negative
     */
    public SortedList(int initialCapacity) {
        super(initialCapacity);
    }

    /**
     * Constructs an empty ArrayList with an initial capacity of ten.
     */
    public SortedList() {
        this(10);
    }

    /**
     * Constructs an ArrayList containing the elements of the specified
     * collection, as per the element's natural ordering. The
     * <tt>SortedList</tt> instance has an initial capacity of
     * 110% the size of the specified collection.
     *
     * @param c the collection whose elements are to be placed into this list.
     * @throws NullPointerException if the specified collection is null.
     */
    public SortedList(Collection<? extends E> c) {
        // Allow 10% room for growth
        super((int) Math.min((c.size() * 110L) / 100, Integer.MAX_VALUE));
        this.addAll(c);
    }

    /**
     * Adds the specified element to this list as per the element's natural
     * ordering.
     *
     * @param o element to be appended to this list.
     * @return <tt>true</tt> (as per the general contract of Collection.add).
     * @throws ClassCastException if the specified element is not of type
     * Comparable.
     */
    @Override
    public boolean add(Object o) {
        if (o == null) {
            return false;
        } else if (!(o instanceof Comparable)) {
            // Must be comparable to be sorted.
            throw new ClassCastException("All objects added to " +
                    this.getClass().getName() +
                    " must implement the Comparable interface.");
        } else if (!duplicates && this.contains(o)) {
            // Fail if duplicates are not allowed but this object is already
            // in the ArrayList.
            return false;
        } else if (this.isEmpty()) {
            // The ArrayList contains no other elements, order doesn't matter
            // yet.
            return super.add((E) o);
        }
        // Find the index where this element belongs according to the natural
        // ordering of the objects.
        for (int i = 0; i < this.size(); ++i) {
            Comparable front = (Comparable) this.get(i);
            Comparable c = (Comparable) o;
            if (i != this.size() - 1) {
                Comparable back = (Comparable) this.get(i + 1);
                if (c.compareTo(front) > 0 && c.compareTo(back) < 0) {
                    this.add(i + 1, (E) o);
                    return true;
                } else if (duplicates && c.equals(front) && c.compareTo(back) < 0) {
                    this.add(i + 1, (E) o);
                    return true;
                }
            } else {
                // This index is the end of the ArrayList..
                if (c.compareTo(front) < 0) {
                    // Occurs when there is only one object in the ArrayList.
                    this.add(i, (E) o);
                    return true;
                } else if ((duplicates && c.compareTo(front) == 0) ||
                        c.compareTo(front) > 0) {
                    // It is the greatest object yet, add it to the end of the
                    // list.
                    return super.add((E) o);
                } else {
                    // Occurs when this object is a duplicate of the last object
                    // in the ArrayList and duplicates are not allowed.
                    return false;
                }
            }
        }
        return false;
    }

    /**
     * Appends all of the elements in the specified Collection to this
     * ArrayList, as per their natural ordering. The behavior of this operation
     * is undefined if the specified Collection is modified while the operation
     * is in progress.  (This implies that the behavior of this call is
     * undefined if the specified Collection is this ArrayList, and this
     * ArrayList is nonempty.)
     *
     * @param c the elements to be inserted into this ArrayList.
     * @return <tt>true</tt> if this ArrayList changed as a result of the call.
     * @throws NullPointerException if the specified collection is null.
     */
    @Override
    public boolean addAll(Collection<? extends E> c) {
        int sizePrior = this.size();
        Iterator i = c.iterator();
        while (i.hasNext()) {
            this.add(i.next());
        }
        return this.size() != sizePrior;
    }

    /**
     * Set duplicates allowed.
     *
     * @param duplicates <tt>true</tt> if duplicates are to be allowed, <tt>false</tt>
     * otherwise.
     */
    public void allowDuplicates(boolean duplicates) {
        this.duplicates = duplicates;
    }

    /**
     * Query duplicates allowed.
     *
     * @return <tt>true</tt> if duplicates are to be allowed, <tt>false</tt>
     * otherwise.
     */
    public boolean duplicatesAllowed() {
        return this.duplicates;
    }
}