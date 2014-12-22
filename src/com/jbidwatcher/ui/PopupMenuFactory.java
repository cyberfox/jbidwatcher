package com.jbidwatcher.ui;

import javax.annotation.Nullable;
import javax.swing.*;

/**
 * Created by mrs on 12/20/14.
 */
public interface PopupMenuFactory {
  JTabPopupMenu create(JTabbedPane inTabs, @Nullable JPopupMenu popup);
}
