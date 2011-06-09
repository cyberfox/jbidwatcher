package com.jbidwatcher.util.config;

import com.jbidwatcher.util.Constants;

/**
 * User: mrs
 * Date: 6/9/11
 * Time: 3:48 PM
 *
 * JBidwatcher-specific configuration tools, with all the power of the general-purpose config class behind it.
 */
public class JConfig extends com.cyberfox.util.config.JConfig {
  static {
    setBaseName("JBidWatch.cfg");
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
