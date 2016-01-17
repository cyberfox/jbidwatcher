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

import static com.jbidwatcher.util.UIConstants.*;

/**
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
  private final EntryCorral entryCorral;
  private final PauseManager pauseManager;
  private final JBidToolBar toolBar;

  @Inject
  public UIBackbone(AuctionServerManager serverManager, AuctionsManager auctionsManager, SearchManager searcher,
                    EntryCorral entryCorral, PauseManager pauseManager, JBidToolBar toolBar) {
    this.serverManager = serverManager;
    this.auctionsManager = auctionsManager;
    this.searcher = searcher;
    this.entryCorral = entryCorral;
    this.pauseManager = pauseManager;
    this.toolBar = toolBar;

    TimerHandler clockTimer = new TimerHandler(new TimerHandler.WakeupProcess() {
      /**
       * Check and update the clock every second; also handles recognition of sleep-based slippage of time.
       *
       * @return True always.
       */
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

  private boolean _linkUp = true;

  /**
   * @brief Function to let any class tell us that the link is down or
   * up again.
   *
   * @param linkIsUp Is the connection to the auction server up or down?
   */
  public void setLinkUp(boolean linkIsUp) {
    _linkUp = linkIsUp;
  }

  /**
   * @brief Function to identify if the link is up or down.
   *
   * @return - true if the connection with the auction server appears to be working, false otherwise.
   */
  public boolean getLinkUp() {
    return _linkUp;
  }

  /**
   * Handle messages to tell the UI to do something.
   * <br>
   * <br>
   * This is the sole place that UI updates should be done, and all
   * requests to do UI activities should be sent via the MessageQueue
   * for "Swing".  This ensures that they are done on the Swing UI
   * update thread, instead of in random threads throughout the
   * program.  Since Swing is single-threaded, this is necessary.
   * <br>
   * <br>
   * Anything else is presumed to be a status message, to be displayed
   * in the status bar at the bottom of the screen.
   * <br>
   * <br>
   * The messages supported (suffixed by _MSG) are partially documented in
   * {@link com.jbidwatcher.util.UIConstants}
   *
   * @param deQ A string containing a command to be processed by the UI.
   */
  public void messageAction(Object deQ) {
    String[] cmdMessage = ((String) deQ).split(" ", 2);
    switch(cmdMessage[0]) {
      case QUIT_MSG:
        logActivity("Shutting down.");
        mFrame.shutdown();
        break;
      case HIDE_MSG:
        hideUI();
        break;
      case RESTORE_MSG:
        showUI();
        break;
      case VISIBILITY_MSG:
        toggleVisibility();
        break;
      case SNIPE_ALTERED_MSG:
        alterSnipeStatus();
        break;
      case NEWVERSION_MSG:
        logActivity("New version found!");
        announceNewVersion();
        break;
      case SMALL_USERINFO:
        mSmall = !mSmall;
        break;
      case START_UPDATING:
        startUpdating();
        break;
      case VALID_LOGIN_MSG:
        handleValidLogin();
        break;
      case NO_NEWVERSION_MSG:
        JOptionPane.showMessageDialog(null, "No new version available yet.\nKeep checking back!", "No new version", JOptionPane.PLAIN_MESSAGE);
        break;
      case BAD_NEWVERSION_MSG:
        JOptionPane.showMessageDialog(null, "Failed to check for a new version\nProbably a temporary network issue; try again in a little while.",
            "Version check failed", JOptionPane.PLAIN_MESSAGE);
        break;
      case TOOLBAR_MSG:
        toolBar.togglePanel();
        break;
      case HEADER_MSG:
        String headerMsg = cmdMessage[1];
        handleHeader(headerMsg);
        break;
      case LOGIN_STATUS_MSG:
        handleLoginStatus(cmdMessage[1]);
        break;
      case LINK_MSG:
        String linkMsg = cmdMessage[1];
        handleLinkStatus(linkMsg);
        break;
      case DEVICE_REGISTRATION:
        String code = cmdMessage[1];
        JOptionPane.showMessageDialog(null, "Enter the following code on your device: " + code, "Set up Synchronization", JOptionPane.PLAIN_MESSAGE);
        break;
      case ALERT_MSG:
        String alertMsg = cmdMessage[1];
        logActivity("Alert: " + alertMsg);
        if (!duplicateDialog(alertMsg)) {
          JOptionPane.showMessageDialog(null, alertMsg, "Alert", JOptionPane.PLAIN_MESSAGE);
        }
        break;
      case NOACCOUNT_MSG:
        String noAcctMsg = cmdMessage[1];
        logActivity("Alert: " + noAcctMsg);
        JOptionPane.showMessageDialog(null, noAcctMsg, "No auction account", JOptionPane.PLAIN_MESSAGE);
        break;
      case NOTIFY_MSG:
        String notifyMsg = cmdMessage[1];
        logActivity("Notify: " + notifyMsg);
        handleNotify(notifyMsg);
        break;
      case IGNORABLE_MSG:
        String configstr = cmdMessage[1];
        handleIgnorable(configstr);
        break;
      case ERROR_MSG:
        String errorMsg = cmdMessage[1];
        logActivity("Error: " + errorMsg);
        JOptionPane.showMessageDialog(null, errorMsg, "An error occurred", JOptionPane.PLAIN_MESSAGE);
        break;
      case INVALID_LOGIN_MSG:
        String rest = cmdMessage[1];
        handleInvalidLogin(rest);
        break;
      case PRICE:
        if (mFrame != null) {
          mFrame.setPrice(cmdMessage[1]);
        }
        break;
      default:
        String msg = (String) deQ;

        logActivity(msg);
        setStatus(msg);
        break;
    }
  }

  /**
   * Set the primary UI frame.
   *
   * @param frame The window (JFrame) that we are doing our primary display and UI for.
   */
  public void setMainFrame(MacFriendlyFrame frame) {
    mFrame = frame;
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

  private void logActivity(String action) {
    MQFactory.getConcrete("activity").enqueue(action);
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

  private void handleLoginStatus(String status) {
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
      String additionTooltip = "";
      if(!status.equals("SUCCESSFUL")) {
        String successMessage = status.substring("SUCCESSFUL ".length());
        additionTooltip = "\n " + successMessage;
        notifyAlert(successMessage);
      }
      toolBar.setToolTipText("Last login was successful." + additionTooltip);
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

  private void handleNotify(String notifyMsg) {
    if (Platform.isTrayEnabled()) {
      MQFactory.getConcrete("tray").enqueue(NOTIFY_MSG + " " + notifyMsg);
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
  }

  private static final int ONEK = 1024;

  private static final int UPDATE_FRAME_WIDTH = 640;
  private static final int UPDATE_FRAME_HEIGHT = 450;

  /**
   * @brief Announce that a new version is available, and let the user
   * decide what to do about it.
   */
  private static void announceNewVersion() {
    List<String> buttons = new ArrayList<>();
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

      /**
       * Handle the user's actions, in the dialog.
       *
       * @param listen_ae What the user did (clicked on) in the dialog.
       */
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

  private static long lastTime;

  /**
   * @brief Show the time once a second, in strikeout if the link to
   * the default auction server is down.
   */
  private void checkClock() {
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
    String headerLine = getLinkUp() ? defaultServerTime : "<strike>" + defaultServerTime + "</strike>";
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
}
