package com.jbidwatcher.app;

import com.jbidwatcher.util.queue.*;
import com.jbidwatcher.util.Constants;
import com.jbidwatcher.util.config.JConfig;
import com.jbidwatcher.util.config.ErrorManagement;
import com.jbidwatcher.util.html.JHTMLOutput;
import com.jbidwatcher.ui.JBidToolBar;
import com.jbidwatcher.ui.UISnapshot;
import com.jbidwatcher.ui.MyActionListener;
import com.jbidwatcher.ui.SwingMessageQueue;
import com.jbidwatcher.ui.util.OptionUI;
import com.jbidwatcher.auction.FilterManager;
import com.jbidwatcher.auction.AuctionEntry;
import com.jbidwatcher.auction.AuctionsManager;
import com.jbidwatcher.auction.server.AuctionStats;
import com.jbidwatcher.auction.server.AuctionServerManager;
import com.jbidwatcher.auction.server.AuctionServer;
import com.jbidwatcher.platform.Platform;
import com.jbidwatcher.search.SearchManager;
import com.jbidwatcher.UpdaterEntry;
import com.jbidwatcher.UpdateManager;

import javax.swing.*;
import java.awt.Frame;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.util.*;

/**
 * Created by IntelliJ IDEA.
 * User: Morgan
 * Date: Mar 9, 2008
 * Time: 1:23:58 AM
 *
 * The class that handles most of the queued UI messages.
 */
public class UIBackbone implements MessageQueue.Listener {
  private boolean _userValid;
  private Date mNow = new Date();
  private Calendar mCal = new GregorianCalendar();
  private MacFriendlyFrame mFrame;

  public UIBackbone() {
    TimerHandler clockTimer = new TimerHandler(new TimerHandler.WakeupProcess() {
      public boolean check() {
        checkClock();
        return true;
      }
    });
    clockTimer.setName("Clock");
    clockTimer.start();

    MQFactory.addQueue("Swing", new SwingMessageQueue());
    MQFactory.getConcrete("Swing").registerListener(this);
  }

  private void logActivity(String action) {
    MQFactory.getConcrete("activity").enqueue(action);
  }

  private boolean _linkUp = true;

  /**
   * @param linkIsUp Is the connection to the auction server up or down?
   * @brief Function to let any class tell us that the link is down or
   * up again.
   */
  public void setLinkUp(boolean linkIsUp) {
    _linkUp = linkIsUp;
  }

  /**
   * @return - true if the connection with the auction server appears to be working, false otherwise.
   * @brief Function to identify if the link is up or down.
   */
  public boolean getLinkUp() {
    return _linkUp;
  }

  /**
   * @param newStatus The text to place on the status line.
   * @brief Sets the text in the status bar on the bottom of the screen.
   */
  private void setStatus(String newStatus) {
    mNow.setTime(System.currentTimeMillis());
    String defaultServerTime = AuctionServerManager.getInstance().getDefaultServerTime();
    String bracketed = " [" + defaultServerTime + '/' + Constants.localClockFormat.format(mNow) + ']';
    if (JConfig.queryConfiguration("timesync.enabled", "true").equals("false")) {
      TimeZone tz = AuctionServerManager.getInstance().getDefaultServer().getOfficialServerTimeZone();
      if (tz != null && tz.hasSameRules(mCal.getTimeZone())) {
        bracketed = " [" + Constants.localClockFormat.format(mNow) + ']';
      }
    }

    String statusToDisplay = newStatus + bracketed;

    if (mFrame != null) {
      mFrame.setStatus(statusToDisplay);
    } else {
      ErrorManagement.logDebug(newStatus + bracketed);
    }
  }

  private final static String HEADER_MSG = "HEADER ";
  private final static String SNIPE_ALTERED_MSG = "SNIPECHANGED";
  final static String QUIT_MSG = "QUIT";
  private final static String LINK_MSG = "LINK ";
  private final static String ERROR_MSG = "ERROR ";
  private final static String VISIBILITY_MSG = "VISIBILITY";
  private final static String NEWVERSION_MSG = "NEWVERSION";
  private final static String NO_NEWVERSION_MSG = "NO_NEWVERSION";
  private final static String BAD_NEWVERSION_MSG = "BAD_NEWVERSION";
  private final static String ALERT_MSG = "ALERT ";
  private final static String NOTIFY_MSG = "NOTIFY ";
  private final static String HIDE_MSG = "HIDE";
  private final static String RESTORE_MSG = "RESTORE";
  private final static String IGNORABLE_MSG = "IGNORE ";
  private final static String INVALID_LOGIN_MSG = "INVALID LOGIN";
  private final static String VALID_LOGIN_MSG = "VALID LOGIN";
  private final static String START_UPDATING = "ALLOW_UPDATES";

