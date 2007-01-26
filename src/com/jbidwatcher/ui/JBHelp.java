package com.jbidwatcher.ui;
/*
 * Copyright (c) 2000-2007, CyberFOX Software, Inc. All Rights Reserved.
 *
 * Developed by mrs (Morgan Schweers)
 */

import java.io.*;
import com.stevesoft.pat.*;
import com.jbidwatcher.config.JConfig;
import com.jbidwatcher.util.http.Http;
import com.jbidwatcher.Constants;

import java.util.HashMap;

public class JBHelp {
  private static final String paypalDonate =
    "https://www.paypal.com/xclick/business=cyberfox%40users.sourceforge.net&item_name=JBidWatcher+Software&no_shipping=1&return=http%3A//jbidwatcher.sourceforge.net/donation_thanks.html";
  private static final String c_gr = "<font color=\"green\">";
  private static final String c_bl = "<font color=\"blue\">";
  private static final String c_rd = "<font color=\"red\">";
  private static final String c_end= "</font>";
  private static final int MAX_VARIABLES = 40;
  private static final String HEAD_LOC = "/help/MyHead.jpg";

  private JBHelp() {
  }

  public static StringBuffer loadHelp(String helpPath, String title) {
    StringBuffer outSB;

    try {
      outSB = new StringBuffer(preprocess(Http.receivePage(JBidMouse.class.getResource(helpPath).openConnection()), title));
    } catch(IOException ignored) {
      outSB = null;
    }
    return outSB;
  }

  private static String genTOC(String data) {
    int moveOn = 0;
    StringBuffer toc = null;
    Regex qMatch = new Regex("<%que (\\w+)%>(.*)");

    while(qMatch.searchFrom(data, moveOn)) {
      if(toc == null) //noinspection ObjectAllocationInLoop
        toc = new StringBuffer("<ol>");
      moveOn = qMatch.matchedFrom()+1;
      toc.append("<li><a href=\"#Q").append(qMatch.stringMatched(1)).append("\">").append(qMatch.stringMatched(2)).append("</a></li>\n");
    }
    //  Special case no que's found.
    if(moveOn == 0) return "";

    toc.append("</ol>");

    return toc.toString();
  }

  public static String preprocess(StringBuffer helpBuf, String title) {
    String munge = helpBuf.toString();

    String toc = genTOC(munge);

    Regex r = Regex.perlCode("s/<%que (\\w+)%>/<%que%><a name=\"Q\\1\">/");
    munge = r.replaceAll(munge);

    HashMap<String, String> vars = new HashMap<String, String>(MAX_VARIABLES);
    vars.put("<%toc%>", toc);
    vars.put("<%pname%>", Constants.PROGRAM_NAME);
    vars.put("<%ver%>", Constants.PROGRAM_VERS);
    vars.put("<%title%>", title);
    vars.put("<%donate%>", paypalDonate);
    String headImage = JBHelp.class.getResource(HEAD_LOC).toString();
    vars.put("<%head%>", headImage);
    String basicLocation = headImage.substring(0, headImage.length() - HEAD_LOC.length());
    vars.put("<%jar%>", basicLocation);
    vars.put("<%c_gr%>", c_gr);
    vars.put("<%c_bl%>", c_bl);
    vars.put("<%c_rd%>", c_rd);
    vars.put("<%cend%>", c_end);
    vars.put("<%que%>", "<li><B><u>Q.</u> ");
    vars.put("<%ans%>", "<br><u>A.</u></B> ");
    vars.put("<%end%>", "<br><a href=\"#top\">Top</a></li>");
    vars.put("<%jay%>", JBHelp.class.getResource("/jbidwatch64.jpg").toString());
    vars.put("<%auctions_save%>", JConfig.queryConfiguration("savefile"));

    Variables theReplacements = new Variables(vars);
    ReplaceRule.define("var_rule", theReplacements);

    r = Regex.perlCode("s/<%\\w+%>/${var_rule}/");

    String out = r.replaceAll(munge);
    //  Run it a second time, to catch TOC entries.
    out = r.replaceAll(out);

    return out;
  }
}
