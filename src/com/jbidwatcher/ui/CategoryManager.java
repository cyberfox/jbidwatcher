package com.jbidwatcher.ui;

import javax.swing.*;
import java.awt.Component;

/**
 * Created by IntelliJ IDEA.
 * User: mrs
 * Date: 7/5/11
 * Time: 2:54 AM
 *
 * Trying to centralize access to 'what the current chosen category is' concepts, so it doesn't have to be tabs all the time.
 */
public class CategoryManager {
  public static String getCurrentTabTitle() {
    JTabbedPane tabbedPane = JTabManager.getInstance().getTabs();
    return tabbedPane.getTitleAt(tabbedPane.getSelectedIndex());
  }

  public static String getCategoryAt(int tabIndex) {
    JTabbedPane tabbedPane = JTabManager.getInstance().getTabs();
    return tabbedPane.getTitleAt(tabIndex);
  }

  public static void remove(Component removed) {
    JTabManager.getInstance().getTabs().remove(removed);
  }

  public static int getCurrentIndex() {
    return JTabManager.getInstance().getTabs().getSelectedIndex();
  }
}
