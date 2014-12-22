package com.jbidwatcher.ui;

import com.cyberfox.util.platform.Platform;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.jbidwatcher.ui.util.JPasteListener;
import com.jbidwatcher.util.config.JConfig;
import com.jbidwatcher.ui.config.JConfigTab;
import com.jbidwatcher.ui.util.SearchField;
import com.jbidwatcher.ui.util.ButtonMaker;
import com.jbidwatcher.auction.server.AuctionServerManager;
import com.jbidwatcher.util.queue.MQFactory;

import javax.swing.*;
import javax.swing.event.DocumentListener;
import javax.swing.event.DocumentEvent;
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * Created by IntelliJ IDEA.
 * User: mrs
 * Date: Feb 4, 2008
 * Time: 2:08:05 AM
 *
 * Move the toolbar construction code out to its own class, so it's not cluttering up the JBidWatch class.
 */
@Singleton
public class JBidToolBar {
  private static final int SELECT_BOX_SIZE=20;
  private final AuctionServerManager serverManager;
  private final JPasteListener pasteListener;
  private final JTabManager tabManager;
  @Inject
  private PopupMenuFactory menuFactory;
  private JLabel mHeaderStatus;
  private JPanel mBidBarPanel;
  private JBidMenuBar mBidMenu;
  private JTextField mSelectBox;
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

