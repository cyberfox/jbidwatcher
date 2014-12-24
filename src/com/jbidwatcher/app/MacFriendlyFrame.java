package com.jbidwatcher.app;

import com.jbidwatcher.util.config.JConfig;
import com.jbidwatcher.util.queue.MQFactory;
import com.jbidwatcher.util.Constants;
import com.jbidwatcher.util.xml.XMLElement;
import com.jbidwatcher.auction.AuctionEntry;
import com.jbidwatcher.ui.util.JMouseAdapter;
import com.jbidwatcher.ui.util.OptionUI;
import com.jbidwatcher.ui.*;
import com.cyberfox.util.platform.Platform;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.event.*;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Color;
import java.net.URL;

/**
 * Created by IntelliJ IDEA.
 * User: Morgan
 * Date: Mar 9, 2008
 * Time: 1:38:29 AM
 *
 * Mac friendly (with mac tool menus) frame for JBidwatcher.
 */
class MacFriendlyFrame extends JFrame implements com.apple.mrj.MRJQuitHandler, com.apple.mrj.MRJAboutHandler, com.apple.mrj.MRJPrefsHandler {
  private JLabel mStatusBar;
  private JLabel mPrices;

  /**
   * @brief Constructs a new window frame, with all the sorted tables,
   * scroll bars, drag and drop targets, menu & header bar, and status
   * line.  Returns a completed frame, suitable for displaying as the primary UI of the program.
   *
   * @param title - The frame title.
   * @param myFrameAdapter - The adapter to listen to mouse events.
   * @param iconURL - The URL of the icon to associate with the frame.
   * @param tabManager - The Tab Manager to display within the frame.
   */
  public MacFriendlyFrame(JBidToolBar toolBar, String title, JMouseAdapter myFrameAdapter, URL iconURL, JTabManager tabManager) {
    super(title);

    setMinimumSize(new Dimension(1000, 320));

    com.apple.mrj.MRJApplicationUtils.registerQuitHandler(this);
    com.apple.mrj.MRJApplicationUtils.registerAboutHandler(this);
    com.apple.mrj.MRJApplicationUtils.registerPrefsHandler(this);

    addMouseListener(myFrameAdapter);
    setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
    if(!Platform.isMac()) setIconImage(new ImageIcon(iconURL).getImage());

    if (JConfig.queryConfiguration("mac.useMetal", "true").equals("true")) getRootPane().putClientProperty("apple.awt.brushMetalLook", "true");

    JPanel headerBar = toolBar.buildHeaderBar(this, tabManager);

    JPanel statusPane = buildStatusLine();

    getContentPane().add(tabManager.getTabs());
    getContentPane().add(statusPane, BorderLayout.SOUTH);
    getContentPane().add(headerBar, BorderLayout.NORTH);

    pack();

    addWindowListener(new WindowAdapter() {
      public void windowIconified(WindowEvent we) {
        super.windowIconified(we);
        if (Platform.supportsTray() &&
            Platform.isTrayEnabled() &&
            JConfig.queryConfiguration("windows.tray", "true").equals("true") &&
            JConfig.queryConfiguration("windows.minimize", "true").equals("true")) {
          MQFactory.getConcrete("Swing").enqueue("HIDE");
        }
      }

      public void windowClosing(WindowEvent we) {
        super.windowClosing(we);
        MQFactory.getConcrete("Swing").enqueue(UIBackbone.QUIT_MSG);
      }
    });
  }

