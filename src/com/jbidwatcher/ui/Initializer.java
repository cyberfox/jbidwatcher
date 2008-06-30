package com.jbidwatcher.ui;

import javax.swing.*;
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;

/**
 * Created by IntelliJ IDEA.
 * User: Morgan
 * Date: Jun 30, 2008
 * Time: 2:07:40 AM
 *
 * A simple initializer to inject particular objects into the AuctionListHolder's use.
 */
public class Initializer {
  private static JButton sCornerButton;

  public static void setup() {
    sCornerButton = new JButton("*");
    sCornerButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        JMenu bangMenu = JTabManager.getInstance().getCustomColumnMenu();
        bangMenu.getPopupMenu().show(sCornerButton, 0, 0);
      }
    });

    JBidContext tableContextMenu = new JBidMouse();
    JBidContext frameContextMenu = new JBidFrameMouse();

    AuctionListHolder.setFrameContext(frameContextMenu);
    AuctionListHolder.setTableContext(tableContextMenu);
    AuctionListHolder.setCornerButton(sCornerButton);
  }
}
