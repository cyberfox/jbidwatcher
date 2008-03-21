package com.jbidwatcher.app;
/*
 * Copyright (c) 2000-2007, CyberFOX Software, Inc. All Rights Reserved.
 *
 * Developed by mrs (Morgan Schweers)
 */

import com.jbidwatcher.auction.*;
import com.jbidwatcher.auction.FilterManager;
import com.jbidwatcher.auction.server.AuctionServer;
import com.jbidwatcher.auction.server.AuctionServerManager;
import com.jbidwatcher.auction.server.ebay.ebayServer;
import com.jbidwatcher.AudioPlayer;
import com.jbidwatcher.platform.Browser;
import com.jbidwatcher.util.config.*;
import com.jbidwatcher.util.config.ErrorManagement;
import com.jbidwatcher.ui.config.JConfigFrame;
import com.jbidwatcher.ui.config.JConfigTab;
import com.jbidwatcher.platform.Platform;
import com.jbidwatcher.platform.Tray;
import com.jbidwatcher.search.SearchManager;
import com.jbidwatcher.ui.*;
import com.jbidwatcher.ui.RuntimeInfo;
import com.jbidwatcher.ui.util.JBidFrame;
import com.jbidwatcher.ui.util.JMouseAdapter;
import com.jbidwatcher.util.*;
import com.jbidwatcher.util.Constants;
import com.jbidwatcher.util.ActivityMonitor;
import com.jbidwatcher.util.queue.*;
import com.jbidwatcher.util.queue.TimerHandler;
import com.jbidwatcher.util.db.DBManager;
import com.jbidwatcher.webserver.JBidProxy;
import com.jbidwatcher.webserver.SimpleProxy;
import com.jbidwatcher.util.xml.XMLElement;
import com.jbidwatcher.*;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.util.*;
import java.util.List;

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
 */

/** Primary class which holds the main, and prepares and launches
 * all the subclasses and threads.  It also holds the general purpose
 * constants.  It implements JConfigListener, which just means that
 * the updateConfiguration() function will be called when the config
 * changes.
 * @noinspection FeatureEnvy,Singleton
 */
public final class JBidWatch implements JConfig.ConfigListener {
  /** This ClassLoader is only REALLY necessary if we're loading the
   * initial display.cfg and JBidWatch.cfg from the distribution .jar
   * file.  See JConfig.java for more details.
   */
  private static ClassLoader urlCL = (ClassLoader)JBidWatch.class.getClassLoader();
  /** SimpleProxy is the internal web server proxy class.  This lets
   * us turn on or off the proxy, based on configuration changes.
   */
  private SimpleProxy sp;

  private MacFriendlyFrame mainFrame;
  private JTabManager jtmAuctions;

  private RuntimeInfo _rti;
  private static final int HOURS_IN_DAY = 24;
  private static final int MINUTES_IN_HOUR = 60;

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
    List<JConfigTab> serverTabs = AuctionServerManager.getInstance().getServerConfigurationTabs();
    JConfigFrame jcf = new JConfigFrame(serverTabs);
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
   * @return - The input stream corresponding with the best version of the provided file we can find.
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

    Dimension screensize = Toolkit.getDefaultToolkit().getScreenSize();

    JConfig.loadDisplayConfig(urlCL, screensize.width, screensize.height);

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
   * Check for a parameter (--help or -h) to show help for.
   *
   * @param inArgs - The arguments passed into the command line.
   *
   * @return - true if the usage was displayed, false otherwise.
   */
  private static boolean checkArguments(String[] inArgs) {
    boolean rval = false;
    for(String arg : inArgs) {
      rval |= handleArgument(arg);
    }

    return rval;
  }

