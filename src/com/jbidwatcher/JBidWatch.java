package com.jbidwatcher;
/*
 * Copyright (c) 2000-2005 CyberFOX Software, Inc. All Rights Reserved.
 *
 * This library is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Library General Public License as published
 * by the Free Software Foundation; either version 2 of the License, or (at
 * your option) any later version.
 *
 * This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Library General Public License for more
 * details.
 *
 * You should have received a copy of the GNU Library General Public License
 * along with this library; if not, write to the
 *  Free Software Foundation, Inc.
 *  59 Temple Place
 *  Suite 330
 *  Boston, MA 02111-1307
 *  USA
 */

import com.jbidwatcher.platform.Platform;
import com.jbidwatcher.platform.Tray;
import com.jbidwatcher.config.JConfig;
import com.jbidwatcher.config.JConfigTab;
import com.jbidwatcher.config.JConfigFrame;
import com.jbidwatcher.config.JBConfig;
import com.jbidwatcher.queue.*;
import com.jbidwatcher.xml.XMLElement;
import com.jbidwatcher.xml.JTransformer;
import com.jbidwatcher.util.html.JHTMLOutput;
import com.jbidwatcher.util.AudioPlayer;
import com.jbidwatcher.util.ErrorManagement;
import com.jbidwatcher.util.RuntimeInfo;
import com.jbidwatcher.ui.*;
import com.jbidwatcher.webserver.JBidProxy;
import com.jbidwatcher.webserver.SimpleProxy;
import com.jbidwatcher.search.SearchManager;
import com.jbidwatcher.auction.server.AuctionServer;
import com.jbidwatcher.auction.server.AuctionServerManager;
import com.jbidwatcher.auction.AuctionsManager;
import com.jbidwatcher.auction.ThumbnailManager;
import com.jbidwatcher.auction.server.ebay.ebayServer;

import java.io.*;
import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import javax.swing.event.*;
import java.util.*;
import java.util.List;
import java.net.*;

/**
 * @file   JBidWatch.java
 * @author Morgan Schweers <cyberfox@users.sourceforge.net>
 * @date   Fri Oct 11 17:54:21 2002
 *
 * @brief The startup class, that prepares the UI, and starts all the
 * threads.
 *
 * Perfection is reached, not when there is no longer anything to add, but
 * when there is no longer anything to take away.
 *                 -- Antoine de Saint-Exupery
 *
 */

/** Primary class which holds the main, and prepares and launches
 * all the subclasses and threads.  It also holds the general purpose
 * constants.  It implements JConfigListener, which just means that
 * the updateConfiguration() function will be called when the config
 * changes.
 * @noinspection FeatureEnvy,Singleton
 */
public final class JBidWatch implements JConfig.ConfigListener, MessageQueue.Listener {
  /** This ClassLoader is only REALLY necessary if we're loading the
   * initial display.cfg and JBidWatch.cfg from the distribution .jar
   * file.  See JConfig.java for more details.
   */
  private static ClassLoader urlCL = (ClassLoader)JBidWatch.class.getClassLoader();
  /** SimpleProxy is the internal web server proxy class.  This lets
   * us turn on or off the proxy, based on configuration changes.
   */
  private SimpleProxy sp;

  private static List<Object> gcSafe = new ArrayList<Object>();
  private JFrame mainFrame;
  private boolean _linkUp = true;
  private boolean _userValid;
  private JLabel _headerStatus;
  private JPanel _bidBarPanel;
  private JBidMenuBar _bidMenu;
  private JLabel _statusBar;
  private static final int SELECT_BOX_SIZE=20;
  private JTextField selectBox = new JTextField(SELECT_BOX_SIZE);
  private TimerHandler itemUpdateTimer;
  private TimerHandler searchTimer;
  private JTabManager jtmAuctions;
  private AuctionsManager aucManager;
  private SearchManager searchManager;

  private TimeQueueManager m_tqm = new TimeQueueManager();

  private static String _cfgLoad = "JBidWatch.cfg";
  private RuntimeInfo _rti;
  private static final int HOURS_IN_DAY = 24;
  private static final int MINUTES_IN_HOUR = 60;
  private static final int ONE_SECOND = Constants.ONE_SECOND;
  private static final int ONEK = 1024;
  private AudioPlayer m_ap;

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
   * @return - TODO
   */
  public boolean getLinkUp() {
    return _linkUp;
  }

