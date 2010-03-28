package com.jbidwatcher.util.queue;
/*
 * Copyright (c) 2000-2007, CyberFOX Software, Inc. All Rights Reserved.
 *
 * Developed by mrs (Morgan Schweers)
 */

public class DropQObject extends QObject {
  private boolean mInteractive;

  public DropQObject() { super(); }

  public DropQObject(String data, String label) {
    super(data, label);
    mInteractive = false;
  }

  public DropQObject(String data, String label, boolean isInteractive) {
    super(data, label);
    mInteractive = isInteractive;
  }

  public boolean isInteractive() { return mInteractive; }
  public void setInteractive(boolean interactive) { mInteractive = interactive; }
}
