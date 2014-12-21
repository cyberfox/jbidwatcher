package com.jbidwatcher.ui;

import com.jbidwatcher.auction.Category;
import com.jbidwatcher.auction.AuctionEntry;
import com.jbidwatcher.util.queue.MQFactory;
import com.jbidwatcher.util.queue.PlainMessageQueue;
import com.jbidwatcher.util.config.JConfig;

import javax.swing.*;
import java.awt.event.MouseEvent;
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;
import java.util.List;
import java.util.ArrayList;

/**
 * Created by IntelliJ IDEA.
 * User: Morgan
 * Date: Jun 30, 2008
 * Time: 5:01:03 PM
 *
 * The menuing and such for the table-specific context menu.
 */
public class JBidTableContext extends JBidContext {
  private final ListManager listManager;
  private final JTabManager tabManager;
  private JMenu tabMenu = null;

  public JBidTableContext(JTabManager tabManager, ListManager listManager) {
    this.tabManager = tabManager;
    this.listManager = listManager;
    buildMenu(localPopup);
  }

  protected void DoAction(Object src, String actionString, Object whichAuction) {
    if(actionString.startsWith("BT-")) {
      actionString = actionString.substring(3);
    }
    ((PlainMessageQueue)MQFactory.getConcrete("user")).enqueueObject(new ActionTriple(src, actionString, whichAuction));
  }

  protected void buildMenu(JPopupMenu menu) {
    menu.add(makeMenuItem("Snipe")).addActionListener(this);
    menu.add(makeMenuItem("Cancel Snipe")).addActionListener(this);
    menu.add(new JPopupMenu.Separator());

    menu.add(makeMenuItem("Bid")).addActionListener(this);
    menu.add(makeMenuItem("Buy")).addActionListener(this);
    menu.add(new JPopupMenu.Separator());
    if(JConfig.debugging() && JConfig.queryConfiguration("debug.uber", "false").equals("true")) {
      menu.add(makeMenuItem("Mark as Won")).addActionListener(this);
      menu.add(new JPopupMenu.Separator());
    }

    menu.add(makeMenuItem("Update Auction", "Update")).addActionListener(this);
    menu.add(makeMenuItem("Show Information", "Information")).addActionListener(this);
    menu.add(makeMenuItem("Show In Browser", "Browse")).addActionListener(this);
    //menu.add(makeMenuItem("Add Up Prices", "Sum")).addActionListener(this);
    menu.add(new JPopupMenu.Separator());
    menu.add(makeMenuItem("Set Shipping", "Shipping")).addActionListener(this);
    menu.add(new JPopupMenu.Separator());

    tabMenu = new JMenu("Send To");
    menu.add(tabMenu);
    JMenu comment = new JMenu("Comment");
    comment.add(makeMenuItem("Add", "Add Comment")).addActionListener(this);
    comment.add(makeMenuItem("View", "View Comment")).addActionListener(this);
    comment.add(makeMenuItem("Remove", "Remove Comment")).addActionListener(this);
    menu.add(comment);
    JMenu advanced = new JMenu("Advanced");
    advanced.add(makeMenuItem("Show Last Error", "ShowError")).addActionListener(this);
    advanced.add(makeMenuItem("Mark As Not Ended", "NotEnded")).addActionListener(this);
    if(JConfig.queryConfiguration("my.jbidwatcher.id") != null) {
      advanced.add(makeMenuItem("Report a problem with this item", "Report")).addActionListener(this);
    }
    menu.add(advanced);
    menu.add(new JPopupMenu.Separator());

    menu.add(makeMenuItem("Delete")).addActionListener(this);
  }