  private final static String HEADER_MSG = "HEADER ";
  private final static String SNIPE_ALTERED_MSG = "SNIPECHANGED";
  private final static String QUIT_MSG = "QUIT";
  private final static String LINK_MSG = "LINK ";
  private final static String ERROR_MSG = "ERROR ";
  private static final String VISIBILITY_MSG = "VISIBILITY";
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
   *
   * This is the sole place that UI updates should be done, and all
   * requests to do UI activities should be sent via the MessageQueue
   * for "Swing".  This ensures that they are done on the Swing UI
   * update thread, instead of in random threads throughout the
   * program.  Since Swing is single-threaded, this is necessary.
   *
   * Messages supported (suffixed by _MSG) are:
   *          HEADER     (Draw text on the header (site time))
   *          LINK       (Identify whether the link is up/down)
   *          QUIT       (Shut down the program.)
   *          ERROR      (Show an error message.)
   *          NEWVERSION (Show an announcement about the new version!)
   *          NO_NEWVERSION (Note that no new version is ready.)
   *
   * anything else is presumed to be a status message, to be displayed
   * in the status bar at the bottom of the screen.
   */
  public void messageAction(Object deQ) {
    String msg = (String) deQ;
    if(msg.startsWith(HEADER_MSG)) {
      _headerStatus.setText(msg.substring(HEADER_MSG.length()));
      //      _headerStatus.paintImmediately(_headerStatus.getVisibleRect());
      FilterManager.getInstance().check();
    } else if(msg.startsWith(LINK_MSG)) {
      String linkStat = msg.substring(LINK_MSG.length());
      if(!gcSafe.isEmpty()) {
        setLinkUp(linkStat.startsWith("UP"));
        String rest = linkStat.substring(linkStat.startsWith("UP")?2:4);
        if (rest.length() == 0) {
          if(_userValid) _headerStatus.setToolTipText("");
        } else {
          //  Skip a 'space' at the start.
          rest = rest.substring(1);
          if(_userValid) _headerStatus.setToolTipText(rest);
        }
      }
    } else if(msg.equals(QUIT_MSG)) {
      shutdown();
    } else if(msg.equals(VISIBILITY_MSG)) {
      mainFrame.setVisible(!mainFrame.isVisible());
      MQFactory.getConcrete("tray").enqueue(mainFrame.isVisible()?"RESTORED":"HIDDEN");
      if(mainFrame.isVisible()) mainFrame.setState(Frame.NORMAL);
    } else if(msg.equals(HIDE_MSG)) {
      if(mainFrame.isVisible()) JConfig.snapshotDisplay(mainFrame);
      mainFrame.setVisible(false);

      if(Platform.isTrayEnabled()) {
        MQFactory.getConcrete("tray").enqueue("HIDDEN");
      }
    } else if(msg.equals(RESTORE_MSG)) {
      mainFrame.setVisible(true);
      if(Platform.isTrayEnabled()) {
        MQFactory.getConcrete("tray").enqueue("RESTORED");
      }
      mainFrame.setState(Frame.NORMAL);
    } else if(msg.equals(SNIPE_ALTERED_MSG)) {
      if(Platform.isTrayEnabled()) {
        StringBuffer snipeText = new StringBuffer("TOOLTIP ");
        AuctionServer.AuctionStats as = AuctionServerManager.getInstance().getDefaultServer().getStats();
        if(as.getSnipes() != 0) {
          snipeText.append("Next Snipe at: ").append(Constants.remoteClockFormat.format(as.getNextSnipe().getSnipeDate())).append('\n');
          snipeText.append(as.getSnipes()).append(" snipes outstanding\n");
        }
        if(as.getCompleted() != 0) {
          snipeText.append(as.getCompleted()).append(" auctions completed\n");
        }
        snipeText.append(as.getCount()).append(" auctions total");
        MQFactory.getConcrete("tray").enqueue(snipeText.toString());
      }
    } else if(msg.startsWith(ALERT_MSG)) {
      JOptionPane.showMessageDialog(null, msg.substring(ALERT_MSG.length()), "Alert", JOptionPane.PLAIN_MESSAGE);
    } else if(msg.startsWith(NOTIFY_MSG)) {
      if(Platform.isTrayEnabled()) {
        MQFactory.getConcrete("tray").enqueue(msg);
      } else {
        MQFactory.getConcrete("Swing").enqueue(msg.substring(NOTIFY_MSG.length()));
      }
    } else if(msg.startsWith(IGNORABLE_MSG)) {
      String configstr = msg.substring(IGNORABLE_MSG.length());
      int configLen = configstr.indexOf(' ');
      String realMsg = configstr.substring(configLen+1);
      configstr = configstr.substring(0, configLen);
      OptionUI oui = new OptionUI();
      oui.promptWithCheckbox(null, realMsg, "Alert", configstr, JOptionPane.PLAIN_MESSAGE, JOptionPane.OK_OPTION);
    } else if(msg.startsWith(ERROR_MSG)) {
      JOptionPane.showMessageDialog(null, msg.substring(ERROR_MSG.length()), "An error occurred", JOptionPane.PLAIN_MESSAGE);
    } else if(msg.equals(NEWVERSION_MSG)) {
      announceNewVersion();
    } else if(msg.startsWith(INVALID_LOGIN_MSG)) {
      _userValid = false;
      String rest = msg.substring(INVALID_LOGIN_MSG.length());
      if(rest.length() != 0) {
        //  Eliminate a space that's there for readibility.
        rest = rest.substring(1);
        _headerStatus.setToolTipText(rest);
      }
    } else if(msg.equals(START_UPDATING)) {
      MQFactory.getConcrete("Swing").enqueue("SNIPECHANGED");
      if(itemUpdateTimer == null) {
        //  This timer handles updating auctions and saving a snapshot of
        //  all the auctions.
        itemUpdateTimer = new TimerHandler(aucManager);
        itemUpdateTimer.setName("Updates");
        itemUpdateTimer.start();
      }

      if(searchTimer == null) {
        //  This thread / timer handles the periodic searching that the
        //  search feature allows to be set up.  Check only once a minute,
        //  because searching isn't a very time-critical feature.
        searchTimer = new TimerHandler(searchManager, Constants.ONE_MINUTE);
        searchTimer.setName("Searches");
        searchTimer.start();
      }
      if(!_userValid) {
        String msgType = ALERT_MSG;
        String destination = "Swing";
        if(Platform.isTrayEnabled()) {
          msgType = NOTIFY_MSG;
          destination = "tray";
        }
        MQFactory.getConcrete(destination).enqueue(msgType +
                                               "Not yet logged in.  Snipes will not fire until logging in\n" +
                                               "is successful.  Item updating has been enabled, but any\n" +
                                               "features that rely on being logged in will not work.");
      }
    } else if(msg.equals(VALID_LOGIN_MSG)) {
      MQFactory.getConcrete("Swing").enqueue("SNIPECHANGED");
      _userValid = true;
      _headerStatus.setToolTipText("");

      if(itemUpdateTimer == null) {
        //  This timer handles updating auctions and saving a snapshot of
        //  all the auctions.
        itemUpdateTimer = new TimerHandler(aucManager);
        itemUpdateTimer.setName("Updates");
        itemUpdateTimer.start();
      }

      if(searchTimer == null) {
        //  This thread / timer handles the periodic searching that the
        //  search feature allows to be set up.  Check only once a minute,
        //  because searching isn't a very time-critical feature.
        searchTimer = new TimerHandler(searchManager, Constants.ONE_MINUTE);
        searchTimer.setName("Searches");
        searchTimer.start();
      }
    } else if(msg.equals(NO_NEWVERSION_MSG)) {
      JOptionPane.showMessageDialog(null, "No new version available yet.\nKeep checking back!", "No new version", JOptionPane.PLAIN_MESSAGE);
    } else if(msg.equals(BAD_NEWVERSION_MSG)) {
      JOptionPane.showMessageDialog(null, "Failed to check for a new version\nProbably a temporary network issue; try again in a little while.",
                                    "Version check failed", JOptionPane.PLAIN_MESSAGE);
    } else if(msg.equals("TOOLBAR")) {
      _bidBarPanel.setVisible(!_bidBarPanel.isVisible());
      JConfig.setConfiguration("display.toolbar", _bidBarPanel.isVisible()?"true":"false");
      if (_bidBarPanel.isVisible()) {
        _headerStatus.setVisible(false);
        _bidBarPanel.add(_headerStatus, BorderLayout.EAST);
        _headerStatus.setVisible(true);
      } else {
        //  If it's a mac, the clock display can't move into the 'menu' component, because there isn't one!
        if (!Platform.isMac()) {
          _bidMenu.add(_headerStatus);
        }
      }
    } else {
      setStatus(msg);
    }
  }

