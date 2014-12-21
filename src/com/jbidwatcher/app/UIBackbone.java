package com.jbidwatcher.app;

import com.cyberfox.util.platform.Platform;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.jbidwatcher.util.PauseManager;
import com.jbidwatcher.util.queue.*;
import com.jbidwatcher.util.Constants;
import com.jbidwatcher.util.config.JConfig;
import com.jbidwatcher.util.html.JHTMLOutput;
import com.jbidwatcher.ui.util.OptionUI;
import com.jbidwatcher.ui.*;
import com.jbidwatcher.auction.AuctionEntry;
import com.jbidwatcher.auction.EntryCorral;
import com.jbidwatcher.auction.server.AuctionStats;
import com.jbidwatcher.auction.server.AuctionServerManager;
import com.jbidwatcher.auction.server.AuctionServer;
import com.jbidwatcher.search.SearchManager;
import com.jbidwatcher.UpdaterEntry;
import com.jbidwatcher.UpdateManager;

import javax.swing.*;
import java.awt.Component;
import java.awt.Frame;
import java.awt.Dimension;
import java.awt.Window;
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
@Singleton
public final class UIBackbone implements MessageQueue.Listener {
  private boolean _userValid;
  private Date mNow = new Date();
  private Calendar mCal = new GregorianCalendar();
  private MacFriendlyFrame mFrame;
  private boolean mSmall;

  private final AuctionServerManager serverManager;
  private final AuctionsManager auctionsManager;
  private final SearchManager searcher;
  private final JTabManager tabs;
  private final EntryCorral entryCorral;
  private final PauseManager pauseManager;
  private final JBidToolBar toolBar;

  private static final ImageIcon redStatus = new ImageIcon(JConfig.getResource("/icons/status_red.png"));
  private static final ImageIcon redStatus16 = new ImageIcon(JConfig.getResource("/icons/status_red_16.png"));
  private static final ImageIcon greenStatus = new ImageIcon(JConfig.getResource("/icons/status_green.png"));
  private static final ImageIcon greenStatus16 = new ImageIcon(JConfig.getResource("/icons/status_green_16.png"));
  private static final ImageIcon yellowStatus = new ImageIcon(JConfig.getResource("/icons/status_yellow.png"));
  private static final ImageIcon yellowStatus16 = new ImageIcon(JConfig.getResource("/icons/status_yellow_16.png"));

  @Inject
  public UIBackbone(AuctionServerManager serverManager, AuctionsManager auctionsManager, SearchManager searcher, JTabManager tabs,
                    EntryCorral entryCorral, PauseManager pauseManager, JBidToolBar toolBar) {
    this.serverManager = serverManager;
    this.auctionsManager = auctionsManager;
    this.searcher = searcher;
    this.tabs = tabs;
    this.entryCorral = entryCorral;
    this.pauseManager = pauseManager;
    this.toolBar = toolBar;

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
    String defaultServerTime = serverManager.getDefaultServerTime();
    String bracketed = " [" + defaultServerTime + ']';
    if (JConfig.queryConfiguration("timesync.enabled", "true").equals("false")) {
      TimeZone tz = serverManager.getServer().getOfficialServerTimeZone();
      if (tz != null && tz.hasSameRules(mCal.getTimeZone())) {
        bracketed = " [" + Constants.localClockFormat.format(mNow) + ']';
      }
    }

    String statusToDisplay = newStatus + bracketed;

    if (mFrame != null) {
      mFrame.setStatus(statusToDisplay);
    } else {
      JConfig.log().logDebug(newStatus + bracketed);
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
  private static final String NOACCOUNT_MSG = "NOACCOUNT ";
  private static final String PRICE = "PRICE ";
  private static final String SMALL_USERINFO = "TOGGLE_SMALL";
  private static final String DEVICE_REGISTRATION = "SECURITY ";

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
    } else if (msg.startsWith("LOGINSTATUS ")) {
      handleLoginStatus(msg);
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
    } else if (msg.startsWith(DEVICE_REGISTRATION)) {
      String code = msg.substring(DEVICE_REGISTRATION.length());
      JOptionPane.showMessageDialog(null, "Enter the following code on your device: " + code, "Set up Synchronization", JOptionPane.PLAIN_MESSAGE);
    } else if (msg.startsWith(ALERT_MSG)) {
      String alertMsg = msg.substring(ALERT_MSG.length());
      logActivity("Alert: " + alertMsg);
      if(!duplicateDialog(alertMsg)) {
        JOptionPane.showMessageDialog(null, alertMsg, "Alert", JOptionPane.PLAIN_MESSAGE);
      }
    } else if (msg.startsWith(NOACCOUNT_MSG)) {
      String noAcctMsg = msg.substring(NOACCOUNT_MSG.length());
      logActivity("Alert: " + noAcctMsg);
      JOptionPane.showMessageDialog(null, noAcctMsg, "No auction account", JOptionPane.PLAIN_MESSAGE);
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
    } else if (msg.equals(SMALL_USERINFO)) {
      mSmall = !mSmall;
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
      toolBar.togglePanel();
    } else if (msg.startsWith(PRICE)) {
      if(mFrame != null) {
        mFrame.setPrice(msg.substring(PRICE.length()));
      }
    } else {
      logActivity(msg);
      setStatus(msg);
    }
  }

