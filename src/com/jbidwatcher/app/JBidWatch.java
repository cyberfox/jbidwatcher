package com.jbidwatcher.app;
/*
 * Copyright (c) 2000-2007, CyberFOX Software, Inc. All Rights Reserved.
 *
 * Developed by mrs (Morgan Schweers)
 */

import com.cyberfox.util.config.ErrorManagement;

import com.cyberfox.util.platform.Path;
import com.cyberfox.util.platform.Platform;
import com.cyberfox.util.platform.osx.NoNap;
import com.google.inject.*;
import com.jbidwatcher.auction.*;
import com.jbidwatcher.auction.server.AuctionServerFactory;
import com.jbidwatcher.auction.server.AuctionStats;
import com.jbidwatcher.platform.*;
import com.jbidwatcher.auction.server.AuctionServer;
import com.jbidwatcher.auction.server.AuctionServerManager;
import com.jbidwatcher.ui.commands.UserActions;
import com.jbidwatcher.ui.config.JConfigFrame;
import com.jbidwatcher.search.SearchManager;
import com.jbidwatcher.ui.*;
import com.jbidwatcher.ui.util.JBidFrame;
import com.jbidwatcher.ui.util.JMouseAdapter;
import com.jbidwatcher.ui.util.RuntimeInfo;
import com.jbidwatcher.util.Constants;
import com.jbidwatcher.scripting.JRubyPreloader;
import com.jbidwatcher.util.config.JConfig;
import com.jbidwatcher.util.db.ActiveRecord;
import com.jbidwatcher.util.services.ActivityMonitor;
import com.jbidwatcher.util.ErrorMonitor;
import com.jbidwatcher.scripting.Scripting;
import com.jbidwatcher.util.queue.*;
import com.jbidwatcher.util.services.AudioPlayer;
import com.jbidwatcher.util.services.SyncService;
import com.jbidwatcher.util.xml.XMLElement;
import com.jbidwatcher.*;
import com.jbidwatcher.my.MyJBidwatcher;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.*;
import java.util.*;

