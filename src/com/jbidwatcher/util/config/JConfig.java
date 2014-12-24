package com.jbidwatcher.util.config;

import com.DeskMetrics.DeskMetrics;

import java.io.File;

/**
 * User: mrs
 * Date: 6/9/11
 * Time: 3:48 PM
 *
 * JBidwatcher-specific configuration tools, with all the power of the general-purpose config class behind it.
 */
public class JConfig extends com.cyberfox.util.config.JConfig {
  private static DeskMetrics metrics;

  static {
    setBaseName("JBidWatch.cfg");
    metrics = DeskMetrics.getInstance();
    metrics.setEndpoint("https://my.jbidwatcher.com/report/usage");

    String version = null;
    Package pack = JConfig.class.getPackage();
    if (pack != null) version = pack.getImplementationVersion();
    if (version == null) {
      version = "debug";
    }

    //  Metrics are kept always, but only shared on shutdown if the user
    //  has opted in to sending them; this allows us to also send them
    //  (if they allow it) on bug-reporting.
    metrics.start("4f4a195ca14ad72a1d000000", version);
  }

  public static boolean sendMetricsAllowed(String version) {
    return queryConfiguration("metrics.optin", "false").equals("true") ||
           (queryConfiguration("metrics.optin", "false").equals("pre") && isPrerelease(version));
  }

  public static boolean isPrerelease(String version) {return version.matches(".*(pre|alpha|beta).*");}

  public static void stopMetrics(String version) {
    try {
      if(metrics != null) {
        //  With the exception of certain metrics operations which upload
        //  immediately (and that I don't use), this will prevent the metrics
        //  code from uploading anything until the end of the session.
        if(sendMetricsAllowed(version)) {
          metrics.stop();
        }
      }
    } catch (Exception e) {
      //  Let's stop all exceptions, so they don't propagate up.
      JConfig.log().handleDebugException("Failed to send metrics to the server", e);
    }
  }

  public static DeskMetrics getMetrics() {
    return metrics;
  }

  public static void fixupPaths(String homeDirectory) {
    String[][] s = {{"auctions.savepath", "auctionsave"},
        {"platform.path", "platform"},
        {"savefile", "auctions.xml"},
        {"search.savefile", "searches.xml"}};
    String sep = System.getProperty("file.separator");
    for (String[] pair : s) {
      setConfiguration(pair[0], homeDirectory + sep + pair[1]);
    }
  }

  public static File getContentFile(String identifier) {
    File fp = null;
    String outPath = queryConfiguration("auctions.savepath");
    if(outPath != null && outPath.length() != 0) {
      String filePath = outPath + System.getProperty("file.separator") + identifier + ".html.gz";
      fp = new File(filePath);
    }
    return fp;
  }
}
