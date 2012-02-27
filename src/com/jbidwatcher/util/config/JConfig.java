package com.jbidwatcher.util.config;

import com.DeskMetrics.DeskMetrics;
import com.jbidwatcher.util.Constants;

import java.io.IOException;

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
    try {
      String version = Constants.class.getPackage().getImplementationVersion();
      if(version == null) {
        version = "debug";
      }
      //  Metrics are kept always, but only shared on shutdown if the user
      //  has opted in to sending them; this allows us to also send them
      //  (if they allow it) on bug-reporting.
      metrics.start("4f4a195ca14ad72a1d000000", version);
    } catch (IOException e) {
      metrics = null;
    }
  }

  public static void stopMetrics() {
    try {
      if(metrics != null) {
        if(queryConfiguration("metrics.optin", "false").equals("true")) {
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

  public static String getVersion() {
    return Constants.PROGRAM_VERS;
  }
}