  private static boolean handleArgument(String arg) {
    if (arg.startsWith("--help") || arg.startsWith("-h")) {
      //noinspection UseOfSystemOutOrSystemErr
      System.out.println("usage: java JBidWatch [{cfg-file}]");
      JOptionPane.showMessageDialog(null, "<html><body>usage:<br><center>java JBidWatch [{cfg-file}]</center><br>Default user home: " +
          JConfig.getHome() + "</body></html>", "Help display", JOptionPane.PLAIN_MESSAGE);
      return true;
    } else if (arg.startsWith("--test-ruby")) {
      try {
        Scripting.initialize();
        Scripting.ruby("require 'jbidwatcher/utilities'");
        Scripting.rubyMethod("play_around", "Zarf");
      } catch (Exception e) { e.printStackTrace(); }
      return true;
    } else if (arg.startsWith("--usb")) {
      JConfig.setHome(System.getProperty("user.dir"));
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
   * @param lafList - The list of look-and-feels from which to choose.
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

    if(Platform.isMac() && Platform.setQuaquaFeel(inFrame)) {
      whatLaF = null;
    }

    if(whatLaF != null) {
      try {
        UIManager.setLookAndFeel(whatLaF);
      } catch (Exception exMe) {
        ErrorManagement.handleException("Exception in setUI, failure to set " + whatLaF + ": " + exMe, exMe);
        //  Don't try to update the frame with the new UI.
        inFrame = null;
      }
    }

    if (inFrame != null) {
      SwingUtilities.updateComponentTreeUI(inFrame);
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
    String cfgLoad = "JBidWatch.cfg";

    if(checkArguments(args)) {
      System.exit(0);
    }

    JConfig.setVersion(Constants.PROGRAM_VERS);

    System.setProperty("sun.net.client.defaultConnectTimeout", "5000");
    System.setProperty("sun.net.client.defaultReadTimeout", "15000");

    //  Pass a parameter (other than --help or -h) to launch that as a
    //  configuration file.
    if (args.length != 0) {
      if(args[0].charAt(0) != '-') {
        cfgLoad = args[0];
      }
    }

    cfgLoad = JConfig.getCanonicalFile(cfgLoad, "jbidwatcher", false);
    boolean ebayLoaded = false;
    InputStream configStream = checkConfig(cfgLoad);
    boolean needUserSetup = (configStream == null);
    boolean firstRun;
    if (needUserSetup) {
      setUI(null, null, UIManager.getInstalledLookAndFeels());
      //  Preload the eBay server, must be done before Configuration setup
      //  could happen, to get the configuration tab for eBay.
      AuctionServerManager.getInstance().addServer(new ebayServer());
      ebayLoaded = true;
      Platform.setupMacUI();
      JConfig.setConfiguration("first.run", "true");
      firstRun = true;
      getUserSetup();
      configStream = checkConfig(cfgLoad);
    } else {
      JConfig.setConfiguration("first.run", "false");
      firstRun = false;
    }
    JConfig.setConfiguration("temp.cfg.load", cfgLoad);
    loadConfig(configStream);
    JConfig.setConfiguration("first.run", firstRun?"true":"false");
    if(args.length > 0 && args[0] != null && args[0].equals("-transform")) {
      String outName;
      if(args.length == 1 || args[1] == null) {
        outName = JConfig.getCanonicalFile("auctions.html", "jbidwatcher", false);
      } else {
        outName = args[1];
      }
      AuctionTransformer.outputHTML(JConfig.queryConfiguration("savefile", "auctions.xml"), outName);
      System.exit(0);
    }
    setUI(null, null, UIManager.getInstalledLookAndFeels());

    try {
      Upgrader.upgrade();
    } catch(Exception e) {
      if (e.getMessage().matches("^Failed to start database.*")) {
        JOptionPane.showMessageDialog(null, "JBidwatcher can't access its database.\nPlease check to see if you are running another instance.", "Can't access auction database", JOptionPane.PLAIN_MESSAGE);
        System.exit(0);
      }
      ErrorManagement.handleException("Upgrading error", e);
    }

    if(!ebayLoaded) AuctionServerManager.getInstance().addServer(new ebayServer());

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
      imageURL = JConfig.getResource("/jbidwatch_apr1.jpg");
      JConfig.setConfiguration("sniperkitty", "true");
    } else {
      imageURL = JConfig.getResource(JConfig.queryConfiguration("splash", "jbidwatch.jpg"));
    }
    JSplashScreen Splash = new JSplashScreen(new ImageIcon(imageURL));

    try {
      JBidWatch program = new JBidWatch(Splash);
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
  }

  private MacFriendlyFrame buildFrame() {
    URL iconURL = JConfig.getResource(JConfig.queryConfiguration("icon", "jbidwatch64.jpg"));
    JMouseAdapter myFrameAdapter = new JBidFrameMouse();

    return new MacFriendlyFrame("JBidwatcher", myFrameAdapter, iconURL, jtmAuctions);
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
    DBManager.start();
    ActivityMonitor.start();
    UIBackbone backbone = new UIBackbone();
    MQFactory.getConcrete("login").registerListener(new MessageQueue.Listener() {
      public void messageAction(Object deQ) {
        MQFactory.getConcrete("Swing").enqueue("LOGINSTATUS " + deQ.toString());
      }
    });
    ThumbnailManager.start();
    FilterManager.getInstance().loadFilters();
    AuctionsManager.getInstance().loadAuctions();
    //  This needs to be after the auction manager, so that all the
    //  auction servers that are loaded by loading auctions will be
    //  available to add searches if they need to.
    SearchManager.getInstance().loadSearches();

    Scripting.initialize();
    AuctionServerManager.getInstance().getDefaultServerTime();

    JConfig.registerListener(this);

//    String defaultServer = AuctionServerManager.getInstance().getDefaultServer().getName();
//    MQFactory.getConcrete(defaultServer).enqueue(new AuctionQObject(AuctionQObject.MENU_CMD, AuctionServer.UPDATE_LOGIN_COOKIE, null)); //$NON-NLS-1$

    //  Register the handler for all 'drop' events.
    JBWDropHandler.start();
    Browser.start();

    jtmAuctions = AuctionsUIModel.getTabManager();
    JBidFrame.setDefaultMenuBar(JBidMenuBar.getInstance(jtmAuctions, "Search Editor"));

    mainFrame = buildFrame();
    mainFrame.setLocation(JConfig.screenx, JConfig.screeny);
    mainFrame.setSize(JConfig.width, JConfig.height);
    backbone.setMainFrame(mainFrame);

    inSplash.close();
    //noinspection UnusedAssignment
    inSplash = null;

    jtmAuctions.sortDefault();

    mainFrame.setVisible(true);

    //  Construct the tray object, so that we can interact with the system tray.
    if(Platform.isWindows()) {
      Tray.start();
      if(JConfig.queryConfiguration("windows.tray", "true").equals("true")) {
        MQFactory.getConcrete("tray").enqueue("TRAY on");
      }
    }

    //  Start any servers if necessary, and set the background colors,
    //  and anything else we need to load from the configuration file.
    updateConfiguration();

    SuperQueue sq = SuperQueue.getInstance();
    preQueueServices(sq);
    TimerHandler timeQueue = sq.start();

    //  Because the program is starting to get widely spread around,
    //  and I can't control the version numbers everywhere, this
    //  should monitor a certain location once a day and look for an
    //  update.  The user has the option to turn this off!
    TimerHandler updateTimer = new TimerHandler(UpdateManager.getInstance(), HOURS_IN_DAY * MINUTES_IN_HOUR * Constants.ONE_MINUTE);
    updateTimer.setName("VersionChecker");
    updateTimer.start();

    AudioPlayer.start();

    if(JConfig.queryConfiguration("debug.memory", "false").equals("true")) _rti = new RuntimeInfo();
    try {
      //  Don't leave this thread until the timeQueue has completed; i.e. the program is exiting.
      timeQueue.join();
    } catch (InterruptedException e) {
      ErrorManagement.handleException("timeQueue interrupted", e);
    }
  }

  private void preQueueServices(SuperQueue q) {
    long now = System.currentTimeMillis();

    if (JConfig.queryConfiguration("updates.enabled", "true").equals("true")) {
      q.preQueue(Boolean.FALSE, "update", now + (Constants.ONE_SECOND * 10));
    }
    //noinspection MultiplyOrDivideByPowerOfTwo
    if (JConfig.queryConfiguration("timesync.enabled", "true").equals("true")) {
      q.preQueue("TIMECHECK", "auction_manager", now + (Constants.ONE_SECOND * 2), Constants.THIRTY_MINUTES);
    }
    q.preQueue(new AuctionQObject(AuctionQObject.MENU_CMD, AuctionServer.UPDATE_LOGIN_COOKIE, null), "ebay", now + Constants.ONE_SECOND * 3, 240 * Constants.ONE_MINUTE);
    q.preQueue("ALLOW_UPDATES", "Swing", now + (Constants.ONE_SECOND * 2 * 10));
    q.preQueue("FLUSH", "dbflush", now + Constants.ONE_MINUTE, Constants.ONE_SECOND * 15);

    //  Other interesting examples...
    //q.preQueue("This is a message for the display!", "Swing", System.currentTimeMillis()+Constants.ONE_MINUTE);
    //q.preQueue(JBidMouse.ADD_AUCTION + "5582606163", "user", System.currentTimeMillis() + (Constants.ONE_MINUTE / 2));
    //q.preQueue("http://www.jbidwatcher.com", "browse", System.currentTimeMillis() + (Constants.ONE_MINUTE / 4));
    //q.preQueue(new AuctionQObject(AuctionQObject.BID, new AuctionBid("5582606251", Currency.getCurrency("2.99"), 1), "none"), "ebay", System.currentTimeMillis() + (Constants.ONE_MINUTE*2) );
  }
}