  /**
   * @brief Handle messages to tell the UI to do something.
   * <p/>
   * This is the sole place that UI updates should be done, and all
   * requests to do UI activities should be sent via the MessageQueue
   * for "Swing".  This ensures that they are done on the Swing UI
   * update thread, instead of in random threads throughout the
   * program.  Since Swing is single-threaded, this is necessary.
   * <p/>
   * Messages supported (suffixed by _MSG) are:
   * HEADER     (Draw text on the header (site time))
   * LINK       (Identify whether the link is up/down)
   * QUIT       (Shut down the program.)
   * ERROR      (Show an error message.)
   * NEWVERSION (Show an announcement about the new version!)
   * NO_NEWVERSION (Note that no new version is ready.)
   * <p/>
   * anything else is presumed to be a status message, to be displayed
   * in the status bar at the bottom of the screen.
   */
  public void messageAction(Object deQ) {
    String msg = (String) deQ;
    if (msg.startsWith(HEADER_MSG)) {
      String headerMsg = msg.substring(HEADER_MSG.length());
      handleHeader(headerMsg);
    } else if (msg.startsWith(LINK_MSG)) {
      String linkMsg = msg.substring(LINK_MSG.length());
      handleLinkStatus(linkMsg);
    } else if (msg.equals(QUIT_MSG)) {
      logActivity("Shutting down.");
      mFrame.shutdown();
    } else if (msg.equals(VISIBILITY_MSG)) {
      toggleVisibility();
    } else if (msg.equals(HIDE_MSG)) {
      hideUI();
    } else if (msg.equals(RESTORE_MSG)) {
      showUI();
    } else if (msg.equals(SNIPE_ALTERED_MSG)) {
      alterSnipeStatus();
    } else if (msg.startsWith(ALERT_MSG)) {
      String alertMsg = msg.substring(ALERT_MSG.length());
      logActivity("Alert: " + alertMsg);
      JOptionPane.showMessageDialog(null, alertMsg, "Alert", JOptionPane.PLAIN_MESSAGE);
    } else if (msg.startsWith(NOTIFY_MSG)) {
      String notifyMsg = msg.substring(NOTIFY_MSG.length());
      logActivity("Notify: " + notifyMsg);
      handleNotify(msg, notifyMsg);
    } else if (msg.startsWith(IGNORABLE_MSG)) {
      String configstr = msg.substring(IGNORABLE_MSG.length());
      handleIgnorable(configstr);
    } else if (msg.startsWith(ERROR_MSG)) {
      String errorMsg = msg.substring(ERROR_MSG.length());
      logActivity("Error: " + errorMsg);
      JOptionPane.showMessageDialog(null, errorMsg, "An error occurred", JOptionPane.PLAIN_MESSAGE);
    } else if (msg.equals(NEWVERSION_MSG)) {
      logActivity("New version found!");
      announceNewVersion();
    } else if (msg.startsWith(INVALID_LOGIN_MSG)) {
      String rest = msg.substring(INVALID_LOGIN_MSG.length());
      handleInvalidLogin(rest);
    } else if (msg.equals(START_UPDATING)) {
      startUpdating();
    } else if (msg.equals(VALID_LOGIN_MSG)) {
      handleValidLogin();
    } else if (msg.equals(NO_NEWVERSION_MSG)) {
      JOptionPane.showMessageDialog(null, "No new version available yet.\nKeep checking back!", "No new version", JOptionPane.PLAIN_MESSAGE);
    } else if (msg.equals(BAD_NEWVERSION_MSG)) {
      JOptionPane.showMessageDialog(null, "Failed to check for a new version\nProbably a temporary network issue; try again in a little while.",
          "Version check failed", JOptionPane.PLAIN_MESSAGE);
    } else if (msg.equals("TOOLBAR")) {
      JBidToolBar.getInstance().togglePanel();
    } else {
      logActivity(msg);
      setStatus(msg);
    }
  }

