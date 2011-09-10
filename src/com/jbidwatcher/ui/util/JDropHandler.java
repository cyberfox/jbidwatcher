package com.jbidwatcher.ui.util;
/*
 * Copyright (c) 2000-2007, CyberFOX Software, Inc. All Rights Reserved.
 *
 * Developed by mrs (Morgan Schweers)
 */

import java.awt.Point;

public interface JDropHandler {
  void receiveDropString(StringBuffer dropped, Point location);
}
