package com.jbidwatcher.ui;

import com.jbidwatcher.platform.Platform;
import com.jbidwatcher.JBidWatch;
import com.jbidwatcher.config.JConfig;
import com.jbidwatcher.config.JConfigTab;
import com.jbidwatcher.auction.server.AuctionServerManager;

import javax.swing.*;
import javax.swing.event.DocumentListener;
import javax.swing.event.DocumentEvent;
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;
import java.awt.*;

/**
 * Created by IntelliJ IDEA.
 * User: mrs
 * Date: Feb 4, 2008
 * Time: 2:08:05 AM
 *
 * Move the toolbar construction code out to its own class, so it's not cluttering up the JBidWatch class.
 */
public class JBidToolBar {
  private static final int SELECT_BOX_SIZE=20;
  private JLabel _headerStatus;
  private JPanel _bidBarPanel;
  private JBidMenuBar _bidMenu;
  private JTextField selectBox = new JTextField(SELECT_BOX_SIZE);
  private static JBidToolBar mInstance = null;

  /**
   * @brief Build the tool bar, with all the graphic buttons, and the
   * upper status bar which displays the time @ the main auction site.
   *
   * @param inFrame - The frame to add the toolbar/status to.
   *
   * @param inAction - The action listener that is to be informed when
   * events occur on the toolbar.
   *
   * @return - A JPanel containing the entire toolbar and header status bar.
   */
  public JPanel buildHeaderBar(JFrame inFrame, final JTabManager inAction) {
    _bidBarPanel = new JPanel(new BorderLayout());
    _bidMenu = JBidMenuBar.getInstance(inAction, "JBidwatcher");

    _headerStatus = new JLabel("", SwingConstants.RIGHT);
    inFrame.setJMenuBar(_bidMenu);
    AuctionServerManager.getInstance().addAuctionServerMenus();

    _bidMenu.add(Box.createHorizontalGlue());

    JToolBar _bidBar = new JToolBar();

    _bidBarPanel.setBorder(BorderFactory.createEtchedBorder());
    _bidBarPanel.add(_headerStatus, BorderLayout.EAST);

    JBidToolBar.addbutton(_bidBar, inAction, "Add", "icons/add_auction.png", "Add auction");
    JBidToolBar.addbutton(_bidBar, inAction, "Delete", "icons/delete.png", "Delete Auction");

    JBidToolBar.addbutton(_bidBar, inAction, "Search", "icons/find.png", "Auction Search Manager");

    JBidToolBar.addbutton(_bidBar, inAction, "Information", "icons/information.png", "Get information");

    JBidToolBar.addbutton(_bidBar, inAction, "UpdateAll", "icons/updateall.png", "Update All Auctions");
    JBidToolBar.addbutton(_bidBar, inAction, "StopUpdating", "icons/stopupdating.png", "Stop Updating Auctions");

    JBidToolBar.addbutton(_bidBar, inAction, "Configure", "icons/configuration.png", "Configure");
    JBidToolBar.addbutton(_bidBar, inAction, "Save", "icons/save.png", "Save Auctions");

    //      addbutton(_bidBar, inAction, "GetMyEbay", "getmyebay.gif", "Get My eBay");

    JBidToolBar.addbutton(_bidBar, inAction, "Help", "icons/help.png", "Help");
    JBidToolBar.addbutton(_bidBar, inAction, "About", "icons/about.png", "About JBidWatcher");
    JBidToolBar.addbutton(_bidBar, inAction, "Forum", "icons/forum.png", "JBidwatcher Forums");
    JBidToolBar.addbutton(_bidBar, inAction, "Report Bug", "icons/report_bug.png", "Report Bug");
    JBidToolBar.addbutton(_bidBar, inAction, "View Log", "icons/log_view.png", "View Log");
    JBidToolBar.addbutton(_bidBar, inAction, "Snipe", "icons/auction.png", "Place snipe");

    if(JConfig.queryConfiguration("toolbar.floater", "false").equals("false")) {
      _bidBar.setFloatable(false);
    }

    _bidBar.setRollover(true);

    // update (?)
    // bid (dollar in a circle?)
    // snipe (clock background with a $ overlay)
    // cancel snipe (an X over the snipe?)
    // Browse?
    // Comment
    // Copy
    // Print?
    // Paste an auction from the clipboard
    // Synchronize the time
    // Exit?

    /**
     * Add selection/search bar.
     */
    DocumentListener selectListener = new DocumentListener() {
        public void insertUpdate(DocumentEvent de) {
        }
        public void changedUpdate(DocumentEvent de) {
        }
        public void removeUpdate(DocumentEvent de) {
        }
      };
    JConfigTab.adjustField(selectBox, "Search and select items from the current table.", selectListener);
    ActionListener doSearch = new ActionListener() {
        public void actionPerformed(ActionEvent ae) {
          inAction.selectBySearch(selectBox.getText());
        }
      };
    selectBox.addActionListener(doSearch);
    JPanel jp = new JPanel();
    jp.add(JConfigTab.makeLine(new JLabel(" Select: "), selectBox));
    _bidBar.add(JConfigTab.panelPack(jp));

    _bidBarPanel.add(_bidBar, BorderLayout.WEST);

    _bidBarPanel.setVisible(JConfig.queryConfiguration("display.toolbar", "true").equals("true"));

    return _bidBarPanel;
  }

