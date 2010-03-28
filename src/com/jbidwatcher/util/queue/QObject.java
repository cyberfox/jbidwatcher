package com.jbidwatcher.util.queue;
/*
 * Copyright (c) 2000-2007, CyberFOX Software, Inc. All Rights Reserved.
 *
 * Developed by mrs (Morgan Schweers)
 */

public class QObject {
  protected Object mData;
  protected String mLabel;

  public QObject(Object data, String label) {
    mData = data;
    mLabel = label;
  }

  public QObject() { }

//  public String toString() {
//    return "QObject{" +
//            "mData=" + mData +
//            ", mLabel='" + mLabel + '\'' +
//            '}';
//  }
//
  public Object getData() { return mData; }
  public void setData(Object newData) { mData = newData; }

  public String getLabel() { return mLabel; }
  public void setLabel(String label) { mLabel = label; }
}