  private void handleValidLogin() {
    MQFactory.getConcrete("Swing").enqueue("SNIPECHANGED");
    AuctionsManager.start();
    SearchManager.start();
    JBidToolBar.getInstance().setToolTipText("");

    _userValid = true;
  }

  private void startUpdating() {
    MQFactory.getConcrete("Swing").enqueue("SNIPECHANGED");
    AuctionsManager.start();
    SearchManager.start();

    if (!_userValid) {
      String msgType = ALERT_MSG;
      String destination = "Swing";
      if (Platform.isTrayEnabled()) {
        msgType = NOTIFY_MSG;
        destination = "tray";
      }
      MQFactory.getConcrete(destination).enqueue(msgType +
          "Not yet logged in.  Snipes will not fire until logging in\n" +
          "is successful.  Item updating has been enabled, but any\n" +
          "features that rely on being logged in will not work.");
    }
  }

  private void handleInvalidLogin(String rest) {
    _userValid = false;
    if (rest.length() != 0) {
      //  Eliminate a space that's there for readibility.
      rest = rest.substring(1);
      JBidToolBar.getInstance().setToolTipText(rest);
      logActivity(rest);
    } else {
      logActivity("Invalid login.");
    }
  }

  private void handleIgnorable(String configstr) {
    int configLen = configstr.indexOf(' ');
    String realMsg = configstr.substring(configLen + 1);
    configstr = configstr.substring(0, configLen);
    OptionUI oui = new OptionUI();
    oui.promptWithCheckbox(null, realMsg, "Alert", configstr, JOptionPane.PLAIN_MESSAGE, JOptionPane.OK_OPTION);
  }

  private void handleNotify(String msg, String notifyMsg) {
    if (Platform.isTrayEnabled()) {
      MQFactory.getConcrete("tray").enqueue(msg);
    } else {
      MQFactory.getConcrete("Swing").enqueue(notifyMsg);
    }
  }

  private void alterSnipeStatus() {
    if (Platform.isTrayEnabled()) {
      AuctionStats as = AuctionServerManager.getInstance().getStats();
      if (as != null) {
        StringBuffer snipeText = new StringBuffer("TOOLTIP ");
        if (as.getSnipes() != 0) {
          snipeText.append("Next Snipe at: ").append(Constants.remoteClockFormat.format(as.getNextSnipe().getSnipeDate())).append('\n');
          snipeText.append(as.getSnipes()).append(" snipes outstanding\n");
        }
        if (as.getCompleted() != 0) {
          snipeText.append(as.getCompleted()).append(" auctions completed\n");
        }
        snipeText.append(as.getCount()).append(" auctions total");
        MQFactory.getConcrete("tray").enqueue(snipeText.toString());
      }
    }
  }

  private void showUI() {
    mFrame.setVisible(true);
    if (Platform.isTrayEnabled()) {
      MQFactory.getConcrete("tray").enqueue("RESTORED");
    }
    mFrame.setState(Frame.NORMAL);
  }

  private void hideUI() {
    if (mFrame.isVisible()) {
      UISnapshot.recordLocation(mFrame);
    }
    mFrame.setVisible(false);

    if (Platform.isTrayEnabled()) {
      MQFactory.getConcrete("tray").enqueue("HIDDEN");
    }
  }

  private void toggleVisibility() {
    mFrame.setVisible(!mFrame.isVisible());
    MQFactory.getConcrete("tray").enqueue(mFrame.isVisible() ? "RESTORED" : "HIDDEN");
    if (mFrame.isVisible()) mFrame.setState(Frame.NORMAL);
  }

  private void handleLinkStatus(String linkStat) {
    setLinkUp(linkStat.startsWith("UP"));
    String rest = linkStat.substring(linkStat.startsWith("UP") ? 2 : 4);
    if (rest.length() == 0) {
      if (_userValid) JBidToolBar.getInstance().setToolTipText("");
    } else {
      //  Skip a 'space' at the start.
      rest = rest.substring(1);
      logActivity("Link issues:");
      logActivity(rest);
      if (_userValid) JBidToolBar.getInstance().setToolTipText(rest);
    }
  }

  private void handleHeader(String headerMsg) {
    JBidToolBar.getInstance().setText(headerMsg);
    FilterManager.getInstance().check();
  }

