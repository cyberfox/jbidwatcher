package com.jbidwatcher.ui;
/*
 * Copyright (c) 2000-2007, CyberFOX Software, Inc. All Rights Reserved.
 *
 * Developed by mrs (Morgan Schweers)
 */

import com.jbidwatcher.platform.Platform;
import com.jbidwatcher.config.JConfig;
import com.jbidwatcher.JBidWatch;

import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import java.util.HashMap;

/**
 * The GUI Menu bar. This will create the menu bar for the application.
 *
 * @version $Revision: 1.38 $
 */
public class JBidMenuBar extends JMenuBar {
  protected static JBidMenuBar _instance;
  protected JMenuBar _menuBar;
  protected JMenu _fileMenu;
  protected JMenu _editMenu;
  protected JMenu _serverMenu;
  protected JMenu _auctionMenu;
  protected JMenu _helpMenu;
  protected ActionListener _actionDirector;

  protected void makeMenuItem(
        JMenu inMenu,
        String inName,
        String inActionCommand,
        int mnemonic,
        KeyStroke accelerator) {
    makeMenuItem(inMenu, inName, inActionCommand, mnemonic, accelerator, true);
  }

  protected void makeMenuItem(
        JMenu inMenu,
        String inName,
        String inActionCommand,
        int mnemonic,
        KeyStroke accelerator,
        boolean add) {

        JMenuItem constructItem = new JMenuItem();

        constructItem.setText(inName);
        constructItem.setActionCommand(inActionCommand);
        constructItem.addActionListener(_actionDirector);
        constructItem.setMnemonic(mnemonic);

        if (accelerator != null) {
            constructItem.setAccelerator(accelerator);
        } // end of if (accelerator != null)

        if(add) inMenu.add(constructItem);
    }

  protected void makeMenuItem(
        JMenu inMenu,
        String inName,
        int mnemonic,
        KeyStroke accelerator) {

        makeMenuItem(inMenu, inName, inName, mnemonic, accelerator, true);
    }


  protected void makeMenuItem(JMenu inMenu, String inName, String inActionCommand, char mnemonic) {
    JMenuItem constructItem = new JMenuItem();

    constructItem.setText(inName);
    constructItem.setActionCommand(inActionCommand);
    constructItem.addActionListener(_actionDirector);
    constructItem.setMnemonic(mnemonic);

    inMenu.add(constructItem);
  }

  protected void makeMenuItem(JMenu inMenu, String inName, char mnemonic) {
    makeMenuItem(inMenu, inName, inName, mnemonic);
  }

  protected void makeMenuItem(JMenu inMenu, String inName, int mnemonic) {
      makeMenuItem(inMenu, inName, inName, mnemonic, null);
  }


  protected void makeMenuItem(JMenu inMenu, String inName) {
    makeMenuItem(inMenu, inName, inName, '\0');
  }

  protected void establishFileMenu(JMenu inMenu) {
    makeMenuItem(
        inMenu,
        "Save auctions", "Save",
        KeyEvent.VK_S,
        KeyStroke.getKeyStroke(KeyEvent.VK_S, Toolkit.getDefaultToolkit().getMenuShortcutKeyMask()));

    makeMenuItem(inMenu, "Dump cache", "Dump", KeyEvent.VK_D,
        KeyStroke.getKeyStroke(KeyEvent.VK_D, Toolkit.getDefaultToolkit().getMenuShortcutKeyMask()));

    if(Platform.isMac()) {
      makeMenuItem(
          inMenu,
          "Configure",
          KeyEvent.VK_C,
          KeyStroke.getKeyStroke(KeyEvent.VK_COMMA, Toolkit.getDefaultToolkit().getMenuShortcutKeyMask()));
    } else {
      makeMenuItem(
          inMenu,
          "Configure",
          KeyEvent.VK_C,
          KeyStroke.getKeyStroke(KeyEvent.VK_C, Toolkit.getDefaultToolkit().getMenuShortcutKeyMask()));
    }

    makeMenuItem(inMenu, "Scripting Manager", "Scripting", 'M');
    makeMenuItem(inMenu, "Check For Updates", KeyEvent.VK_U);
    makeMenuItem(inMenu, "Clear deleted tracking", "Clear Deleted", KeyEvent.VK_D, KeyStroke.getKeyStroke(KeyEvent.VK_D, Toolkit.getDefaultToolkit().getMenuShortcutKeyMask()));
    if(!Platform.isMac()) inMenu.add(new JSeparator());
    makeMenuItem(
        inMenu,
        "Exit",
        "Exit",
        KeyEvent.VK_X,
        KeyStroke.getKeyStroke(KeyEvent.VK_Q, Toolkit.getDefaultToolkit().getMenuShortcutKeyMask()), !Platform.isMac());
  }