  /**
   * @brief Add a toolbar button to the display, with a particular action, name, image, and tooltip.
   *
   * @param jtb - The toolbar to add to.
   * @param inAction - The ActionListener who will listen for actions on this button.
   * @param buttonName - The action name that will be sent to the action listener when the button is pressed.
   * @param buttonImage - The image to use for the button.
   * @param buttonTip - The tooltip to pop up for the button.
   */
  public static void addbutton(JToolBar jtb, ActionListener inAction, String buttonName, String buttonImage, String buttonTip) {
    final JButton newButton = makeButton(buttonImage, buttonTip, buttonName, inAction, false);

    if(Platform.isMac()) {
      newButton.setBorder(null);
      newButton.setBorderPainted(false);
      newButton.setContentAreaFilled(false);
      newButton.setRolloverEnabled(true);
    }

    jtb.add(newButton);
  }

  private static ClassLoader urlCL = (ClassLoader)JBidWatch.class.getClassLoader();
  public static JButton makeButton(String buttonImage, String buttonTip, String buttonName, ActionListener inAction, boolean shrink) {
    JButton newButton = new JButton();
    ImageIcon newImage = new ImageIcon(urlCL.getResource(buttonImage));

    newButton.setIcon(newImage);
    if(shrink) {
      Dimension size = new Dimension(newImage.getIconWidth(), newImage.getIconHeight());
      newButton.setSize(size);
      newButton.setMaximumSize(size);
      newButton.setMinimumSize(size);
      newButton.setPreferredSize(size);
    }
    newButton.setToolTipText(buttonTip);
    newButton.setActionCommand("BT-" + buttonName);
    newButton.addActionListener(inAction);
    return newButton;
  }

  private JBidToolBar() {
    //  Nothing to do, just yet...?
  }

  public static JBidToolBar getInstance() {
    if(mInstance == null) mInstance = new JBidToolBar();
    return mInstance;
  }

  public void setText(String msg) {
    _headerStatus.setText(msg);
  }

  public void setToolTipText(String tooltip) {
    _headerStatus.setToolTipText(tooltip);
  }

  public void show(boolean visible) {
    _headerStatus.setVisible(visible);
  }

  public void togglePanel() {
    _bidBarPanel.setVisible(!_bidBarPanel.isVisible());
    JConfig.setConfiguration("display.toolbar", _bidBarPanel.isVisible()?"true":"false");
    if (_bidBarPanel.isVisible()) {
      show(false);
      _bidBarPanel.add(_headerStatus, BorderLayout.EAST);
      show(true);
    } else {
      //  If it's a mac, the clock display can't move into the 'menu' component, because there isn't one!
      if (!Platform.isMac()) {
        _bidMenu.add(_headerStatus);
      }
    }
  }
}
