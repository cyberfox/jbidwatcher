package com.jbidwatcher.ui;

import com.jbidwatcher.platform.Platform;
import com.jbidwatcher.util.config.JConfig;
import com.jbidwatcher.ui.config.JConfigTab;
import com.jbidwatcher.ui.util.SearchField;
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
  private Icon mCurrentStatus;
  private Icon mCurrentStatus16;

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
    establishMenu(inFrame, inAction);

    mBidBarPanel = new JPanel(new BorderLayout());
    mBidBarPanel.setBorder(BorderFactory.createEtchedBorder());

    mHeaderStatus = new JLabel("", SwingConstants.RIGHT);
    mBidBarPanel.add(mHeaderStatus, BorderLayout.EAST);

    JToolBar bidBar = establishToolbar(inAction);
    mBidBarPanel.add(bidBar, BorderLayout.WEST);

    mBidBarPanel.putClientProperty("JToolBar.isRollover", Boolean.TRUE);

    mBidBarPanel.setVisible(JConfig.queryConfiguration("display.toolbar", "true").equals("true"));

    return mBidBarPanel;
  }

  private JToolBar establishToolbar(final JTabManager inAction) {
    JToolBar bidBar = new JToolBar();

    JBidToolBar.addbutton(bidBar, inAction, "Add", getSource("add_auction.png"), "Add auction");
    JBidToolBar.addbutton(bidBar, inAction, "Search", getSource("find.png"), "Auction Search Manager");
    bidBar.addSeparator();
    JBidToolBar.addbutton(bidBar, inAction, "Snipe", getSource("auction.png"), "Place snipe");
    JBidToolBar.addbutton(bidBar, inAction, "Information", getSource("information.png"), "Get information");
    JBidToolBar.addbutton(bidBar, inAction, "Delete", getSource("delete.png"), "Delete Auction");
    bidBar.addSeparator();
    JBidToolBar.addbutton(bidBar, inAction, "UpdateAll", getSource("updateall.png"), "Update All Auctions");
    JBidToolBar.addbutton(bidBar, inAction, "StopUpdating", getSource("stopupdating.png"), "Stop Updating Auctions");
    bidBar.addSeparator();
    JBidToolBar.addbutton(bidBar, inAction, "Configure", getSource("configuration.png"), "Configure");
    if(JConfig.debugging) JBidToolBar.addbutton(bidBar, inAction, "View Log", getSource("log_view.png"), "View Log");
    JBidToolBar.addbutton(bidBar, inAction, "FAQ", getSource("help.png"), "Help");
    JBidToolBar.addbutton(bidBar, inAction, "About", getSource("about.png"), "About JBidwatcher");
    bidBar.addSeparator();
    JBidToolBar.addbutton(bidBar, inAction, "Forum", getSource("forum.png"), "JBidwatcher Forums");
    if (JConfig.debugging) JBidToolBar.addbutton(bidBar, inAction, "Report Bug", getSource("report_bug.png"), "Report Bug");

    if(JConfig.queryConfiguration("toolbar.floater", "false").equals("false")) {
      bidBar.setFloatable(false);
    }

    bidBar.setRollover(true);

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

    JPanel searchBox = establishSearchBox(inAction);
    bidBar.addSeparator();
    bidBar.add(searchBox);
    bidBar.putClientProperty("JToolBar.isRollover", Boolean.TRUE);
    return bidBar;
  }

  private JPanel establishSearchBox(final JTabManager inAction) { /**
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
    return jp;
  }

  private void establishMenu(JFrame inFrame, JTabManager inAction) {
    mBidMenu = JBidMenuBar.getInstance(inAction, "JBidwatcher");
    mBidMenu.add(AuctionServerManager.getInstance().addAuctionServerMenus().getMenu());
    mBidMenu.add(Box.createHorizontalGlue());
    inFrame.setJMenuBar(mBidMenu);
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

  public void setTextIcon(Icon status, Icon status16) {
    mCurrentStatus = status;
    mCurrentStatus16 = status16;
    if(mHeaderStatus != null) setStatusIcon();
  }

  public void setStatusIcon() {
    if(mBidBarPanel.isVisible()) {
      mHeaderStatus.setIcon(mCurrentStatus);
    } else {
      mHeaderStatus.setIcon(mCurrentStatus16);
    }
  }

  private String mTooltip = "";
  private String mExtra = "";

  public void setToolTipText(String tooltip) {
    mTooltip = tooltip;
    updateTooltip();
  }

  private void updateTooltip() {
    if(mHeaderStatus != null) {
      if(mExtra != null && mExtra.length() != 0) {
        mHeaderStatus.setToolTipText("<html><body>" + mTooltip + "<br>" + mExtra + "</body></html>");
      } else {
        mHeaderStatus.setToolTipText(mTooltip);
      }
    }
  }

  public void setToolTipExtra(String extra) {
    mExtra = extra;
    updateTooltip();
  }

  public void show(boolean visible) {
    if (mHeaderStatus != null) mHeaderStatus.setVisible(visible);
  }

  public void togglePanel() {
    mBidBarPanel.setVisible(!mBidBarPanel.isVisible());
    if(mHeaderStatus != null) setStatusIcon();
    JConfig.setConfiguration("display.toolbar", mBidBarPanel.isVisible()?"true":"false");
    if (mBidBarPanel.isVisible()) {
      show(false);
      mBidBarPanel.add(mHeaderStatus, BorderLayout.EAST, 0);
      show(true);
    } else {
      //  If it's a mac, the clock display can't move into the 'menu' component, because there isn't one!
      if (!Platform.isMac()) {
        mBidMenu.add(mHeaderStatus);
      }
    }
  }
}