  private boolean duplicateDialog(String alertMsg) {
    Window[] rval = JDialog.getWindows();

    for (Window w : rval) {
      if (w instanceof JDialog) {
        JDialog jd = (JDialog)w;
        if(jd.isVisible()) {
          Component[] components = jd.getContentPane().getComponents();
          for(Component c : components) {
            if(c instanceof JOptionPane) {
              if(((JOptionPane)c).getMessage().equals(alertMsg)) return true;
            }
          }
        }
      }
    }
    return false;
  }

  private void handleLoginStatus(String msg) {
    String status = msg.substring("LOGINSTATUS ".length());
    if(status.startsWith("FAILED")) {
      toolBar.setToolTipText("Login failed.");
      toolBar.setTextIcon(redStatus, redStatus16);
      JConfig.getMetrics().trackEvent("login", "fail");
      notifyAlert(status.substring("FAILED ".length()));
    } else if(status.startsWith("CAPTCHA")) {
      toolBar.setToolTipText("Login failed due to CAPTCHA.");
      toolBar.setTextIcon(redStatus, redStatus16);
      JConfig.getMetrics().trackEvent("login", "captcha");
    } else if(status.startsWith("SUCCESSFUL")) {
      toolBar.setToolTipText("Last login was successful.");
      toolBar.setTextIcon(greenStatus, greenStatus16);
      JConfig.getMetrics().trackEvent("login", "success");
    } else {   //  Status == NEUTRAL
      toolBar.setToolTipText("Last login did not clearly fail, but no valid cookies were received.");
      toolBar.setTextIcon(yellowStatus, yellowStatus16);
      JConfig.getMetrics().trackEvent("login", "neutral");
    }
  }

  private void handleValidLogin() {
    MQFactory.getConcrete("Swing").enqueue("SNIPECHANGED");
    auctionsManager.start();
    searcher.start();
    toolBar.setToolTipExtra(null);

    _userValid = true;
  }

  private void startUpdating() {
    MQFactory.getConcrete("Swing").enqueue("SNIPECHANGED");
    auctionsManager.start();
    searcher.start();

    if (!_userValid) {
      notifyAlert("Not yet logged in.  Snipes will not fire until logging in\n" +
          "is successful.  Item updating has been enabled, but any\n" +
          "features that rely on being logged in will not work.");
    }
  }

  private void notifyAlert(String alertMessage) {
    String msgType = ALERT_MSG;
    String destination = "Swing";
    if (Platform.isTrayEnabled()) {
      msgType = NOTIFY_MSG;
      destination = "tray";
    }
    MQFactory.getConcrete(destination).enqueue(msgType + alertMessage);
  }

