package com.jbidwatcher.ui;
/*
 * Copyright (c) 2000-2007, CyberFOX Software, Inc. All Rights Reserved.
 *
 * Developed by mrs (Morgan Schweers)
 */

import com.jbidwatcher.ui.config.JConfigFrame;
import com.jbidwatcher.ui.util.OptionUI;
import com.jbidwatcher.ui.util.JBidFrame;
import com.jbidwatcher.ui.table.TableColumnController;
import com.jbidwatcher.util.config.ErrorManagement;
import com.jbidwatcher.auction.FilterManager;

import java.awt.event.*;
import java.awt.*;
import javax.swing.*;
import javax.swing.event.PopupMenuListener;
import javax.swing.event.PopupMenuEvent;
import java.util.*;
import java.util.List;

public class JTabPopupMenu extends JContext {
  private JTabbedPane _myTabs = null;
  private JMenu customize = null;
  private JMenuItem _print = null;
  private JMenuItem _export = null;
  private JMenu _deleteSubmenu = null;
  private Map<String, JCheckBoxMenuItem> menuItemMap = new TreeMap<String, JCheckBoxMenuItem>();
  private JMenuItem _properties = null;

  /**
   * @brief Make a small menu for tabs.
   *
   * @return The pop-up menu to be displayed on 'context menu' at any of the tabs.
   * @noinspection StringContatenationInLoop
   */
  private JPopupMenu makeTabMenu() {
    JPopupMenu myPopup = new JPopupMenu();

    customize = new JMenu("Custom Columns");

    customize.getPopupMenu().addPopupMenuListener(new PopupMenuListener() {
      public void popupMenuWillBecomeVisible(PopupMenuEvent e) { prepCustomColumnMenu(null); }
      public void popupMenuWillBecomeInvisible(PopupMenuEvent e) { }
      public void popupMenuCanceled(PopupMenuEvent e) { }
    });

    myPopup.add(makeMenuItem("Add Tab")).addActionListener(this);
    _deleteSubmenu = new JMenu("Delete");
    _deleteSubmenu.add(makeMenuItem("Just Tab")).addActionListener(this);
    _deleteSubmenu.add(makeMenuItem("Tab & All Entries")).addActionListener(this);
    myPopup.add(_deleteSubmenu);
    myPopup.add(customize).addActionListener(this);
    //myPopup.add(makeMenuItem("+/- Comment")).addActionListener(this);
    myPopup.add(_print = makeMenuItem("Print")).addActionListener(this);
    myPopup.add(_export = makeMenuItem("Export")).addActionListener(this);
    myPopup.add(_properties = makeMenuItem("Properties")).addActionListener(this);

    return myPopup;
  }

  /**
   * @brief Use reflection to determine if we have an indexAtLocation
   * function, and always allow them to TRY to delete, if we don't.
   * If we do, figure if it's the bottom three tabs (current,
   * completed, selling) we don't want to allow delete.
   *
   * @param inPopup - The pop-up menu that is going to be displayed.
   * @param e - The event that occurred (a context-operation).
   */
  protected void beforePopup(JPopupMenu inPopup, MouseEvent e) {
    super.beforePopup(inPopup, e);
    int curIndex = _myTabs.indexAtLocation(e.getX(), e.getY());
    if (curIndex == -1) {
      customize.setEnabled(false);
      _deleteSubmenu.setEnabled(false);
      _print.setEnabled(false);
      ErrorManagement.logDebug("Whoops!  Click-point not found!");
    } else {
      _print.setEnabled(true);

      prepCustomColumnMenu(curIndex);

      if (curIndex < 3) {
        _deleteSubmenu.setEnabled(false);
      } else {
        _deleteSubmenu.setEnabled(true);
      }
    }
  }

  public void prepCustomColumnMenu(Integer tabIndex) {
    customize.removeAll();
    Collection<String> nameCollection = TableColumnController.getInstance().getColumnNames();
    ArrayList<String> sortedNames = new ArrayList<String>(nameCollection);
    Collections.sort(sortedNames);

    for (String s : sortedNames) {
      JCheckBoxMenuItem colMenuItem = new JCheckBoxMenuItem(s);
      colMenuItem.setActionCommand('~' + s);
      customize.add(colMenuItem).addActionListener(this);
      menuItemMap.put(s, colMenuItem);
    }

    if(tabIndex == null) tabIndex = _myTabs.getSelectedIndex();
    customize.setEnabled(true);
    String tabName = _myTabs.getTitleAt(tabIndex);
    uncheckAll();
    setColumnChecks(tabName);
  }

  public JMenu getCustomizeMenu() {
    return customize;
  }

  private void setColumnChecks(String tabName) {
    List<String> columns = FilterManager.getInstance().getColumns(tabName);
    for (String colName : columns) {
      JCheckBoxMenuItem jch = menuItemMap.get(colName);
      jch.setState(true);
    }
  }

