package com.jbidwatcher.auction.server.ebay;

import com.jbidwatcher.util.StringTools;
import com.jbidwatcher.util.html.CleanupHandler;
import com.jbidwatcher.util.Externalized;

/**
 * Created by IntelliJ IDEA.
 * User: Morgan
 * Date: Feb 25, 2007
 * Time: 5:37:34 PM
 *
 * The core code to clean up eBay HTML pages before trying to parse them.
 */
public class ebayCleaner implements CleanupHandler
{
  /**
   * @param sb - The StringBuffer to eliminate script entries from.
   * @brief Remove all scripts (javascript or other) in the string
   * buffer passed in.
   */
  private void killScripts(StringBuffer sb) {
    StringTools.deleteRegexPairs(sb, Externalized.getString("ebayServer.stripScript"), Externalized.getString("ebayServer.stripScriptEnd"));
  }

  /**
   * @param sb - The StringBuffer to clean of scripts and comments.
   * @brief Delete all scripts, and comments on an HTML page.
   */
  public void cleanup(StringBuffer sb) {
    killScripts(sb);

    //  Eliminate all comment sections.
    StringTools.deleteRegexPairs(sb, Externalized.getString("ebayServer.stripComment"), Externalized.getString("ebayServer.stripCommentEnd"));
  }
}
