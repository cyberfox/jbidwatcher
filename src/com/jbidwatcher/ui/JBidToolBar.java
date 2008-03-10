package com.jbidwatcher.ui;

import com.jbidwatcher.platform.Platform;
import com.jbidwatcher.util.config.JConfig;
import com.jbidwatcher.ui.config.JConfigTab;
import com.jbidwatcher.auction.server.AuctionServerManager;

import javax.swing.*;
import javax.swing.event.DocumentListener;
import javax.swing.event.DocumentEvent;
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;
import java.awt.*;
import java.net.URL;

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
  private JLabel mHeaderStatus;
  private JPanel mBidBarPanel;
  private JBidMenuBar mBidMenu;
  private JTextField mSelectBox;
  private static JBidToolBar mInstance = null;

  private String getSource(String icon) {
    String iconSize = JConfig.queryConfiguration("ui.iconSize", "32");
    String toolbarSrc = "/icons/toolbar/" + iconSize + "/";

    return toolbarSrc + icon;
  }

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
    mBidBarPanel = new JPanel(new BorderLayout());
    mBidMenu = JBidMenuBar.getInstance(inAction, "JBidwatcher");

    mHeaderStatus = new JLabel("", SwingConstants.RIGHT);
    inFrame.setJMenuBar(mBidMenu);
    mBidMenu.add(AuctionServerManager.getInstance().addAuctionServerMenus().getMenu());

    mBidMenu.add(Box.createHorizontalGlue());

    JToolBar _bidBar = new JToolBar();

    mBidBarPanel.setBorder(BorderFactory.createEtchedBorder());
    mBidBarPanel.add(mHeaderStatus, BorderLayout.EAST);

    JBidToolBar.addbutton(_bidBar, inAction, "Add", getSource("add_auction.png"), "Add auction");
    JBidToolBar.addbutton(_bidBar, inAction, "Delete", getSource("delete.png"), "Delete Auction");

    JBidToolBar.addbutton(_bidBar, inAction, "Search", getSource("find.png"), "Auction Search Manager");

    JBidToolBar.addbutton(_bidBar, inAction, "Information", getSource("information.png"), "Get information");

    JBidToolBar.addbutton(_bidBar, inAction, "UpdateAll", getSource("updateall.png"), "Update All Auctions");
    JBidToolBar.addbutton(_bidBar, inAction, "StopUpdating", getSource("stopupdating.png"), "Stop Updating Auctions");

    JBidToolBar.addbutton(_bidBar, inAction, "Configure", getSource("configuration.png"), "Configure");
    JBidToolBar.addbutton(_bidBar, inAction, "Save", getSource("save.png"), "Save Auctions");

    //      addbutton(_bidBar, inAction, "GetMyEbay", "getmyebay.gif", "Get My eBay");

    JBidToolBar.addbutton(_bidBar, inAction, "FAQ", getSource("help.png"), "Help");
    JBidToolBar.addbutton(_bidBar, inAction, "About", getSource("about.png"), "About JBidWatcher");
    JBidToolBar.addbutton(_bidBar, inAction, "Forum", getSource("forum.png"), "JBidwatcher Forums");
    JBidToolBar.addbutton(_bidBar, inAction, "Report Bug", getSource("report_bug.png"), "Report Bug");
    JBidToolBar.addbutton(_bidBar, inAction, "View Log", getSource("log_view.png"), "View Log");
    JBidToolBar.addbutton(_bidBar, inAction, "Snipe", getSource("auction.png"), "Place snipe");

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
    JConfigTab.adjustField(mSelectBox, "Search and select items from the current table.", selectListener);
    ActionListener doSearch = new ActionListener() {
        public void actionPerformed(ActionEvent ae) {
          inAction.selectBySearch(mSelectBox.getText());
        }
      };
    mSelectBox.addActionListener(doSearch);
    JPanel jp = new JPanel(new GridBagLayout());
    jp.add(mSelectBox, new GridBagConstraints());
    _bidBar.add(jp);

    mBidBarPanel.add(_bidBar, BorderLayout.WEST);
    mBidBarPanel.putClientProperty("JToolBar.isRollover", Boolean.TRUE);

    mBidBarPanel.setVisible(JConfig.queryConfiguration("display.toolbar", "true").equals("true"));

    return mBidBarPanel;
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
      newButton.putClientProperty("Quaqua.Button.style", "toolBarRollover");
    }

    jtb.add(newButton);
  }

  public static JButton makeButton(String buttonImage, String buttonTip, String buttonName, ActionListener inAction, boolean shrink) {
    JButton newButton = new JButton();
    URL iconRes = JConfig.getResource(buttonImage);
    ImageIcon newImage = new ImageIcon(iconRes);

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
    mSelectBox = new SearchField("Select", SELECT_BOX_SIZE);
    if(Platform.isMac()) {
      mSelectBox.putClientProperty("Quaqua.TextField.style", "search");
    }
  }

  public static JBidToolBar getInstance() {
    if(mInstance == null) mInstance = new JBidToolBar();
    return mInstance;
  }

  public void setText(String msg) {
    if(mHeaderStatus != null) mHeaderStatus.setText(msg);
  }

  public void setToolTipText(String tooltip) {
    if (mHeaderStatus != null) mHeaderStatus.setToolTipText(tooltip);
  }

  public void show(boolean visible) {
    if (mHeaderStatus != null) mHeaderStatus.setVisible(visible);
  }

  public void togglePanel() {
    mBidBarPanel.setVisible(!mBidBarPanel.isVisible());
    JConfig.setConfiguration("display.toolbar", mBidBarPanel.isVisible()?"true":"false");
    if (mBidBarPanel.isVisible()) {
      show(false);
      mBidBarPanel.add(mHeaderStatus, BorderLayout.EAST);
      show(true);
    } else {
      //  If it's a mac, the clock display can't move into the 'menu' component, because there isn't one!
      if (!Platform.isMac()) {
        mBidMenu.add(mHeaderStatus);
      }
    }
  }
}