  private static final int ONEK = 1024;

  private static final int UPDATE_FRAME_WIDTH = 512;
  private static final int UPDATE_FRAME_HEIGHT = 350;

  /**
   * @brief Announce that a new version is available, and let the user
   * decide what to do about it.
   */
  private static void announceNewVersion() {
    List<String> buttons = new ArrayList<String>();
    buttons.add("Download");
    buttons.add("Ignore");

    final UpdaterEntry ue = UpdateManager.getInstance().getUpdateInfo();
    StringBuffer fullMsg = new StringBuffer(4 * ONEK);
    fullMsg.append("<html><body>There is a new version available!<br>The new version is <b>");
    fullMsg.append(ue.getVersion());
    fullMsg.append("</b><br>");
    fullMsg.append("Upgrading is ");
    fullMsg.append(ue.getSeverity());
    fullMsg.append("<br>The URL is <a href=\"");
    fullMsg.append(ue.getURL());
    fullMsg.append("\">");
    fullMsg.append(ue.getURL());
    fullMsg.append("</a><br><br>");
    fullMsg.append(ue.getDescription()).append("</body></html>");

    MyActionListener mal = new MyActionListener() {
      private final String go_to = ue.getURL();

      public void actionPerformed(ActionEvent listen_ae) {
        String actionString = listen_ae.getActionCommand();
        if (actionString.equals("Download")) {
          MQFactory.getConcrete("browse").enqueue(go_to);
        }
        m_within.dispose();
        m_within = null;
      }
    };
    OptionUI oui = new OptionUI();
    JFrame newFrame = oui.showChoiceTextDisplay(new JHTMLOutput("Version " + ue.getVersion() + " available!", fullMsg).getStringBuffer(),
        new Dimension(UPDATE_FRAME_WIDTH, UPDATE_FRAME_HEIGHT), "Version " + ue.getVersion() + " available!", buttons,
        "Upgrade information", mal);
    mal.setFrame(newFrame);
  }

  static long lastTime;

  /**
   * @brief Show the time once a second, in strikeout if the link to
   * the default auction server is down.
   */
  void checkClock() {
    if (lastTime != 0) {
      if ((lastTime + Constants.ONE_MINUTE) < System.currentTimeMillis()) {
        //  We've been out for more than a minute!
        handleSleepDeprivation();
      }
    }
    lastTime = System.currentTimeMillis();
    String defaultServerTime = AuctionServerManager.getInstance().getDefaultServerTime();
    if (JConfig.queryConfiguration("display.toolbar", "true").equals("true")) {
      defaultServerTime = "<b>" + defaultServerTime.replace("@", "</b><br>");
//      defaultServerTime = "<b style=\"background-color: #c0c0c0\">" + defaultServerTime.replace("@", "</b><br>");
    }

    if (!_userValid) defaultServerTime = "Not logged in...";
    String headerLine = _linkUp ? defaultServerTime : "<strike>" + defaultServerTime + "</strike>";

    headerLine = "<html>" + headerLine + "</html>";

    MQFactory.getConcrete("Swing").enqueue("HEADER " + headerLine);
  }

  private void handleSleepDeprivation() {
    Date now = new Date();
    String status = "We appear to be waking from sleep; networking may not be up yet.";
    ErrorManagement.logDebug(status);
    List<AuctionEntry> sniped = AuctionServerManager.getInstance().allSniped();
    if (sniped != null && !sniped.isEmpty()) {
      boolean foundSnipe = false;
      for (AuctionEntry entry : sniped) {
        entry.setLastStatus(status);
        if (now.after(entry.getEndDate())) {
          entry.setLastStatus("The computer may have slept through the snipe time!");
          if (!foundSnipe) foundSnipe = true;
        }
      }
      if (foundSnipe) status += "  One or more snipes may not have been fired.";
      MQFactory.getConcrete("Swing").enqueue(NOTIFY_MSG + status);
    }
    SuperQueue.getInstance().getQueue().add(new AuctionQObject(AuctionQObject.MENU_CMD, AuctionServer.UPDATE_LOGIN_COOKIE, null), "ebay", System.currentTimeMillis() + (10 * Constants.ONE_SECOND));
  }

  public void setMainFrame(MacFriendlyFrame frame) {
    mFrame = frame;
  }
}