  private JPanel buildStatusLine() {
    final JPanel statusPane = new JPanel();
    Border myBorder = BorderFactory.createCompoundBorder(
        BorderFactory.createCompoundBorder(
            BorderFactory.createEmptyBorder(0, 2, 0, 2),
            BorderFactory.createMatteBorder(1, 0, 0, 0, Color.DARK_GRAY)),
        BorderFactory.createEmptyBorder(1, 5, 1, 5));
    statusPane.setBorder(myBorder);
    statusPane.setLayout(new BoxLayout(statusPane, BoxLayout.X_AXIS));

    JSeparator vert1 = new JSeparator(SwingConstants.VERTICAL);
    vert1.setForeground(Color.DARK_GRAY);
    vert1.setMinimumSize(new Dimension(10, 5));
    vert1.setMaximumSize(new Dimension(10, 20));
    statusPane.add(vert1);

    mStatusBar = new JLabel("Ready!");
    final Dimension statusBarSize = new Dimension(600, 16);
    mStatusBar.setMaximumSize(statusBarSize);
    mStatusBar.setMinimumSize(statusBarSize);
    mStatusBar.setPreferredSize(statusBarSize);
    statusPane.add(mStatusBar);

    statusPane.add(Box.createHorizontalGlue());

    JSeparator vert2 = new JSeparator(SwingConstants.VERTICAL);
    vert2.setForeground(Color.DARK_GRAY);
    vert2.setMinimumSize(new Dimension(10, 5));
    vert2.setMaximumSize(new Dimension(10, 20));
    statusPane.add(vert2);

    mPrices = new JLabel(" ");
    Dimension priceSize = new Dimension(300, 16);
    mPrices.setMinimumSize(priceSize);
    mPrices.setPreferredSize(priceSize);
    statusPane.add(mPrices);

    statusPane.add(Box.createHorizontalStrut(10));

    final int baseSize = 14 + 2 + 10 + 10 + 300 + 10;
    addComponentListener(new ComponentAdapter() {
      public void componentResized(ComponentEvent e) {
        int textWidthAllowed = statusPane.getWidth() - baseSize;
        statusBarSize.setSize(textWidthAllowed - 15, 16);
        mStatusBar.setMaximumSize(statusBarSize);
        mStatusBar.setMinimumSize(statusBarSize);
        mStatusBar.setPreferredSize(statusBarSize);
      }
    });

    return statusPane;
  }

  public void handleQuit() {
    if (!(JConfig.queryConfiguration("prompt.snipe_quit", "false").equals("true")) &&
        (AuctionEntry.snipedCount() != 0)) {
      MQFactory.getConcrete("Swing").enqueue(UIBackbone.QUIT_MSG);
      //  Please wait, we'll be ready to quit shortly.
      throw new IllegalStateException("Ne changez pas mains, il viendra bient?t.");
    } else {
      MQFactory.getConcrete("jbidwatcher").enqueue("EXIT");
    }
  }

  public void handleAbout() {
    MQFactory.getConcrete("user").enqueue("About " + Constants.PROGRAM_NAME);
  }

  public void handlePrefs() {
    MQFactory.getConcrete("user").enqueue("Configure");
  }

  /**
   * author: Dan Caprioara on java-dev at lists.apple.com.
   * <p/>
   * There is a bug on Mac OS X, when a WindowAdapter
   * (apple.laf.AquaMenuBarUI$FixupMenuBarWindowAdapter) is
   * registered repeatedly to the frame, without checking it was
   * already added. This is done by the apple LF.<p>
   * <p/>
   * Here the window listener is first removed, and then added.
   */
  public synchronized void addWindowListener(WindowListener listen) {
    super.removeWindowListener(listen);
    super.addWindowListener(listen);
  }

  public void setStatus(String status) {
    mStatusBar.setText(XMLElement.decodeString(status));
    mStatusBar.paintImmediately(mStatusBar.getVisibleRect());
  }

  public void setPrice(String price) {
    mPrices.setText(price);
    mPrices.paintImmediately(mPrices.getVisibleRect());
  }

  /**
   * @brief Save the display properties, the configuration, the
   * auctions, and exit.  This exists to prompt for shutdown
   * if there are any outstanding snipes.
   */
  public void shutdown() {
    try {
      if (AuctionEntry.snipedCount() != 0) {
        OptionUI oui = new OptionUI();
        //  Use the right parent!  FIXME -- mrs: 17-February-2003 23:53
        int rval = oui.promptWithCheckbox(null, "There are outstanding snipes that will not be able to fire while " + Constants.PROGRAM_NAME +
            " is not running.  Are you sure you want to quit?", "Pending Snipes confirmation",
            "prompt.snipe_quit");
        if (rval == JOptionPane.CANCEL_OPTION) return;
      }
    } catch(Exception e) {
      JConfig.log().logDebug("Skipping snipe check due to exception!");
    }
    MQFactory.getConcrete("jbidwatcher").enqueue("EXIT");
  }
}