  protected void establishDebugMenu(JMenu inMenu) {
    makeMenuItem(inMenu, "Serialize");
    makeMenuItem(inMenu, "Deserialize");
    inMenu.add(new JSeparator());
  }

  protected void establishEditMenu(JMenu inMenu) {
    makeMenuItem(inMenu, "Paste Auction", "Paste", KeyEvent.VK_P,
                 KeyStroke.getKeyStroke(KeyEvent.VK_V, Toolkit.getDefaultToolkit().getMenuShortcutKeyMask()));
    JMenu copyMenu = new JMenu("Copy...");
    copyMenu.add(JContext.makeGeneralMenuItem("Information", "Copy")).addActionListener(_actionDirector);
    copyMenu.add(JContext.makeGeneralMenuItem("URL", "CopyURL")).addActionListener(_actionDirector);
    copyMenu.add(JContext.makeGeneralMenuItem("Auction Id", "CopyID")).addActionListener(_actionDirector);
    inMenu.add(copyMenu);
    //makeMenuItem(inMenu, "Copy Auction URL", "CopyURL", KeyEvent.VK_C,
    //             KeyStroke.getKeyStroke(KeyEvent.VK_C, Toolkit.getDefaultToolkit().getMenuShortcutKeyMask()));
    //makeMenuItem(inMenu, "Copy Auction ID", "CopyID", 'I');
    inMenu.add(new JSeparator());
    makeMenuItem(inMenu, "Find", "Search", KeyEvent.VK_F,
                 KeyStroke.getKeyStroke(KeyEvent.VK_F, Toolkit.getDefaultToolkit().getMenuShortcutKeyMask()));
    inMenu.add(new JSeparator());
    makeMenuItem(inMenu, "Set Background Color", "Set Background Color", 'B');
    makeMenuItem(inMenu, "Show/Hide Toolbar", "Toolbar", 'T');
  }

  protected void establishServerMenu(JMenu inMenu) {
    String doTimeSync = JConfig.queryConfiguration("timesync.enabled", "true");
    
    if(doTimeSync.equals("true"))
      makeMenuItem(inMenu, "Synchronize time", "Resync", 'R');
    makeMenuItem(inMenu, "Update auctions", "UpdateAll", 'U');
    makeMenuItem(inMenu, "Stop Activity", "StopUpdating", KeyEvent.VK_S,
                 KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, Toolkit.getDefaultToolkit().getMenuShortcutKeyMask()));
   if(doTimeSync.equals("true"))
      makeMenuItem(inMenu, "Time Information", "Show Time Info", 'T');
  }

  protected void establishAuctionMenu(JMenu inMenu) {
    //  The mac doesn't have an 'INSERT' key.  I suppose 'Overwrite'
    //  mode is too complex?  Frustration abounds, as CMD-A is 'select
    //  all'.  We're going with CMD-I to mirror 'Insert'.
    if(Platform.isMac()) {
      makeMenuItem(inMenu, "Add", KeyEvent.VK_A,
                   KeyStroke.getKeyStroke(KeyEvent.VK_I, Toolkit.getDefaultToolkit().getMenuShortcutKeyMask()));
    } else {
      makeMenuItem(inMenu, "Add", KeyEvent.VK_A,
                   KeyStroke.getKeyStroke(KeyEvent.VK_INSERT, 0));
    }

    //  Require 'CMD-Del' for the Mac, because otherwise it catches
    //  the 'Del' operation in the middle of text entry.  (D'oh!)
    if(Platform.isMac()) {
      makeMenuItem(inMenu, "Delete", KeyEvent.VK_D,
                   KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, Toolkit.getDefaultToolkit().getMenuShortcutKeyMask()));
    } else {
      makeMenuItem(inMenu, "Delete", KeyEvent.VK_D,
                   KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0));
    }

    inMenu.add(new JSeparator());
    makeMenuItem(inMenu, "Snipe", 'S', KeyStroke.getKeyStroke(KeyEvent.VK_E, Toolkit.getDefaultToolkit().getMenuShortcutKeyMask()));
    makeMenuItem(inMenu, "Multiple Snipe", 'i');
    makeMenuItem(inMenu, "Cancel snipe", 'C');
    inMenu.add(new JSeparator());
    makeMenuItem(inMenu, "Bid", 'B');
    makeMenuItem(inMenu, "Buy", 'y');
    inMenu.add(new JSeparator());
    makeMenuItem(inMenu, "Update", 'U');
    makeMenuItem(inMenu, "Show Information", "Information", 'I');