  private void uncheckAll() {
    for (JCheckBoxMenuItem jch : menuItemMap.values()) {
      jch.setState(false);
    }
  }

  public void actionPerformed(ActionEvent ae) {
    super.actionPerformed(ae);
    DoAction(ae.getActionCommand(), _myTabs.getSelectedIndex());
  }

  protected JFrame propFrame = null;
  private TreeMap<String, JTabProperties> tabToProperties = null;

  protected JFrame getFrame(String tabName) {
    if(propFrame == null) propFrame = new JBidFrame("Tab Properties");
    if(tabToProperties == null) tabToProperties = new TreeMap<String, JTabProperties>();
    JTabProperties properties = tabToProperties.get(tabName);

    if(properties == null) {
      properties = new JTabProperties(tabName);
      tabToProperties.put(tabName, properties);
    }

    Container content = propFrame.getContentPane();
    content.removeAll();
    content.setLayout(new BorderLayout());
    content.add(properties, BorderLayout.CENTER);

    JPanel tmp = new JPanel();
    JButton ok = new JButton("OK");
    ok.addActionListener(properties);
    tmp.add(ok, BorderLayout.CENTER);
    content.add(tmp, BorderLayout.SOUTH);

    propFrame.addWindowListener(new JConfigFrame.IconifyingWindowAdapter(propFrame));
    propFrame.pack();
    propFrame.setResizable(false);
    properties.setFrame(propFrame);
    return propFrame;
  }

  /**
   * @brief Execute a given action, based on the string form of the action name.
   *
   * @param actionString - The command to do.
   * @param tabIndex - The tab to perform the action on.
   */
  protected void DoAction(String actionString, int tabIndex) {
    if(actionString.equals("Add Tab")) {
      OptionUI oui = new OptionUI();
      String result = oui.promptString(_myTabs.getComponentAt(tabIndex>=0?tabIndex:0), "Enter the name of the tab to add.  Prefer brevity.", "Add New Tab", "");

      if(result == null) return;
      result = result.trim();
      if(result.length() == 0) return;

      FilterManager.getInstance().addTab(result);
      return;
    }

    String tabName = _myTabs.getTitleAt(tabIndex);
    if(actionString.charAt(0) == '~') {
      boolean result = FilterManager.getInstance().toggleField(tabName, actionString.substring(1));
      if(tabToProperties != null) {
        JTabProperties properties = tabToProperties.get(tabName);
        if(properties != null) {
          properties.setColumnStatus(actionString.substring(1), result);
        }
      }
    }

    if(actionString.equals("Properties")) {
      JFrame jf = getFrame(tabName);
      jf.setState(Frame.NORMAL);
      jf.setVisible(true);
    }

    if(actionString.equals("Export")) {
      JFileChooser jfc = new JFileChooser();
      jfc.setApproveButtonText("Export");
      int result = jfc.showSaveDialog(null);
      switch(result) {
        case JFileChooser.APPROVE_OPTION:
          String fname = jfc.getSelectedFile().getAbsolutePath();
          if(!FilterManager.getInstance().exportTab(tabName, fname)) {
            JOptionPane.showMessageDialog(null, "Could not export tab [" + tabName + "].", "Export error", JOptionPane.PLAIN_MESSAGE);
          }
          return;
        case JFileChooser.ERROR_OPTION:
        case JFileChooser.CANCEL_OPTION:
        default:
          return;
      }
    }

    if(actionString.equals("Print")) {
      if(tabIndex == -1) {
        ErrorManagement.logDebug("Can't print unknown tab, must prompt...");
      } else {
        if(!FilterManager.getInstance().printTab(tabName)) {
          JOptionPane.showMessageDialog(null, "Could not print tab [" + tabName + "].", "Print error", JOptionPane.PLAIN_MESSAGE);
        }
      }
    }

    boolean eraseEntries = false;

    if(actionString.equals("Tab & All Entries")) {
      eraseEntries = true;
      actionString = "Just Tab";
    }

    if(actionString.equals("Just Tab")) {
      //  If we couldn't have figured out which tab, prompt for delete.
      if(tabIndex == -1) {
        ErrorManagement.logDebug("Prompting for Delete...\n");
      } else {
        ErrorManagement.logDebug("Deleting tab [" + tabName + "]...\n");
        if(!FilterManager.getInstance().deleteTab(tabName, eraseEntries)) {
          JOptionPane.showMessageDialog(null, "Could not delete tab [" + tabName + "].", "Tab deletion error", JOptionPane.PLAIN_MESSAGE);
        }
      }
    }
  }

  /**
   * @brief Construct a menu & listener to be used as a context menu
   * on the tabbed display.
   *
   * @param inTabs - The tab display to act as a context menu for.
   */
  public JTabPopupMenu(JTabbedPane inTabs) {
    _myTabs = inTabs;
    localPopup = makeTabMenu();
  }
}
