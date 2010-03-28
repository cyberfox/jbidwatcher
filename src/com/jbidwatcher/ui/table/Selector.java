package com.jbidwatcher.ui.table;
/*
 * Copyright (c) 2000-2007, CyberFOX Software, Inc. All Rights Reserved.
 *
 * Developed by mrs (Morgan Schweers)
 */

import javax.swing.*;

/**
 * Created by IntelliJ IDEA.
 * User: mrs
 * Date: Dec 1, 2004
 * Time: 4:37:37 PM
 *
 * Abstraction for selecting entries in a table.
 */
public interface Selector {
  public boolean select(JTable inTable);
}
