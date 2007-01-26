package com.jbidwatcher.queue;
/*
 * Copyright (c) 2000-2007, CyberFOX Software, Inc. All Rights Reserved.
 *
 * Developed by mrs (Morgan Schweers)
 */

public class QObject {
  Object m_data;
  String m_label;

  public QObject(Object data, String label) {
    m_data = data;
    m_label = label;
  }

  public String toString() {
    return "QObject{" +
            "m_data=" + m_data +
            ", m_label='" + m_label + '\'' +
            '}';
  }

  public Object getData() { return m_data; }
  public String getLabel() { return m_label; }
}