  protected void beforePopup(JPopupMenu jp, MouseEvent e) {
    ActionListener tabActions = new ActionListener() {
      public void actionPerformed(ActionEvent action) {
        String toTab = action.getActionCommand();
        DoSendTo(toTab);
      }
    };
    super.beforePopup(jp, e);

    if (tabMenu != null) {
      tabMenu.removeAll();

      JTabbedPane tabbedPane = tabManager.getTabs();
      String currentTitle = tabbedPane.getTitleAt(tabbedPane.getSelectedIndex());
      // shows the list of tabs in the same order as the tabs
      List<String> tabs = listManager.allCategories();
      if (tabs == null) {
        tabMenu.setEnabled(false);
      } else {
        tabs.remove("selling");
        tabs.remove(currentTitle);
        tabMenu.setEnabled(true);
        for (String tab : tabs) {
          if(tab != null) tabMenu.add(makeMenuItem(tab)).addActionListener(tabActions);
        }
      }
    }

    /**
     * This sucks.  I need to push the generic code up, and leave the auction-specific code here, or
     * somehow move all the non-auction-specific functionality to its own class.  This code is broken,
     * at the least because it has to use 'instanceof' to work.
     */
    Object resolvedObject = resolvePoint();
    AuctionEntry ae = null;

    if (resolvedObject != null && resolvedObject instanceof AuctionEntry) {
      ae = (AuctionEntry) resolvedObject;
    }

    int[] rowList = getPossibleRows();

    if (rowList != null && rowList.length != 0) {
      if (rowList.length == 1) {
        Object firstSelected = getIndexedEntry(rowList[0]);
        if (firstSelected != null && firstSelected instanceof AuctionEntry) {
          ae = (AuctionEntry) firstSelected;
        }
      } else {
        ae = null;
      }
    }

    //  Ignored if it wasn't renamed, but otherwise always restore to 'known state'.
    rename("Multisnipe", "Snipe");
    rename("Edit", "Add");               // Comment

    if (ae != null) {
      if (ae.getComment() == null) {
        disable("View");
        disable("Remove");
      } else {
        rename("Add", "Edit");
      }
      if (!ae.isSniped()) disable("Cancel Snipe");
      if (!ae.isComplete()) {
        disable("Complete");
        disable("Mark As Not Ended");
      } else {
        enable("Mark As Not Ended");
      }

      if (ae.isSeller() || ae.isComplete()) {
        disable("Buy");
        disable("Bid");
        disable("Snipe");
      }

      if (ae.isFixed()) {
        disable("Bid");
        disable("Snipe");
      }

      if (!ae.isFixed() && ae.getBuyNow().isNull()) {
        disable("Buy");
      }
    }

    if (rowList != null && rowList.length > 1) {
      disable("Bid");
      disable("Buy");
      disable("Show Last Error");
      disable("Set Shipping");
      disable("Add");
      disable("View");
      disable("Remove");

      boolean anySniped = false;
      boolean anyFixed = false;
      boolean anyEnded = false;
      boolean anyCurrent = false;
      for (int aRowList : rowList) {
        Object line = getIndexedEntry(aRowList);
        AuctionEntry step = (AuctionEntry) line;
        if (step.isSniped()) anySniped = true;
        if (step.isFixed()) anyFixed = true;
        if (step.isComplete()) anyEnded = true;
        if (!step.isComplete()) anyCurrent = true;
      }

      if (!anySniped) disable("Cancel Snipe");
      if (anyFixed || anyEnded) disable("Snipe");
      if (!anyCurrent) enable("Complete");
      if (anyEnded) enable("Mark As Not Ended");
      else disable("Mark As Not Ended");
      rename("Snipe", "Multisnipe");
    }

    if (ae == null || ae.getErrorPage() == null) {
      disable("Show Last Error");
    }
  }

  private void DoSendTo(String tab) {
    int[] rowList = getPossibleRows();

    if (rowList.length == 0) {
      JOptionPane.showMessageDialog(null, "No auctions selected to move!", "Error moving listings", JOptionPane.PLAIN_MESSAGE);
      return;
    }

    Category c = Category.findFirstByName(tab);

    if (c == null) {
      JOptionPane.showMessageDialog(null, "Cannot locate that tab, something has gone wrong.\nClose and restart JBidwatcher.", "Error moving listings", JOptionPane.PLAIN_MESSAGE);
      return;
    }

    //  Build a temporary table, because the items will vanish out of
    //  the table when we start refiltering them, and that will mess
    //  everything up.
    ArrayList<AuctionEntry> tempTable = new ArrayList<AuctionEntry>(rowList.length);
    for (int aRowList : rowList) {
      AuctionEntry moveEntry = (AuctionEntry) getIndexedEntry(aRowList);
      tempTable.add(moveEntry);
    }

    //  Now move all entries in the temporary table to the new tab.
    for (AuctionEntry moveEntry : tempTable) {
      moveEntry.setCategory(tab);
      MQFactory.getConcrete("redraw").enqueue(moveEntry.getIdentifier());
    }
  }
}
