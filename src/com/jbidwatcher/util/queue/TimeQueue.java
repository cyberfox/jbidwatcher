package com.jbidwatcher.util.queue;

/*
 * Copyright (c) 2000-2007, CyberFOX Software, Inc. All Rights Reserved.
 *
 * Developed by mrs (Morgan Schweers)
 */

import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: mrs
 * Date: May 10, 2005
 * Time: 11:07:18 PM
 *
 */
public class TimeQueue {
  private Heap m_heap = new Heap(11);

  public boolean erase(QObject tqo) {
    return m_heap.erase(tqo);
  }

  public class QObject implements Comparable {
    private long m_when;
    private Object m_event;

    public QObject(long when, Object o) {
        m_when = when;
        m_event = o;
    }

    public boolean less(QObject o) {
      return m_when < o.m_when;
    }

    public Object getEvent() { return m_event; }

    public int compareTo(Object o) {
        QObject cmp = (QObject) o;
        if(m_when < cmp.m_when) return -1;
        if(m_when > cmp.m_when) return 1;

        return 0;
    }

    public long getTime() {
      return m_when;
    }
  }

  public void addEvent(long when, Object o) {
      m_heap.insert(new QObject(when, o));
  }

  public List getSorted() {
    return m_heap.getSorted();
  }

  public List getUnsorted() {
    return m_heap.getUnsorted();
  }

  public Object getAnyLessThan(long when) {
    QObject cmp = (QObject)m_heap.peek();
    if(cmp != null && cmp.m_when < when) {
      cmp = (QObject)m_heap.extract();
      return cmp.m_event;
    }
    return null;
  }
}