//    makeMenuItem(inMenu, "Show Last Error Page", "ShowError", 'l');
    makeMenuItem(inMenu, "Show in browser", "Browse", 'b', KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, Toolkit.getDefaultToolkit().getMenuShortcutKeyMask()));
//    makeMenuItem(inMenu, "Show status", "Status", 't');
    inMenu.add(new JSeparator());
    makeMenuItem(inMenu, "Make comment", "Comment", 'M');
    makeMenuItem(inMenu, "View Comment", 'V');
  }

  protected void establishHelpMenu(JMenu inMenu) {
    makeMenuItem(inMenu, "Explain the colors and icons", 'E');
    makeMenuItem(inMenu, "FAQ", 'F', KeyStroke.getKeyStroke(KeyEvent.VK_F1, 0));
    inMenu.add(new JSeparator());
    makeMenuItem(inMenu, "About", 'A');
  }

  private static HashMap<String, JBidMenuBar> _frameMenus = new HashMap<String, JBidMenuBar>(10);

  /**
   * @brief Return the menu instance associated with a specific frame name.
   *
   * @param inAction - The action underlying the menu, in case it has to be created.
   * @param frameName - The name of the frame to associated the menu bar with.
   *
   * @return - A menu bar usable with said frame.
   */
  protected static JBidMenuBar getFrameInstance(ActionListener inAction, String frameName) {
    JBidMenuBar retInstance = _frameMenus.get(frameName);
    if(retInstance == null) {
      if(inAction == null) {
        throw new RuntimeException("JBidMenuBar.getInstance(null) called when no matching instance \"" + frameName + "\" yet created!");
      }
      retInstance = new JBidMenuBar(inAction);
      _frameMenus.put(frameName, retInstance);
    }

    if(_instance == null) _instance = retInstance;

    return retInstance;
  }

  /**
   * @brief Return the 'global instance' of the menu bar.
   *
   * @param inAction - The action handler to use under the menu bar.
   *
   * @return - The global instance of the menu bar.
   */
  public static JBidMenuBar getInstance(ActionListener inAction) {
    return getInstance(inAction, null);
  }

  /**
   * @brief Get an instance of the menu bar for use with a given frame name.
   *
   * @param inAction - The action to use underneath the menu bar.
   * @param frameName - The name of the frame this menu bar is to be associated with.
   *
   * @return - A menu bar, either the 'global instance', or a specific one for the frame.
   */
  public static JBidMenuBar getInstance(ActionListener inAction, String frameName) {
    if(inAction == null && _instance == null && _frameMenus == null) {
      throw new RuntimeException("JBidMenuBar.getInstance(null, null) called when no instance yet created!");
    }

    if(frameName != null) {
      return getFrameInstance(inAction, frameName);
    }

    //  Return the 'global instance'.
    if(_instance == null) {
      _instance = new JBidMenuBar(inAction);
    }
    return _instance;
  }

  private JBidMenuBar(ActionListener inAction) {
    _actionDirector = inAction;
    _fileMenu = new JMenu("File");
    _fileMenu.setMnemonic('F');
    _editMenu = new JMenu("Edit");
    _editMenu.setMnemonic('E');
    _serverMenu = new JMenu("Servers");
    _serverMenu.setMnemonic('S');
    _auctionMenu = new JMenu("Auction");
    _auctionMenu.setMnemonic('A');
    _helpMenu = new JMenu("Help");
    _helpMenu.setMnemonic('H');

    establishFileMenu(_fileMenu);
    establishEditMenu(_editMenu);
    establishServerMenu(_serverMenu);
    establishAuctionMenu(_auctionMenu);
    establishHelpMenu(_helpMenu);

    add(_fileMenu);
    add(_editMenu);
    add(_serverMenu);
    add(_auctionMenu);
    add(_helpMenu);
  }
}
