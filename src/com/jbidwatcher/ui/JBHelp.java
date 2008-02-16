package com.jbidwatcher.ui;
/*
 * Copyright (c) 2000-2007, CyberFOX Software, Inc. All Rights Reserved.
 *
 * Developed by mrs (Morgan Schweers)
 */

import java.io.*;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.net.URL;

import com.jbidwatcher.config.JConfig;
import com.jbidwatcher.util.http.Http;
import com.jbidwatcher.Constants;

public class JBHelp {
  private static final String paypalDonate =
    "https://www.paypal.com/xclick/business=cyberfox%40users.sourceforge.net&item_name=JBidWatcher+Software&no_shipping=1&return=http%3A//jbidwatcher.sourceforge.net/donation_thanks.html";
  private static final String c_gr = "<font color=\"green\">";
  private static final String c_bl = "<font color=\"blue\">";
  private static final String c_rd = "<font color=\"red\">";
  private static final String c_end= "</font>";
  private static final String HEAD_LOC = "/help/MyHead.jpg";

  private JBHelp() {
  }

  public static StringBuffer loadHelp(String helpPath, String title) {
    StringBuffer outSB;

    try {
      URL resource = JConfig.getResource(helpPath);
      outSB = new StringBuffer(preprocess(Http.receivePage(resource.openConnection()), title));
    } catch(IOException ignored) {
      outSB = null;
    }
    return outSB;
  }

  private static String genTOC(StringBuffer data) {
    int moveOn = 0;
    StringBuffer toc = new StringBuffer();
    Pattern quesPat = Pattern.compile("<%que (\\w+)%>(.*)");
    Matcher qMatch = quesPat.matcher(data);

    while(qMatch.find(moveOn)) {
      if(moveOn == 0) toc.append("<ol>");
      moveOn = qMatch.start()+1;
      toc.append("<li><a href=\"#Q").append(qMatch.group(1)).append("\">").append(qMatch.group(2)).append("</a></li>\n");
    }
    //  Special case no que's found.
    if(moveOn == 0) return "";

    toc.append("</ol>");

    return toc.toString();
  }

  public static String preprocess(StringBuffer helpBuf, String title) {
    String munge = helpBuf.toString();

    String toc = genTOC(helpBuf);
    String headImage = JConfig.getResource(HEAD_LOC).toString();
    String basicLocation = headImage.substring(0, headImage.length() - HEAD_LOC.length());

    munge = munge.replaceAll("<%que ([0-9]+)%>", "<%que%><a name=\"Q$1\">").
        replaceAll("<%toc%>", Matcher.quoteReplacement(toc)).
        replaceAll("<%pname%>", Matcher.quoteReplacement(Constants.PROGRAM_NAME)).
        replaceAll("<%ver%>", Matcher.quoteReplacement(Constants.PROGRAM_VERS)).
        replaceAll("<%title%>", Matcher.quoteReplacement(title)).
        replaceAll("<%donate%>", Matcher.quoteReplacement(paypalDonate)).
        replaceAll("<%head%>", Matcher.quoteReplacement(headImage)).
        replaceAll("<%jar%>", Matcher.quoteReplacement(basicLocation)).
        replaceAll("<%c_gr%>", Matcher.quoteReplacement(c_gr)).
        replaceAll("<%c_bl%>", Matcher.quoteReplacement(c_bl)).
        replaceAll("<%c_rd%>", Matcher.quoteReplacement(c_rd)).
        replaceAll("<%cend%>", Matcher.quoteReplacement(c_end)).
        replaceAll("<%que%>", "<li><B><u>Q.</u> ").
        replaceAll("<%ans%>", "<br><u>A.</u></B> ").
        replaceAll("<%end%>", "<br><a href=\"#top\">Top</a></li>").
        replaceAll("<%jay%>", Matcher.quoteReplacement(JConfig.getResource("/jbidwatch64.jpg").toString())).
        replaceAll("<%auctions_save%>", Matcher.quoteReplacement(JConfig.queryConfiguration("savefile")));

    return munge;
  }
}
