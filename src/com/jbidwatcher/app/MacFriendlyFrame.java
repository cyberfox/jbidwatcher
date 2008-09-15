package com.jbidwatcher.app;

import com.jbidwatcher.util.config.JConfig;
import com.jbidwatcher.util.config.ErrorManagement;
import com.jbidwatcher.util.queue.MQFactory;
import com.jbidwatcher.util.db.ActiveRecord;
import com.jbidwatcher.util.Constants;
import com.jbidwatcher.auction.server.AuctionStats;
import com.jbidwatcher.auction.server.AuctionServerManager;
import com.jbidwatcher.auction.AuctionEntry;
import com.jbidwatcher.ui.util.JMouseAdapter;
import com.jbidwatcher.ui.util.OptionUI;
import com.jbidwatcher.ui.*;
import com.jbidwatcher.platform.Platform;
import com.jbidwatcher.search.SearchManager;

import javax.swing.*;
import java.awt.event.WindowListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.net.URL;
import java.util.Properties;

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

  /**
   * @brief Constructs a new window frame, with all the sorted tables,
   * scroll bars, drag and drop targets, menu & header bar, and status
   * line.
   *
   * @param title - The frame title.
   * @param myFrameAdapter - The adapter to listen to mouse events.
   * @param iconURL - The URL of the icon to associate with the frame.
   * @param tabManager - The Tab Manager to display within the frame.
   * 
   * @return A completed frame, suitable for displaying as the primary UI of the program.
   */
  public MacFriendlyFrame(String title, JMouseAdapter myFrameAdapter, URL iconURL, JTabManager tabManager) {
    super(title);

    setMinimumSize(new Dimension(1000, 320));

    com.apple.mrj.MRJApplicationUtils.registerQuitHandler(this);
    com.apple.mrj.MRJApplicationUtils.registerAboutHandler(this);
    com.apple.mrj.MRJApplicationUtils.registerPrefsHandler(this);

    addMouseListener(myFrameAdapter);
    setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
    setIconImage(new ImageIcon(iconURL).getImage());

    JPanel headerBar = JBidToolBar.getInstance().buildHeaderBar(this, tabManager);

    mStatusBar = new JLabel("Ready!", SwingConstants.LEFT);
    getContentPane().add(tabManager.getTabs());
    getContentPane().add(mStatusBar, BorderLayout.SOUTH);
    getContentPane().add(headerBar, BorderLayout.NORTH);

    pack();

    addWindowListener(new WindowAdapter() {
      public void windowIconified(WindowEvent we) {
        super.windowIconified(we);
        if (Platform.isWindows() &&
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

  public void handleQuit() {
    if (!(JConfig.queryConfiguration("prompt.snipe_quit", "false").equals("true")) &&
        (AuctionEntry.snipedCount() != 0)) {
      MQFactory.getConcrete("Swing").enqueue(UIBackbone.QUIT_MSG);
      //  Please wait, we'll be ready to quit shortly.
      throw new IllegalStateException("Ne changez pas mains, il viendra bient?t.");
    } else {
      internal_shutdown();
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
    mStatusBar.setText("<html><body>" + status + "</body></html>");
    mStatusBar.paintImmediately(mStatusBar.getVisibleRect());
  }

  /**
   * @return A property table of all the table column header information, suitable for saving.
   * @brief Obtains a 'property list' of all the column widths, names,
   * etc., in order to save them off so the UI can remain
   * approximately the same between executions.
   */
  public static Properties getColumnProperties() {
    Properties colProps = new Properties();

    colProps = ListManager.getInstance().extractProperties(colProps);

    return (colProps);
  }

  /**
   * @brief Save the display properties, the configuration, the
   * auctions, and exit.  This exists to prompt for shutdown
   * if there are any outstanding snipes.
   */
  public void shutdown() {
    if (AuctionEntry.snipedCount() != 0) {
      OptionUI oui = new OptionUI();
      //  Use the right parent!  FIXME -- mrs: 17-February-2003 23:53
      int rval = oui.promptWithCheckbox(null, "There are outstanding snipes that will not be able to fire while " + Constants.PROGRAM_NAME +
          " is not running.  Are you sure you want to quit?", "Pending Snipes confirmation",
          "prompt.snipe_quit");
      if (rval == JOptionPane.CANCEL_OPTION) return;
    }

    internal_shutdown();
  }

  public void internal_shutdown() {
    Properties colProps = getColumnProperties();
    SearchManager.getInstance().saveSearchDisplay();
    Properties displayProps = UISnapshot.snapshotLocation(this);
    JConfig.saveDisplayConfig(displayProps, colProps);

    //  Save it to the original file, if it was provided at runtime,
    //  otherwise to the appropriate default.
    String cfgLoad = JConfig.queryConfiguration("temp.cfg.load", "JBidWatch.cfg");
    String cfgFilename = cfgLoad.equals("JBidWatch.cfg") ? JConfig.getCanonicalFile(cfgLoad, "jbidwatcher", false) : cfgLoad;

    //  TODO -- Need to save searches in the database too...  Right now they're still hanging around in XML form.
    SearchManager.getInstance().saveSearches();
    AuctionStats as = AuctionServerManager.getInstance().getStats();
    JConfig.setConfiguration("last.auctioncount", Integer.toString(as.getCount()));
    JConfig.saveConfiguration(cfgFilename);
    ActiveRecord.shutdown();
    ErrorManagement.logMessage("Shutting down JBidwatcher.");
    ErrorManagement.closeLog();
    System.exit(0);
  }
}
