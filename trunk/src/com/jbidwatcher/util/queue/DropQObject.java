package com.jbidwatcher.util.queue;
/*
 * Copyright (c) 2000-2007, CyberFOX Software, Inc. All Rights Reserved.
 *
 * Developed by mrs (Morgan Schweers)
 */

public class DropQObject extends QObject {
  private boolean m_interactive;

  public DropQObject(String data, String label) {
    super(data, label);
    m_interactive = false;
  }

  public DropQObject(String data, String label, boolean isInteractive) {
    super(data, label);
    m_interactive = isInteractive;
  }

  public boolean isInteractive() { return m_interactive; }
}