  private static final int UPDATE_FRAME_WIDTH=512;
  private static final int UPDATE_FRAME_HEIGHT=350;

  /**
   * @brief Announce that a new version is available, and let the user
   * decide what to do about it.
   */
  private static void announceNewVersion() {
    List<String> buttons = new ArrayList<String>();
    buttons.add("Download");
    buttons.add("Ignore");

    final UpdaterEntry ue = UpdateManager.getInstance().getUpdateInfo();
    StringBuffer fullMsg = new StringBuffer(4*ONEK);
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
          if(actionString.equals("Download")) {
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

  private Date mNow = new Date();
  private Calendar mCal = new GregorianCalendar();
  /**
   * @brief Sets the text in the status bar on the bottom of the screen.
   *
   * @param newStatus The text to place on the status line.
   */
  private void setStatus(String newStatus) {
    mNow.setTime(System.currentTimeMillis());
    String defaultServerTime = AuctionServerManager.getInstance().getDefaultServerTime();
    String bracketed = " [" + defaultServerTime + '/' + Constants.localClockFormat.format(mNow) + ']';
    if(JConfig.queryConfiguration("timesync.enabled", "true").equals("false")) {
      TimeZone tz = AuctionServerManager.getInstance().getDefaultServer().getOfficialServerTimeZone();
      if(tz != null && tz.hasSameRules(mCal.getTimeZone())) {
        bracketed = " [" + Constants.localClockFormat.format(mNow) + ']';
      }
    }

    String statusToDisplay = newStatus + bracketed;

    if(_statusBar != null) {
      _statusBar.setText("<html><body>" + statusToDisplay + "</body></html>");
      _statusBar.paintImmediately(_statusBar.getVisibleRect());
    } else {
      ErrorManagement.logDebug(newStatus + bracketed);
    }
  }

  /**
   * @brief Try to guarantee a directory for saving 'cached copies'
   * and eventually configuration information.
   *
   * First we try the passed in directory, if it doesn't exist, try to
   * create it.  If that fails, then try to create a 'default'
   * directory.  If THAT fails, we're in trouble.  We return null, and
   * expect everything else to handle it properly.  (Not try to save
   * anything there.)  We also note an error in the logs...
   *
   * @param inPath - The 'preferred' path to use.
   *
   * @return - a String identifying the path to our save directory.
   */
  private static String makeSaveDirectory(String inPath) {
    return makeStandardDirectory(inPath, "auctionsave", "jbidwatcher");
  }

  private static String makePlatformDirectory(String inPath) {
    return makeStandardDirectory(inPath, "platform", "jbidwatcher");
  }

  private static String makeStandardDirectory(String inPath, String defaultSubdir, String defaultDirectory) {
    String outPath = inPath;

    if(outPath != null) {
      File fp_test = new File(outPath);
      if(!fp_test.exists()) {
        if(!fp_test.mkdirs()) {
          outPath = null;
        }
      }
    }

    if(outPath == null) {
      String directoryPath = JConfig.getCanonicalFile(defaultSubdir, defaultDirectory, false);
      File fp = new File(directoryPath);

      if(fp.exists()) {
        outPath = fp.getAbsolutePath();
      } else {
        fp.mkdirs();
        outPath = fp.getAbsolutePath();
      }
    }

    return outPath;
  }

  private static void getUserSetup() {
    JConfig.setConfiguration("config.firstrun", "true");
    JConfigFrame jcf = new JConfigFrame();
    jcf.spinWait();
  }

  /**
   * @brief Load a configuration, if possible; if not, load the
   * configuration from the .jar file.
   *
   * The full logic sequence is as follows:
   *     Find the best location for the file.
   *     If it's not any of those, load it from the .jar file.
   *
   * @param inConfig - The configuration file to try to load.
   *
   * @return - TODO
   */
  private static InputStream checkConfig(String inConfig) {
    JConfig.setConfigurationFile(inConfig);
    return JConfig.bestSource(urlCL, inConfig);
  }

  private static void loadConfig(InputStream configStream) {
    JConfig.load(configStream);

    //  This MUST be run before any UI objects are addressed, if at all possible.
    //  In the case of the initial configuration, unfortunately it's not possible.
    Platform.setupMacUI();

    JConfig.loadDisplayConfig(urlCL);

    String aucSave = makeSaveDirectory(JConfig.queryConfiguration("auctions.savepath"));
    if(aucSave != null) {
      JConfig.setConfiguration("auctions.savepath", aucSave);
    }

    String platform = makePlatformDirectory(JConfig.queryConfiguration("platform.path"));
    if(platform != null) {
      JConfig.setConfiguration("platform.path", platform);
      if(Platform.isWindows()) {
        if(Platform.extractAndLoadLibrary()) {
          Platform.setTrayEnabled(true);
        }
      }
    }
  }

  /**
   * @brief Check to see if the user tried to use any of the help parameters.
   *
   * @param inArgs - The arguments passed into the command line.
   *
   * @return - true if the usage was displayed, false otherwise.
   */
  private static boolean CheckHelp(String[] inArgs) {
    if(inArgs.length != 0) {
      if(inArgs[0].startsWith("--help") ||
         inArgs[0].startsWith("-h")) {
        //noinspection UseOfSystemOutOrSystemErr
        System.out.println("usage: java JBidWatch [{cfg-file}]");
        JOptionPane.showMessageDialog(null, "<html><body>usage:<br><center>java JBidWatch [{cfg-file}]</center><br>Default user home: " +
                                            System.getProperty("user.home") + "</body></html>", "Help display", JOptionPane.PLAIN_MESSAGE);
        return true;
      }
    }

    return false;
  }

  /**
   * @brief Set the proxy values if they are indicated by the configuration.
   *
   * @param inProps - The properties list to check.
   *
   * @return - true if proxies were set, false otherwise.
   */
  private static boolean EstablishProxy(Properties inProps) {
    String webProxyHost = JConfig.queryConfiguration("proxy.host", null);
    String webProxyPort = JConfig.queryConfiguration("proxy.port", null);

    if(JConfig.queryConfiguration("proxyfirewall", "none").equals("proxy")) {
      if (webProxyHost != null && webProxyPort != null) {
        inProps.setProperty("http.proxySet", "true");
        inProps.setProperty("http.proxyHost", webProxyHost);
        inProps.setProperty("http.proxyPort", webProxyPort);

        inProps.setProperty("proxySet", "true");
        inProps.setProperty("proxyHost", webProxyHost);
        inProps.setProperty("proxyPort", webProxyPort);
        return true;
      }
    }
    return false;
  }

  private static boolean EstablishHTTPSProxy(Properties inProps) {
    if(JConfig.queryConfiguration("proxy.https.set", "false").equals("true")) {
      String secureProxyHost = JConfig.queryConfiguration("proxy.https.host");
      String secureProxyPort = JConfig.queryConfiguration("proxy.https.port");
      if(secureProxyHost != null && secureProxyPort != null) {
        inProps.setProperty("https.proxySet", "true");
        inProps.setProperty("https.proxyHost", secureProxyHost);
        inProps.setProperty("https.proxyPort", secureProxyPort);
        return true;
      }
    }
    return false;
  }

  /**
   * @brief Set the firewall values, if they are indicated by the configuration.
   *
   * @param inProps - The properties list to check.
   *
   * @return - true if the firewall info was set, false otherwise.
   */
  private static boolean EstablishFirewall(Properties inProps) {
    if(JConfig.queryConfiguration("proxyfirewall", "none").equals("firewall")) {
      String socksHost = JConfig.queryConfiguration("firewall.host", null);
      String socksPort = JConfig.queryConfiguration("firewall.port", "1080");//  Default SOCKS port.
      if(socksHost != null) {
        inProps.setProperty("socksProxyHost", socksHost);
        inProps.setProperty("socksProxyPort", socksPort);

        return true;
      }
    }
    return false;
  }

  /**
   * @brief Set the UI to be used for the Swing L&F.
   *
   * @param whichUI - The name of the UI to use.
   *
   * @param inFrame - The base frame of the application, to indicate
   * that the UI has changed.
   *
   * @param lafList - TODO
   */
  private static void setUI(String whichUI, JFrame inFrame, UIManager.LookAndFeelInfo[] lafList) {
    String whatLaF = null;

    if(whichUI != null) {
      for (UIManager.LookAndFeelInfo aLafList : lafList) {
        if (whichUI.equals(aLafList.getName())) whatLaF = aLafList.getClassName();
      }
    }

    //  If we still haven't chosen a L&F, set to the system default.
    if(whatLaF == null) {
      if( (whatLaF = System.getProperty("swing.defaultlaf")) == null) {
        whatLaF = UIManager.getSystemLookAndFeelClassName();
      }
    }

    Platform.checkLaF(whatLaF);

    try {
      UIManager.setLookAndFeel(whatLaF);
      if(inFrame != null) {
        SwingUtilities.updateComponentTreeUI(inFrame);
      }
    } catch(Exception exMe) {
      ErrorManagement.handleException("Exception in setUI, failure to set " + whatLaF + ": " + exMe, exMe);
    }
  }

  /**
   * @brief Checks for help, loads the configuration, sets up system
   * properties like proxies or firewalls, initializes the splash
   * screen, and launches the application proper.
   *
   * @param args Command line arguments.
   */
  public static void main(String[] args) {
    //  Check for a parameter (--help or -h) to show help for.
    if( CheckHelp(args) ) {
      System.exit(0);
    }

    System.setProperty("sun.net.client.defaultConnectTimeout", "5000");
    System.setProperty("sun.net.client.defaultReadTimeout", "15000");

    //  Pass a parameter (other than --help or -h) to launch that as a
    //  configuration file.
    if (args.length != 0) {
      if(args[0].charAt(0) != '-') {
        _cfgLoad = args[0];
      }
    }

    _cfgLoad = JConfig.getCanonicalFile(_cfgLoad, "jbidwatcher", false);
    boolean ebayLoaded = false;
    InputStream configStream = checkConfig(_cfgLoad);
    boolean needUserSetup = (configStream == null);
    boolean firstRun;
    if (needUserSetup) {
      setUI(null, null, UIManager.getInstalledLookAndFeels());
      //  Preload the eBay server, must be done before Configuration setup
      //  could happen, to get the configuration tab for eBay.
      AuctionServerManager.getInstance().addServer("ebay", new ebayServer());
      ebayLoaded = true;
      Platform.setupMacUI();
      JConfig.setConfiguration("first.run", "true");
      firstRun = true;
      getUserSetup();
      configStream = checkConfig(_cfgLoad);
    } else {
      JConfig.setConfiguration("first.run", "false");
      firstRun = false;
    }
    loadConfig(configStream);
    JConfig.setConfiguration("first.run", firstRun?"true":"false");
    if(args.length > 0 && args[0] != null && args[0].equals("-transform")) {
      String outName;
      if(args.length == 1 || args[1] == null) {
        outName = JConfig.getCanonicalFile("auctions.html", "jbidwatcher", false);
      } else {
        outName = args[1];
      }
      JTransformer.outputHTML(JConfig.queryConfiguration("savefile", "auctions.xml"), outName);
      System.exit(0);
    }
    setUI(null, null, UIManager.getInstalledLookAndFeels());
    if(!ebayLoaded) AuctionServerManager.getInstance().addServer("ebay", new ebayServer());

    loadProxySettings();

    if(JConfig.queryConfiguration("show.badhtml", "false").equals("true")) {
      XMLElement.rejectBadHTML(true);
    }

    //  Show splash screen and progress bar.
    Calendar rightNow = Calendar.getInstance();
    int _mon = rightNow.get(Calendar.MONTH);
    int _day = rightNow.get(Calendar.DAY_OF_MONTH);
    URL imageURL;

    if( (_day == 1 && _mon == Calendar.APRIL) &&
        !JConfig.queryConfiguration("sniperkitty", "false").equals("true")) {
      imageURL = urlCL.getResource("/jbidwatch_apr1.jpg");
      JConfig.setConfiguration("sniperkitty", "true");
    } else {
      imageURL = urlCL.getResource(JConfig.queryConfiguration("splash", "jbidwatch.jpg"));
    }
    JSplashScreen Splash = new JSplashScreen(new ImageIcon(imageURL));

    try {
      JBidWatch program = new JBidWatch(Splash);
      gcSafe.add(program);
      Thread.currentThread().join();

      program.repaint();
    } catch(Exception e) {
      ErrorManagement.handleException("JBidWatcher: " + e, e);
    }
  }

  private void repaint() {
    mainFrame.repaint();
  }

  private static void loadProxySettings() {
    Properties sysProps = System.getProperties();

    boolean proxied = EstablishProxy(sysProps);
    boolean firewalled = EstablishFirewall(sysProps);
    boolean secured = EstablishHTTPSProxy(sysProps);

    boolean sysPropsChanged = proxied || firewalled || secured;

    if(sysPropsChanged) System.setProperties(sysProps);
  }

  /**
   * @brief Obtains a 'property list' of all the column widths, names,
   * etc., in order to save them off so the UI can remain
   * approximately the same between executions.
   *
   * @return A property table of all the table column header information, suitable for saving.
   */
  public static Properties getColumnProperties() {
    Properties colProps = new Properties();

    colProps = FilterManager.getInstance().extractProperties(colProps);

    return(colProps);
  }

  /**
   * @brief Save the display properties, the configuration, the
   * auctions, and exit.  This exists to prompt for shutdown
   * if there are any outstanding snipes.
   */
  public void shutdown() {
    if(AuctionsManager.getInstance().anySnipes()) {
      OptionUI oui = new OptionUI();
    //  Use the right parent!  FIXME -- mrs: 17-February-2003 23:53
      int rval = oui.promptWithCheckbox(null, "There are outstanding snipes that will not be able to fire while " + Constants.PROGRAM_NAME +
                                              " is not running.  Are you sure you want to quit?", "Pending Snipes confirmation",
                                                                                                  "prompt.snipe_quit");
      if(rval == JOptionPane.CANCEL_OPTION) return;
    }

    internal_shutdown();
  }

  public Point getOrigin() {
    return mainFrame.getLocationOnScreen();
  }

  public void internal_shutdown() {
    Properties colProps = getColumnProperties();
    SearchManager.getInstance().saveSearchDisplay();
    JConfig.saveDisplayConfig(mainFrame, colProps);

    //  Save it to the original file, if it was provided at runtime,
    //  otherwise to the appropriate default.
    String cfgFilename = _cfgLoad.equals("JBidWatch.cfg") ? JConfig.getCanonicalFile(_cfgLoad, "jbidwatcher", false) : _cfgLoad;

    AuctionsManager.getInstance().saveAuctions();
    SearchManager.getInstance().saveSearches();
    AuctionServer.AuctionStats as = AuctionServerManager.getInstance().getDefaultServer().getStats();
    JConfig.setConfiguration("last.auctioncount", Integer.toString(as.getCount()));
    JConfig.saveConfiguration(cfgFilename);
    System.exit(0);
  }

  static long lastTime;

  /**
   * @brief Show the time once a second, in strikeout if the link to
   * the default auction server is down.
   */
  private void checkClock() {
    if(lastTime != 0) {
      if( (lastTime + Constants.ONE_MINUTE) < System.currentTimeMillis()) {
        //  We've been out for more than a minute!
        ErrorManagement.logDebug("We appear to be waking from sleep...  Networking is probably not up yet.");
        if(AuctionsManager.getInstance().anySnipes()) {
          ErrorManagement.logDebug("There were snipes outstanding; hopefully none were supposed to fire during the sleep period...");
        }
        m_tqm.add(new AuctionQObject(AuctionQObject.MENU_CMD, AuctionServer.UPDATE_LOGIN_COOKIE, null), "ebay", System.currentTimeMillis() + (10 * Constants.ONE_SECOND));
      }
    }
    lastTime = System.currentTimeMillis();
    String defaultServerTime = AuctionServerManager.getInstance().getDefaultServerTime();

    if(!_userValid) {
      defaultServerTime = "Not logged in...";
    }
    String headerLine = _linkUp ? defaultServerTime : "<html><strike>" + defaultServerTime + "</strike></html>";

    MQFactory.getConcrete("Swing").enqueue("HEADER " + headerLine);
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
  private static void addbutton(JToolBar jtb, ActionListener inAction, String buttonName, String buttonImage, String buttonTip) {
    JButton newButton = makeButton(buttonImage, buttonTip, buttonName, inAction, false);

    jtb.add(newButton);
  }

  public static JButton makeButton(String buttonImage, String buttonTip, String buttonName, ActionListener inAction, boolean shrink) {
    JButton newButton = new JButton();
    ImageIcon newImage = new ImageIcon(JBidWatch.class.getResource(buttonImage));

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
  private JPanel buildHeaderBar(JFrame inFrame, ActionListener inAction) {
    _bidBarPanel = new JPanel(new BorderLayout());
    _bidMenu = JBidMenuBar.getInstance(inAction, "JBidwatcher");

    _headerStatus = new JLabel("", SwingConstants.RIGHT);
    inFrame.setJMenuBar(_bidMenu);
    AuctionServerManager.getInstance().addAuctionServerMenus();

    _bidMenu.add(Box.createHorizontalGlue());

    JToolBar _bidBar = new JToolBar();

    _bidBarPanel.setBorder(BorderFactory.createEtchedBorder());
    _bidBarPanel.add(_headerStatus, BorderLayout.EAST);

    addbutton(_bidBar, inAction, "Add", "/icons/add_auction.gif", "Add auction");
    addbutton(_bidBar, inAction, "Delete", "/icons/delete.gif", "Delete Auction");

    addbutton(_bidBar, inAction, "Search", "/icons/find.gif", "Auction Search Manager");

    addbutton(_bidBar, inAction, "Information", "/icons/information.gif", "Get information");

    addbutton(_bidBar, inAction, "UpdateAll", "/icons/updateall.gif", "Update All Auctions");
    addbutton(_bidBar, inAction, "StopUpdating", "/icons/stopupdating.gif", "Stop Updating Auctions");

    addbutton(_bidBar, inAction, "Configure", "/icons/configuration.gif", "Configure");
    addbutton(_bidBar, inAction, "Save", "/icons/save.gif", "Save Auctions");

    //      addbutton(_bidBar, inAction, "GetMyEbay", "getmyebay.gif", "Get My eBay");

    addbutton(_bidBar, inAction, "Help", "/icons/help.gif", "Help");
    addbutton(_bidBar, inAction, "About", "/icons/about.gif", "About JBidWatcher");

    if(JConfig.queryConfiguration("toolbar.floater", "false").equals("false")) {
      _bidBar.setFloatable(false);
    }


    //    This feature is only available in 1.4+!  Crap...  It'd be nice.
    //    _bidBar.setRollover(true);

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
          jtmAuctions.selectBySearch(selectBox.getText());
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
   * @brief Callback called by JConfig when the configuration changes.
   *
   * We're very interested in whether the internal webserver is
   * activated or not.  If it changes state, we start or stop the
   * server.
   *
   * Also, we check the background color, and set it appropriately.
   */
  public final void updateConfiguration() {
    int localServer_port = Integer.parseInt(JConfig.queryConfiguration("server.port", Constants.DEFAULT_SERVER_PORT_STRING));

    String savedBGColor = JConfig.queryConfiguration("background", "false");
    if(!savedBGColor.equals("false")) {
      FilterManager.getInstance().setBackground(Color.decode('#' + savedBGColor));
    }

    //  Enable the internal server, if it's set.
    if(JConfig.queryConfiguration("server.enabled", "false").equals("true")) {
      if(sp == null) {
        sp = new SimpleProxy(localServer_port, JBidProxy.class, null);
      }

      sp.go();
    } else {
      if(sp != null) {
        sp.halt();
      }
    }
    loadProxySettings();

    if(JConfig.queryConfiguration("debug.memory", "false").equals("true")) {
      if(_rti == null) {
        _rti = new RuntimeInfo();
      } else {
        _rti.setVisible(true);
      }
    } else {
      if(_rti != null) _rti.setVisible(false);
    }

    MQFactory.getConcrete("ebay").enqueue(new AuctionQObject(AuctionQObject.LOAD_STRINGS, "Reload the strings.", null));
  }

  /**
   * @brief Constructs a new window frame, with all the sorted tables,
   * scroll bars, drag and drop targets, menu & header bar, and status
   * line.
   *
   * @return A completed frame, suitable for displaying as the primary UI of the program.
   */
  private JFrame buildFrame() {
    class JFriendlyFrame
      extends JFrame
      implements com.apple.mrj.MRJQuitHandler, com.apple.mrj.MRJAboutHandler, com.apple.mrj.MRJPrefsHandler {
      JFriendlyFrame(String title) {
        super(title);

        com.apple.mrj.MRJApplicationUtils.registerQuitHandler(this);
        com.apple.mrj.MRJApplicationUtils.registerAboutHandler(this);
        com.apple.mrj.MRJApplicationUtils.registerPrefsHandler(this);
      }

      public void handleQuit() {
        if(AuctionsManager.getInstance().anySnipes()) {
          MQFactory.getConcrete("Swing").enqueue(QUIT_MSG);
          //  Please wait, we'll be ready to quit shortly.
          throw new IllegalStateException("Ne changez pas mains, il viendra bient�t.");
        } else {
          //  Known bug -- If the user has said, 'Never show me this
          //  prompt' for the outstanding snipes warning, we will
          //  still go to that code path on shutdown if there are
          //  outstanding snipes.  A 'would we show this message'
          //  function would be cool. -- mrs: 24-November-2003 00:31
          internal_shutdown();
        }
      }

      public void handleAbout() {
        MQFactory.getConcrete("user").enqueue("About");
      }

      public void handlePrefs() {
        MQFactory.getConcrete("user").enqueue("Configure");
      }

      /**
       * author: Dan Caprioara on java-dev at lists.apple.com.
       *
       * There is a bug on Mac OS X, when a WindowAdapter
       * (apple.laf.AquaMenuBarUI$FixupMenuBarWindowAdapter) is
       * registered repeatedly to the frame, without checking it was
       * already added. This is done by the apple LF.<p>
       *
       * Here the window listener is first removed, and then added.
       */
      public synchronized void addWindowListener(WindowListener listen) {
        super.removeWindowListener(listen);
        super.addWindowListener(listen);
      }
    }

    JMouseAdapter myFrameAdapter = new JBidFrameMouse();

    _statusBar = new JLabel("Ready!", SwingConstants.LEFT);

    JFriendlyFrame newFrame = new JFriendlyFrame("JBidwatcher");
    newFrame.addMouseListener(myFrameAdapter);

    newFrame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);

    URL iconURL = urlCL.getResource(JConfig.queryConfiguration("icon", "jbidwatch64.jpg"));
    newFrame.setIconImage(new ImageIcon(iconURL).getImage());

    jtmAuctions = AuctionsUIModel.getTabManager();
    JPanel _headerBar = buildHeaderBar(newFrame, jtmAuctions);

    newFrame.getContentPane().add(jtmAuctions.getTabs());
    newFrame.getContentPane().add(_statusBar, BorderLayout.SOUTH);
    newFrame.getContentPane().add(_headerBar, BorderLayout.NORTH);

    newFrame.pack();

    newFrame.addWindowListener(new WindowAdapter() {
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
        MQFactory.getConcrete("Swing").enqueue(QUIT_MSG);
      }
    });

    return newFrame;
  }

  /**
   * @brief Load the saved auctions, build the UI frame, close down
   * the splash screen, and start the monitor and update threads.
   *
   * @param inSplash Splash screen with a status bar, to be updated during startup.
   *
   * @noinspection CallToThreadStartDuringObjectConstruction
   */
  private JBidWatch(JSplashScreen inSplash) {
    ThumbnailManager.getInstance();
    FilterManager.getInstance().loadFilters();

    aucManager = AuctionsManager.getInstance();
    aucManager.loadAuctions(inSplash);
    //  This needs to be after the auction manager, so that all the
    //  auction servers that are loaded by loading auctions will be
    //  available to add searches if they need to.
    searchManager = SearchManager.getInstance();
    searchManager.loadSearches();

    AuctionServerManager.getInstance().getDefaultServerTime();

    JConfig.registerListener(this);
    MQFactory.addQueue("Swing", new SwingMessageQueue());
    MQFactory.getConcrete("Swing").registerListener(this);

    String defaultServer = AuctionServerManager.getInstance().getDefaultServer().getName();
    MQFactory.getConcrete(defaultServer).enqueue(new AuctionQObject(AuctionQObject.MENU_CMD, AuctionServer.UPDATE_LOGIN_COOKIE, null)); //$NON-NLS-1$

    //  Register the handler for all 'drop' events.
    gcSafe.add(new JBWDropHandler());
    gcSafe.add(new JBConfig());

    mainFrame = buildFrame();

    mainFrame.setLocation(JConfig.screenx, JConfig.screeny);
    mainFrame.setSize(JConfig.width, JConfig.height);

    inSplash.close();
    //noinspection UnusedAssignment
    inSplash = null;

    mainFrame.setVisible(true);

    //  Construct the tray object, so that we can interact with the system tray.
    if(Platform.isWindows()) {
      gcSafe.add(new Tray());
      if(JConfig.queryConfiguration("windows.tray", "true").equals("true")) {
        MQFactory.getConcrete("tray").enqueue("TRAY on");
      }
    }

    //  Start any servers if necessary, and set the background colors,
    //  and anything else we need to load from the configuration file.
    updateConfiguration();

    //m_tqm.add("This is a message for the display!", "Swing", System.currentTimeMillis()+Constants.ONE_MINUTE);
    //m_tqm.add(JBidMouse.ADD_AUCTION + "5582606163", "user", System.currentTimeMillis() + (Constants.ONE_MINUTE / 2));

    long now = System.currentTimeMillis();
    //  Don't show the affiliate question on the first run.
    if(JConfig.queryConfiguration("first.run", "true").equals("false")) {
      //  Don't show it for pre-releases.
      if(Constants.PROGRAM_VERS.indexOf("pre") == -1) {
        //  Don't show it if the user has already turned it on, or has already seen it.
        if(JConfig.queryConfiguration("ebay.affiliate", "false").equals("false") &&
           JConfig.queryConfiguration("prompt.affiliate", "true").equals("true")) {
          if(JConfig.queryConfiguration("ebay.affiliate.override", "false").equals("true")) m_tqm.add("Affiliate", "user", now + (ONE_SECOND * 5));
        }
      }
    }

    if(JConfig.queryConfiguration("ebay.affiliate", "false").equals("true") &&
       JConfig.queryConfiguration("informed.affiliate_over", "false").equals("false")) {
      m_tqm.add("NoAffiliate", "user", now + (ONE_SECOND * 5));
    }

    if(JConfig.queryConfiguration("updates.enabled", "true").equals("true")) {
      m_tqm.add("CHECK", "update", now + (ONE_SECOND * 10));
    }
    //noinspection MultiplyOrDivideByPowerOfTwo
    if (JConfig.queryConfiguration("timesync.enabled", "true").equals("true"))
      m_tqm.add("TIMECHECK", "auction_manager", now + (ONE_SECOND * 2), Constants.THIRTY_MINUTES);
    m_tqm.add(new AuctionQObject(AuctionQObject.MENU_CMD, AuctionServer.UPDATE_LOGIN_COOKIE, null), "ebay", now + ONE_SECOND*3, 120 * Constants.ONE_MINUTE);
    m_tqm.add(START_UPDATING, "Swing", now + (ONE_SECOND * 2 * 10));
    //m_tqm.add("http://www.jbidwatcher.com", "browse", System.currentTimeMillis() + (Constants.ONE_MINUTE / 4));
    //m_tqm.add(new AuctionQObject(AuctionQObject.BID, new AuctionBid("5582606251", Currency.getCurrency("2.99"), 1), "none"), "ebay",
    //          System.currentTimeMillis() + (Constants.ONE_MINUTE*2) );

    TimerHandler timeQueue = new TimerHandler(m_tqm);
    timeQueue.setName("SuperQueue");
    timeQueue.start();
    gcSafe.add(timeQueue);

    //  Because the program is starting to get widely spread around,
    //  and I can't control the version numbers everywhere, this
    //  should monitor a certain location once a day and look for an
    //  update.  The user MUST have the option to turn this off!
    TimerHandler updateTimer = new TimerHandler(UpdateManager.getInstance(), HOURS_IN_DAY * MINUTES_IN_HOUR * Constants.ONE_MINUTE);
    updateTimer.setName("VersionChecker");
    updateTimer.start();
    gcSafe.add(updateTimer);

    TimerHandler clockTimer = new TimerHandler(new TimerHandler.WakeupProcess() {
      public boolean check() {
        checkClock();
        return true;
      }
    });
    clockTimer.setName("Clock");
    clockTimer.start();
    gcSafe.add(clockTimer);

    m_ap = new AudioPlayer();
    gcSafe.add(m_ap);

    if(JConfig.queryConfiguration("debug.memory", "false").equals("true")) _rti = new RuntimeInfo();
  }
}