  private void handleInvalidLogin(String rest) {
    _userValid = false;
    if (rest.length() != 0) {
      //  Eliminate a space that's there for readibility.
      rest = rest.substring(1);
      toolBar.setToolTipExtra(rest);
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
      AuctionStats as = serverManager.getStats();
      if (as != null) {
        StringBuilder snipeText = new StringBuilder("TOOLTIP ");
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
      if (_userValid) toolBar.setToolTipExtra(null);
    } else {
      //  Skip a 'space' at the start.
      rest = rest.substring(1);
      logActivity("Link issues:");
      logActivity(rest);
      if (_userValid) toolBar.setToolTipExtra(rest);
    }
  }

  private void handleHeader(String headerMsg) {
    toolBar.setText(headerMsg);
    tabs.updateTime();
  }

  private static final int ONEK = 1024;

  private static final int UPDATE_FRAME_WIDTH = 640;
  private static final int UPDATE_FRAME_HEIGHT = 450;

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
    String icon = JConfig.getResource("/jbidwatch64.jpg").toString();
    fullMsg.append("<html><body><table><tr><td><img src=\"").append(icon).append("\"></td>");
    fullMsg.append("<td valign=\"top\"><span class=\"banner\"><b>A new version of " + Constants.PROGRAM_NAME + " is available!</b></span><br>");
    fullMsg.append("<span class=\"smaller\">" + Constants.PROGRAM_NAME + " <b>").append(ue.getVersion());
    fullMsg.append("</b> is now available. Would you like to <a href=\"").append(ue.getURL()).append("\">download it now?</a><br><br>");
    fullMsg.append("Upgrading is <em>").append(ue.getSeverity()).append("</em></span></td></tr></table>");
    fullMsg.append("<p><b>Release Notes:</b></p><div class=\"changelog\">");
    String changelog = ue.getChangelog();
    if(changelog == null) {
      fullMsg.append(ue.getDescription());
    } else {
      fullMsg.append(changelog);
    }
    fullMsg.append("</div></body></html>");

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
    long now = System.currentTimeMillis();
    if (lastTime != 0) {
      if ((lastTime + Constants.ONE_MINUTE) < now) {
        //  We've been out for more than a minute!
        handleSleepDeprivation(now - lastTime);
      }
    }
    lastTime = now;
    String defaultServerTime = serverManager.getDefaultServerTime();
    if (JConfig.queryConfiguration("display.toolbar", "true").equals("true")) {
      defaultServerTime = "<b>" + defaultServerTime.replace("@", "</b><br>");
    }

    if (!_userValid) defaultServerTime = "Not logged in...";
    String headerLine = _linkUp ? defaultServerTime : "<strike>" + defaultServerTime + "</strike>";
    if(mSmall) headerLine = "<small>" + headerLine + "</small>";
    headerLine = "<html><body>" + headerLine + "</body></html>";

    MQFactory.getConcrete("Swing").enqueue("HEADER " + headerLine);
  }

  private void handleSleepDeprivation(long delta) {
    Date now = new Date();
    String status = "We appear to be waking from sleep; networking may not be up yet.";
    JConfig.log().logDebug(status);
    JConfig.getMetrics().trackEventTimed("sleep", "sleep", (int)delta, true);
    List<AuctionEntry> sniped = entryCorral.findAllSniped();
    if (sniped != null && !sniped.isEmpty()) {
      boolean foundSnipe = false;
      for (AuctionEntry entry : sniped) {
        entry.setLastStatus(status);
        if (now.after(entry.getEndDate())) {
          entry.setLastStatus("The computer may have slept through the snipe time!");
          foundSnipe = true;
        }
      }
      if (foundSnipe) {
        status += "  One or more snipes may not have been fired.";
        JConfig.getMetrics().trackEvent("sleep", "snipe_missed");
      }
      MQFactory.getConcrete("Swing").enqueue(NOTIFY_MSG + status);
    }
    //  Pause updates for 20 seconds
    pauseManager.pause(20);

    //  In 25 seconds, log back in.  This is because networking usually takes 15-20 seconds to restart after a sleep event.
    AuctionServer mainServer = serverManager.getServer();
    long wakeUp = System.currentTimeMillis() + (25 * Constants.ONE_SECOND);
    AuctionQObject updateEvent = new AuctionQObject(AuctionQObject.MENU_CMD, AuctionServer.UPDATE_LOGIN_COOKIE, null);

    SuperQueue.getInstance().getQueue().add(updateEvent, mainServer.getFriendlyName(), wakeUp);
  }

  public void setMainFrame(MacFriendlyFrame frame) {
    mFrame = frame;
  }
}
