package com.jbidwatcher.ui;

import com.jbidwatcher.util.queue.MQFactory;

import javax.swing.*;
import java.awt.Component;

/**
 * Created by IntelliJ IDEA.
 * User: mrs
 * Date: 1/23/12
 * Time: 5:15 PM
 *
 * Provides a subclass of JTabbedPane that has a '+' at the end of the list of tabs, unselectable, but when you click on it triggers
 * an 'Add New Tab' dialog.
 */
class PlusTabbedPane extends JTabbedPane {
  private int plusIndex = -1;

  private void addPlusButton() {
    super.addTab("+", null);
    plusIndex = getTabCount() - 1;
  }

  @Override
  public void removeTabAt(int index) {
    if(index != plusIndex) {
      super.removeTabAt(index);
      plusIndex--;
      if (getSelectedIndex() == plusIndex) {
        setSelectedIndex(plusIndex-1);
      }
    }
  }

  @Override
  public void addTab(String title, Component component) {
    if (plusIndex != -1) super.removeTabAt(plusIndex);
    super.addTab(title, component);
    addPlusButton();
  }

  @Override
  public void setSelectedIndex(int index) {
    if(index != plusIndex) super.setSelectedIndex(index);
    else {
      MQFactory.getConcrete("tab_menu").enqueue("Add Tab");
    }
  }
}