    mBidBarPanel = new JPanel();
    mBidBarPanel.setLayout(new BoxLayout(mBidBarPanel, BoxLayout.X_AXIS));
    if(!Platform.isMac()) {
      mBidBarPanel.setBorder(BorderFactory.createEtchedBorder());
    } else {
      mBidBarPanel.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 5));
    }

    JToolBar bidBar = establishToolbar(inAction);
    mBidBarPanel.add(bidBar);

    mBidBarPanel.add(Box.createHorizontalGlue());

    mHeaderStatus = new JLabel("", SwingConstants.RIGHT);
    Dimension boxSize = new Dimension(250, 32);
    mHeaderStatus.setMinimumSize(boxSize);
    mHeaderStatus.setPreferredSize(boxSize);
    mHeaderStatus.setMaximumSize(boxSize);
    mHeaderStatus.addMouseListener(new MouseAdapter() {
      public void mouseClicked(MouseEvent e) {
        super.mouseClicked(e);
        MQFactory.getConcrete("Swing").enqueue("TOGGLE_SMALL");
      }
    });

    mBidBarPanel.add(mHeaderStatus);

    bidBar.putClientProperty("JToolBar.isRollover", Boolean.TRUE);

    mBidBarPanel.setVisible(JConfig.queryConfiguration("display.toolbar", "true").equals("true"));
    if (!mBidBarPanel.isVisible() && !Platform.isMac()) {
      if (mHeaderStatus != null) setStatusIcon();
      mBidMenu.add(mHeaderStatus);
    }

    return mBidBarPanel;
  }

  private JButton mDonateButton;

  private JToolBar establishToolbar(final JTabManager inAction) {
    JToolBar bidBar = new JToolBar();

    ButtonMaker.addbutton(bidBar, inAction, "Add", getSource("add_auction.png"), "Add auction");
    ButtonMaker.addbutton(bidBar, inAction, "Search", getSource("find.png"), "Auction Search Manager");
    bidBar.addSeparator();
    ButtonMaker.addbutton(bidBar, inAction, "Snipe", getSource("auction.png"), "Place snipe");
    ButtonMaker.addbutton(bidBar, inAction, "Information", getSource("information.png"), "Get information");
    ButtonMaker.addbutton(bidBar, inAction, "Delete", getSource("delete.png"), "Delete auction");
    bidBar.addSeparator();
    ButtonMaker.addbutton(bidBar, inAction, "UpdateAll", getSource("updateall.png"), "Update all auctions");
    ButtonMaker.addbutton(bidBar, inAction, "StopUpdating", getSource("stopupdating.png"), "Stop updating auctions");
    bidBar.addSeparator();
    ButtonMaker.addbutton(bidBar, inAction, "Configure", getSource("configuration.png"), "Configure");
    if(JConfig.debugging) ButtonMaker.addbutton(bidBar, inAction, "View Log", getSource("log_view.png"), "View log");
    ButtonMaker.addbutton(bidBar, inAction, "FAQ", getSource("help.png"), "Help");
    ButtonMaker.addbutton(bidBar, inAction, "About", getSource("about.png"), "About JBidwatcher");
    bidBar.addSeparator();
    ButtonMaker.addbutton(bidBar, inAction, "Forum", getSource("forum.png"), "JBidwatcher forums");
    if (JConfig.debugging) ButtonMaker.addbutton(bidBar, inAction, "Report Bug", getSource("report_bug.png"), "Report bug");
    ButtonMaker.addbutton(bidBar, inAction, "My JBidwatcher", getSource("home.png"), "My JBidwatcher");
    if(JConfig.queryConfiguration("donation.clicked", "false").equals("false")) {
      mDonateButton = ButtonMaker.addbutton(bidBar, inAction, "Donate", "/icons/toolbar/btn_donate_SM.gif", "Please donate if you feel JBidwatcher is useful and has helped you!");
    }

    if(JConfig.queryConfiguration("toolbar.floater", "false").equals("false")) {
      bidBar.setFloatable(false);
    }

    if(Platform.isMac()) {
      bidBar.putClientProperty("Quaqua.ToolBar.style", "title");
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

    JComponent searchBox = establishSearchBox(inAction);
    bidBar.addSeparator();
    bidBar.add(searchBox);
    bidBar.addSeparator();
    bidBar.putClientProperty("JToolBar.isRollover", Boolean.TRUE);
    return bidBar;
  }

  /**
   * Add selection/search bar.
   * @param inAction - The action to be triggered by the search/select process.
   * @return - A component containing the search / select field.
   */
  private JComponent establishSearchBox(final JTabManager inAction) {
    DocumentListener selectListener = new DocumentListener() {
        public void insertUpdate(DocumentEvent de) {
        }
        public void changedUpdate(DocumentEvent de) {
        }
        public void removeUpdate(DocumentEvent de) {
        }
      };

    mSelectBox.addMouseListener(pasteListener);
    JConfigTab.tweakTextField(mSelectBox, "Search and select items from the current table.", selectListener);
    ActionListener doSearch = new ActionListener() {
        public void actionPerformed(ActionEvent ae) {
          inAction.selectBySearch(mSelectBox.getText());
        }
      };
    mSelectBox.addActionListener(doSearch);
    mSelectBox.putClientProperty("JTextField.variant", "search");

    JPanel compact = new JPanel();
    compact.setLayout(new BoxLayout(compact, BoxLayout.X_AXIS));
    compact.setPreferredSize(new Dimension(100, 24));
    compact.add(mSelectBox);
    return compact;
  }

  private void establishMenu(JFrame inFrame, JTabManager inAction) {
    mBidMenu = JBidMenuBar.getInstance(menuFactory, tabManager.getTabs(), inAction, "JBidwatcher");

    JMenu menu = serverManager.addAuctionServerMenus().getMenu();

    JBidMenuBar menuCheck = JBidMenuBar.getInstance(menuFactory, tabManager.getTabs(), null);
    if (menuCheck != mBidMenu) {
      menuCheck.add(menu);
      menuCheck.add(Box.createHorizontalGlue());
    }

    mBidMenu.add(menu);
    mBidMenu.add(Box.createHorizontalGlue());

    inFrame.setJMenuBar(mBidMenu);
  }

  @Inject
  private JBidToolBar(AuctionServerManager serverManager, JTabManager tabManager, JPasteListener pasteListener) {
    this.tabManager = tabManager;
    this.serverManager = serverManager;
    this.pasteListener = pasteListener;
    mSelectBox = new SearchField("Select", SELECT_BOX_SIZE);
    if(Platform.isMac()) {
      mSelectBox.putClientProperty("Quaqua.TextField.style", "search");
    }
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
      if(!Platform.isMac()) mBidBarPanel.add(mHeaderStatus, BorderLayout.EAST, 0);
      show(true);
    } else {
      //  If it's a mac, the clock display can't move into the 'menu' component, because there isn't one!
      if (!Platform.isMac()) {
        mBidMenu.add(mHeaderStatus);
      }
    }
  }

  public void hideDonation() {
    if(mDonateButton != null) mDonateButton.setVisible(false);
  }
}