/**
 * @file   JBidWatch.java
 * @author Morgan Schweers <cyberfox@jbidwatcher.com>
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
  private final MyJBidwatcher myJBidwatcher;
  private final JBWDropHandler dropHandler;
  private final Provider<UIBackbone> backboneProvider;
  private final Provider<JConfigFrame> configFrameProvider;
  @Inject
  private AuctionServerFactory serverFactory;
  @Inject
  private PopupMenuFactory menuFactory;

  private final FilterManager filters;
  private final ListManager listManager;
  private final AuctionsManager auctionsManager;
  private SearchManager searchManager;
  private EntryFactory entryFactory;
  private EntryCorral corral;
  private AuctionServerManager serverManager;
  private ErrorMonitor errorMonitor;
  private Injector injector;

  private final Object memInfoSynch = new Object();
  private MacFriendlyFrame mainFrame;
  private JTabManager jtmAuctions;
  private static Sparkle mSparkle = null;
  private SyncService mServiceAdvertiser;

  private RuntimeInfo _rti = null;
  private static final int HOURS_IN_DAY = 24;
  private static final int MINUTES_IN_HOUR = 60;
  private static boolean sUSB = false;
  private boolean ebayLoaded = false;

  private final Object mScriptCompletion = new Object();
  private UserActions userActions;

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
    return Path.makeStandardDirectory(inPath, "auctionsave", "jbidwatcher");
  }

  private static String makePlatformDirectory(String inPath) {
    return Path.makeStandardDirectory(inPath, "platform", "jbidwatcher");
  }

  private void getUserSetup() {
    JConfig.setConfiguration("config.firstrun", "true");
    configFrameProvider.get().spinWait();
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
    if(Platform.isMac()) {
      NoNap.dontNapMeBro();
    }

    Dimension screensize = Toolkit.getDefaultToolkit().getScreenSize();

    String dispFile = Path.getCanonicalFile("display.cfg", "jbidwatcher", true);
    JConfig.loadDisplayConfig(dispFile, urlCL, screensize.width, screensize.height);
    if(sUSB) JConfig.fixupPaths(JConfig.getHomeDirectory());

    String aucSave = makeSaveDirectory(JConfig.queryConfiguration("auctions.savepath"));
    if(aucSave != null) {
      JConfig.setConfiguration("auctions.savepath", aucSave);
    }

    String platform = makePlatformDirectory(JConfig.queryConfiguration("platform.path"));
    if(platform != null) {
      JConfig.setConfiguration("platform.path", platform);
      if((Platform.isWindows() && Platform.extractAndLoadLibrary()) || Platform.supportsTray()) {
        Platform.setTrayEnabled(true);
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
          Path.getHome() + "</body></html>", "Help display", JOptionPane.PLAIN_MESSAGE);
      return true;
    } else if (arg.startsWith("--usb")) {
      Path.setHome(System.getProperty("user.dir"));
      sUSB = true;
    } else if (arg.startsWith("--testImpl")) {
      System.out.println("Impl Version: " + Constants.REVISION());
      System.exit(1);
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
        setProxyAuthenticator();
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
        setProxyAuthenticator();
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
        setProxyAuthenticator();
        return true;
      }
    }
    return false;
  }

  private static void setProxyAuthenticator() {
    if (!sProxyAuthenticatorAlreadySet) {
      final String user = JConfig.queryConfiguration("proxy.user", null);
      final String pass = JConfig.queryConfiguration("proxy.pass", null);
      if (user != null && pass != null) {
        Authenticator.setDefault(new Authenticator() {
          @SuppressWarnings({"RefusedBequest"})
          protected PasswordAuthentication getPasswordAuthentication() {
            String host = getRequestingHost();

            //  If talking to my.jbidwatcher.com, JBidwatcher handles authentication itself.
            if(host == null || !host.contains("jbidwatcher")) {
              return (new PasswordAuthentication(user, pass.toCharArray()));
            }
            return null;
          }
        });
        sProxyAuthenticatorAlreadySet = true;
      }
    }

  }

  private static boolean sProxyAuthenticatorAlreadySet = false;

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
        JConfig.log().handleException("Exception in setUI, failure to set " + whatLaF + ": " + exMe, exMe);
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
    JConfig.setLogger(new ErrorManagement());
    if (checkArguments(args)) {
      System.exit(0);
    }

    Path.setHomeDirectory("jbidwatcher");

    JConfig.setVersion(Constants.PROGRAM_VERS);

    System.setProperty("sun.net.client.defaultConnectTimeout", "5000");
    System.setProperty("sun.net.client.defaultReadTimeout", "15000");

    JBidWatch program = getApplication();

    program.configure(args);
    program.startDatabase();
    program.setupSearches();
    program.loadProxySettings();

    JSplashScreen splashScreen = prepSplashScreen();

    try {
      program.run(splashScreen);
      Thread.currentThread().join();

      program.repaint();
    } catch (Exception e) {
      JConfig.log().handleException("JBidwatcher: " + e, e);
    }
  }

  private static JBidWatch getApplication() {
    AbstractModule guiceModule = new JBidwatcherModule();

    Injector inject = Guice.createInjector(guiceModule);
    return inject.getInstance(JBidWatch.class);
  }

  private void configure(String[] args) {
    //  Pass a parameter (other than --help or -h) to launch that as a
    //  configuration file.
    String cfgLoad = "JBidWatch.cfg";
    if (args.length != 0) {
      if (args[0].charAt(0) != '-') {
        cfgLoad = args[0];
      }
    }

    cfgLoad = Path.getCanonicalFile(cfgLoad, "jbidwatcher", false);
    cfgLoad = lookForNewerMacConfig(cfgLoad);
    InputStream configStream = checkConfig(cfgLoad);

    boolean firstRun;
    boolean needUserSetup = (configStream == null);
    if (needUserSetup) {
      setUI(null, null, UIManager.getInstalledLookAndFeels());
      //  Preload the eBay server, must be done before Configuration setup
      //  could happen, to get the configuration tab for eBay.
      eBayServerSetup();
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
    JConfig.setConfiguration("first.run", firstRun ? "true" : "false");
    setUI(null, null, UIManager.getInstalledLookAndFeels());

    JConfig.log().logMessage(Constants.PROGRAM_NAME + " " + Constants.PROGRAM_VERS + "-" + Constants.REVISION());
    JConfig.log().logMessage(System.getProperty("java.vendor") + " Java, version " + System.getProperty("java.version") + " on " + System.getProperty("os.name"));
    if (JConfig.queryConfiguration("mac", "false").equals("true")) {
      JConfig.setConfiguration("temp.cfg.load", Path.getCanonicalFile("JBidWatch.cfg", "jbidwatcher", false));
    }
    String logFileName = JConfig.log().getLog();
    if (logFileName != null) JConfig.log().logMessage("Logging to " + logFileName);

    if (JConfig.queryConfiguration("show.badhtml", "false").equals("true")) {
      XMLElement.rejectBadHTML(true);
    }
  }

  private void startDatabase() {
    try {
//      boolean creatingDB = JConfig.queryConfiguration("jbidwatcher.created_db", "false").equals("false");
      Upgrader.upgrade();
//      if (creatingDB && JConfig.queryConfiguration("jbidwatcher.created_db", "false").equals("true")) {
        //  Ignored - We just created the database.
//      }
    } catch (Exception e) {
      if (e.getMessage().matches("^Failed to start database.*")) {
        JConfig.log().handleException("JBidwatcher can't access it's database.", e);
        JOptionPane.showMessageDialog(null, "JBidwatcher can't access its database.\nPlease check to see if you are running another instance.", "Can't access auction database", JOptionPane.PLAIN_MESSAGE);
        JConfig.stopMetrics(Constants.PROGRAM_VERS);
        System.exit(0);
      }
      JConfig.log().handleException("Upgrading error", e);
    }
  }

  @Inject
  public JBidWatch(ErrorMonitor monitor, SearchManager searcher, EntryFactory entryMaker, EntryCorral holdingCell,
                   AuctionServerManager serverManager, MyJBidwatcher myInstance, JBWDropHandler dropHandler,
                   FilterManager filterManager, JTabManager tabManager, ListManager listManager, AuctionsManager auctionsManager,
                   UserActions userActions, Injector inject,
                   Provider<UIBackbone> uiBackboneProvider, Provider<JConfigFrame> configFrameProvider) {
    this.searchManager = searcher;
    this.corral = holdingCell;
    this.entryFactory = entryMaker;
    this.serverManager = serverManager;
    this.errorMonitor = monitor;
    this.myJBidwatcher = myInstance;
    this.dropHandler = dropHandler;
    this.filters = filterManager;
    this.jtmAuctions = tabManager;
    this.listManager = listManager;
    this.auctionsManager = auctionsManager;
    this.backboneProvider = uiBackboneProvider;
    this.userActions = userActions;
    this.injector = inject;
    this.configFrameProvider = configFrameProvider;

    AuctionEntry.addObserver(entryFactory);
    MultiSnipe.setCorral(corral);
  }

  private void setupSearches() {//  We need to load searches before adding the eBay server, so
    //  that it knows that a My eBay search already exists and doesn't
    //  try to recreate it.
    searchManager.loadSearches();

    if(!ebayLoaded) {
      eBayServerSetup();
    }
    searchManager.setDestinationQueue(this.serverManager.getServer().getFriendlyName());
  }

  private static JSplashScreen prepSplashScreen() {//  Show splash screen and progress bar.
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
    return new JSplashScreen(new ImageIcon(imageURL));
  }

  private static String lookForNewerMacConfig(String cfgLoad) {
    if (System.getProperty("mrj.version") != null) {
      String sep = System.getProperty("file.separator");
      String macHome = Path.getMacHomeDirectory("jbidwatcher");
      String macCfg = macHome + sep + "JBidWatch.cfg";
      File mac = new File(macCfg);
      File cfg = new File(cfgLoad);
      if(mac.lastModified() > cfg.lastModified()) cfgLoad = macCfg;
    }
    return cfgLoad;
  }

  private void eBayServerSetup() {
    boolean nonUS = JConfig.queryConfiguration("ebay.non_us", Boolean.toString(!Platform.isUSBased())).equals("true");
    String homeSite = nonUS ? JConfig.queryConfiguration("ebay.alternate", "ebay.co.uk") : "ebay.com";
    AuctionServer ebay = serverFactory.create(homeSite, null, null);
    serverManager.setServer(ebay);
  }

  private void repaint() {
    mainFrame.repaint();
  }

  private void loadProxySettings() {
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
   * Also, we check the background color, and set it appropriately.
   */
  public final void updateConfiguration() {
    String savedBGColor = JConfig.queryConfiguration("background", "false");
    if(!savedBGColor.equals("false")) {
      listManager.setBackground(Color.decode('#' + savedBGColor));
    }

    //  Enable the internal server, if it's set.
    if(JConfig.queryConfiguration("server.enabled", "false").equals("true")) {
      mServiceAdvertiser = new SyncService(9099);
      mServiceAdvertiser.advertise();
    } else {
      if(mServiceAdvertiser != null) {
        mServiceAdvertiser.stopAdvertising();
      }
    }
    loadProxySettings();

    synchronized (memInfoSynch) {
      if (JConfig.queryConfiguration("debug.memory", "false").equals("true")) {
        if (_rti == null) {
          _rti = new RuntimeInfo();
        } else {
          _rti.setVisible(true);
        }
      } else {
        if (_rti != null) _rti.setVisible(false);
      }
    }
  }

  private MacFriendlyFrame buildFrame() {
    URL iconURL = JConfig.getResource(JConfig.queryConfiguration("icon", "jbidwatch64.jpg"));
    JMouseAdapter myFrameAdapter = injector.getInstance(JBidFrameMouse.class);
    return new MacFriendlyFrame(injector.getInstance(JBidToolBar.class), "JBidwatcher", myFrameAdapter, iconURL, jtmAuctions);
  }

  /**
   * @brief Load the saved auctions, build the UI frame, close down
   * the splash screen, and start the monitor and update threads.
   *
   * @param inSplash Splash screen with a status bar, to be updated during startup.
   *
   * @noinspection CallToThreadStartDuringObjectConstruction
   */
  private void run(JSplashScreen inSplash) {
    inSplash.message("Initializing Monitors");
    ActivityMonitor.start();
    MQFactory.getConcrete("login").registerListener(new MessageQueue.Listener() {
      public void messageAction(Object deQ) {
        MQFactory.getConcrete("Swing").enqueue("LOGINSTATUS " + deQ.toString());
      }
    });
    ThumbnailLoader.start();

    inSplash.message("Initializing Scripting");
    JRubyPreloader preloader = new JRubyPreloader(mScriptCompletion);
    Thread scriptLoading = new Thread(preloader);
    scriptLoading.start();

    inSplash.message("Initializing Database");
    Initializer.setup(jtmAuctions, listManager, menuFactory);
    filters.loadFilters();
    inSplash.message("Loading Auctions");
    auctionsManager.loadAuctionsFromDatabase();

    serverManager.getDefaultServerTime();

    JConfig.registerListener(this);

    //  Register the handler for all 'drop' events.
    Browser.start();
    MQFactory.getConcrete("user").registerListener(userActions);
    //    class.getClass().getClassLoader().find('com.jbidwatcher.ui.commands.*').loadAll();

    inSplash.message("Building Interface");
    JBidFrame.setDefaultMenuBar(JBidMenuBar.getInstance(menuFactory, jtmAuctions.getTabs(), jtmAuctions, "Search Editor"));

    mainFrame = buildFrame();
    mainFrame.setLocation(JConfig.screenx, JConfig.screeny);
    mainFrame.setSize(JConfig.width, JConfig.height);
    backboneProvider.get().setMainFrame(mainFrame);

    synchronized (mScriptCompletion) {
      if(JConfig.scriptingEnabled()) {
        inSplash.message("Starting scripts");

        Scripting.setGlobalVariable("$auction_server_manager", serverManager);
        Scripting.setGlobalVariable("$auctions_manager", auctionsManager);
        Scripting.setGlobalVariable("$filter_manager", filters);

        Scripting.require("utilities");
        Scripting.ruby("JBidwatcher.after_startup");
      } else {
	JOptionPane.showMessageDialog(null, "<html><body>JBidwatcher is unable to load its scripting layer;<br>" +
				      "as of 3.0 and later, scripting is a core part of JBidwatcher<br>" +
				      "and it will not run without it.</body</html>", "Scripting Error", JOptionPane.ERROR_MESSAGE);

      }
    }
    inSplash.close();
    //noinspection UnusedAssignment
    inSplash = null;

    jtmAuctions.sortDefault();

    mainFrame.setVisible(true);

    //  Construct the tray object, so that we can interact with the system tray.
    if(Platform.supportsTray()) {
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
    final TimerHandler timeQueue = sq.start();

    //  This is how we shut down cleanly.
    MQFactory.getConcrete("jbidwatcher").registerListener(new MessageQueue.Listener() {
      public void messageAction(Object deQ) {
        timeQueue.interrupt();
      }
    });

    boolean updaterStarted = false;
    if(Platform.isMac()) {
      try {
        mSparkle = new Sparkle();
        mSparkle.start();
        updaterStarted = true;
        JConfig.setConfiguration("temp.sparkle", "true");
      } catch(Throwable e) {
        JConfig.log().handleDebugException("Couldn't start Sparkle - This message is normal under OS X 10.4", e);
        updaterStarted = false;
        JConfig.setConfiguration("temp.sparkle", "false");
      }
    }

    if(!updaterStarted) {
      TimerHandler updateTimer = new TimerHandler(UpdateManager.getInstance(), HOURS_IN_DAY * MINUTES_IN_HOUR * Constants.ONE_MINUTE);
      updateTimer.setName("VersionChecker");
      updateTimer.start();
    }

    AudioPlayer.start();

    synchronized(memInfoSynch) { if(_rti == null && JConfig.queryConfiguration("debug.memory", "false").equals("true")) _rti = new RuntimeInfo(); }
    try {
      //  Don't leave this thread until the timeQueue has completed; i.e. the program is exiting.
      timeQueue.join();
    } catch (InterruptedException e) {
      JConfig.log().handleException("timeQueue interrupted", e);
    }
    internal_shutdown();
    JConfig.stopMetrics(Constants.PROGRAM_VERS);
    System.exit(0);
  }

  private void preQueueServices(SuperQueue q) {
    long now = System.currentTimeMillis();

    if (JConfig.queryConfiguration("updates.enabled", "true").equals("true")) {
      q.preQueue("AUTOMATIC", "update", now + (Constants.ONE_SECOND * 10));
    }
    //noinspection MultiplyOrDivideByPowerOfTwo
    if (JConfig.queryConfiguration("timesync.enabled", "true").equals("true")) {
      q.preQueue("TIMECHECK", "auction_manager", now + (Constants.ONE_SECOND * 2), Constants.THIRTY_MINUTES);
    }

    //  TODO mrs - This is where things start to suck. Can this become a single VERB+NOUN operation?
    q.preQueue(new AuctionQObject(AuctionQObject.MENU_CMD, AuctionServer.UPDATE_LOGIN_COOKIE, null),
               serverManager.getServer().getFriendlyName(),
               now + Constants.ONE_SECOND * 3,
               481 * Constants.ONE_MINUTE + Constants.ONE_SECOND * 17);

    q.preQueue("ALLOW_UPDATES", "Swing", now + (Constants.ONE_SECOND * 20));
    establishMetrics(q, now);

    //  Disable this when I am once more gainfully employed.
//    if(JConfig.queryConfiguration("seen.need_help2") == null) {
//      if(JConfig.queryConfiguration("first_run", "false").equals("false")) {
//        q.preQueue("Need Help", "user", now + (Constants.ONE_SECOND * 15));
//        JConfig.setConfiguration("seen.need_help2", "true");
//      }
//    }

    //  Other interesting examples...
    //q.preQueue("This is a message for the display!", "Swing", System.currentTimeMillis()+Constants.ONE_MINUTE);
    //q.preQueue(UserActions.ADD_AUCTION + "5582606163", "user", System.currentTimeMillis() + (Constants.ONE_MINUTE / 2));
    //q.preQueue("http://www.jbidwatcher.com", "browse", System.currentTimeMillis() + (Constants.ONE_MINUTE / 4));
    //q.preQueue(new AuctionQObject(AuctionQObject.BID, new AuctionBid("5582606251", Currency.getCurrency("2.99"), 1), "none"), AuctionServerManager.getInstance().getServer(), System.currentTimeMillis() + (Constants.ONE_MINUTE*2) );
  }

  /**
   * Prompt the user to allow metrics, if they haven't been asked before.  Set up a daily flush, which only happens if the user
   * opts in.
   *
   * @param q - The super-queue to put the one-time and recurring events on.
   * @param now - What time is it now, so everything starts with the same time-base.
   */
  private void establishMetrics(SuperQueue q, long now) {
  //  Ask the user to allow anonymized statistics gathering.
    if(JConfig.queryConfiguration("metrics.optin", "ask").equals("ask")) {
      q.preQueue("Metrics", "user", now + (Constants.ONE_SECOND * 5));
    }

    MQFactory.getConcrete("metrics").registerListener(new MessageQueue.Listener() {
      public void messageAction(Object deQ) {
        if(JConfig.sendMetricsAllowed(Constants.PROGRAM_VERS)) {
          try {
            JConfig.getMetrics().flush();
          } catch (IOException e) {
            JConfig.log().handleDebugException("Couldn't flush analytics to the server.", e);
          }
        }
      }
    });

    q.preQueue("Flush Metrics", "metrics", now + Constants.ONE_DAY, Constants.ONE_DAY);
  }

  /**
   * @return A property table of all the table column header information, suitable for saving.
   * @brief Obtains a 'property list' of all the column widths, names,
   * etc., in order to save them off so the UI can remain
   * approximately the same between executions.
   */
  public Properties getColumnProperties() {
    Properties colProps = new Properties();

    colProps = listManager.extractProperties(colProps);

    return (colProps);
  }

  public void internal_shutdown() {
    //  Shut down internal timers
    try {
      if(mServiceAdvertiser != null) mServiceAdvertiser.stop();
      for (Object o : JConfig.getTimers()) {
        ((TimerHandler) o).interrupt();
        try { ((TimerHandler) o).join(); } catch (InterruptedException ignored) {}
      }

      Properties colProps = getColumnProperties();
      searchManager.saveSearchDisplay();
      Properties displayProps = UISnapshot.snapshotLocation(mainFrame);
      String dispFile = Path.getCanonicalFile("display.cfg", "jbidwatcher", false);
      JConfig.saveDisplayConfig(dispFile, displayProps, colProps);

      //  Save it to the original file, if it was provided at runtime,
      //  otherwise to the appropriate default.
      String cfgLoad = JConfig.queryConfiguration("temp.cfg.load", "JBidWatch.cfg");
      String cfgFilename = cfgLoad.equals("JBidWatch.cfg") ? Path.getCanonicalFile(cfgLoad, "jbidwatcher", false) : cfgLoad;

      //  TODO -- Need to save searches in the database too...  Right now they're still hanging around in XML form.
      searchManager.saveSearches();
      AuctionStats as = serverManager.getStats();
      JConfig.setConfiguration("last.auctioncount", Integer.toString(as.getCount()));
      JConfig.saveConfiguration(cfgFilename);
      ActiveRecord.shutdown();
    } catch(Exception e) {
      JConfig.log().handleException("Threw an error during shutdown!  Shutting down anyway!", e);
    } finally {
      JConfig.log().logMessage("Shutting down JBidwatcher.");
      JConfig.log().closeLog();
    }
  }
}
