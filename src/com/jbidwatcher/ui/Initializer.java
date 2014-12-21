package com.jbidwatcher.ui;

import javax.swing.*;
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;
import java.awt.Component;

/**
 * Created by IntelliJ IDEA.
 * User: Morgan
 * Date: Jun 30, 2008
 * Time: 2:07:40 AM
 *
 * A simple initializer to inject particular objects into the AuctionListHolder's use.
 */
public class Initializer {
  private static JTabPopupMenu sMenu;

  public static void setup(JTabManager tabManager, ListManager listManager, PopupMenuFactory menuFactory) {
    JTabbedPane tabs = tabManager.getTabs();
    sMenu = menuFactory.create(tabs, null);
    ActionListener cornerButtonListener = new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        JMenu bangMenu = sMenu.getCustomizeMenu();
        bangMenu.getPopupMenu().show((Component) e.getSource(), 0, 0);
      }
    };
    AuctionListHolder.setCornerButtonListener(cornerButtonListener);

    JBidContext tableContextMenu = new JBidTableContext(tabManager, listManager);
    JBidContext frameContextMenu = new JBidFrameMouse(tabManager, listManager);

    AuctionListHolder.setFrameContext(frameContextMenu);
    AuctionListHolder.setTableContext(tableContextMenu);
  }
}
