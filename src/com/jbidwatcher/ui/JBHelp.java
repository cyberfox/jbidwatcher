package com.jbidwatcher.ui;
/*
 * Copyright (c) 2000-2007, CyberFOX Software, Inc. All Rights Reserved.
 *
 * Developed by mrs (Morgan Schweers)
 */

import java.io.*;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
//import com.stevesoft.pat.*;
import com.jbidwatcher.config.JConfig;
import com.jbidwatcher.util.http.Http;
import com.jbidwatcher.Constants;

//import java.util.HashMap;

public class JBHelp {
  private static final String paypalDonate =
    "https://www.paypal.com/xclick/business=cyberfox%40users.sourceforge.net&item_name=JBidWatcher+Software&no_shipping=1&return=http%3A//jbidwatcher.sourceforge.net/donation_thanks.html";
  private static final String c_gr = "<font color=\"green\">";
  private static final String c_bl = "<font color=\"blue\">";
  private static final String c_rd = "<font color=\"red\">";
  private static final String c_end= "</font>";
//  private static final int MAX_VARIABLES = 40;
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
    String headImage = JBHelp.class.getResource(HEAD_LOC).toString();
    String basicLocation = headImage.substring(0, headImage.length() - HEAD_LOC.length());

    return munge.replaceAll("<%que (\\w+)%>", "<%que%><a name=\"Q\\1\">").
        replaceAll("<%toc%>", toc).
        replaceAll("<%pname%>", Constants.PROGRAM_NAME).
        replaceAll("<%ver%>", Constants.PROGRAM_VERS).
        replaceAll("<%title%>", title).
        replaceAll("<%donate%>", paypalDonate).
        replaceAll("<%head%>", headImage).
        replaceAll("<%jar%>", basicLocation).
        replaceAll("<%c_gr%>", c_gr).
        replaceAll("<%c_bl%>", c_bl).
        replaceAll("<%c_rd%>", c_rd).
        replaceAll("<%cend%>", c_end).
        replaceAll("<%que%>", "<li><B><u>Q.</u> ").
        replaceAll("<%ans%>", "<br><u>A.</u></B> ").
        replaceAll("<%end%>", "<br><a href=\"#top\">Top</a></li>").
        replaceAll("<%jay%>", JBHelp.class.getResource("/jbidwatch64.jpg").toString()).
        replaceAll("<%auctions_save%>", JConfig.queryConfiguration("savefile"));
  }

//  public static String preprocess2(StringBuffer helpBuf, String title) {
//    String munge = helpBuf.toString();
//
//    String toc = genTOC(munge);
//
//    Regex r = Regex.perlCode("s/<%que (\\w+)%>/<%que%><a name=\"Q\\1\">/");
//    munge = r.replaceAll(munge);
//
//    HashMap<String, String> vars = new HashMap<String, String>(MAX_VARIABLES);
//    vars.put("<%toc%>", toc);
//    vars.put("<%pname%>", Constants.PROGRAM_NAME);
//    vars.put("<%ver%>", Constants.PROGRAM_VERS);
//    vars.put("<%title%>", title);
//    vars.put("<%donate%>", paypalDonate);
//    String headImage = JBHelp.class.getResource(HEAD_LOC).toString();
//    vars.put("<%head%>", headImage);
//    String basicLocation = headImage.substring(0, headImage.length() - HEAD_LOC.length());
//    vars.put("<%jar%>", basicLocation);
//    vars.put("<%c_gr%>", c_gr);
//    vars.put("<%c_bl%>", c_bl);
//    vars.put("<%c_rd%>", c_rd);
//    vars.put("<%cend%>", c_end);
//    vars.put("<%que%>", "<li><B><u>Q.</u> ");
//    vars.put("<%ans%>", "<br><u>A.</u></B> ");
//    vars.put("<%end%>", "<br><a href=\"#top\">Top</a></li>");
//    vars.put("<%jay%>", JBHelp.class.getResource("/jbidwatch64.jpg").toString());
//    vars.put("<%auctions_save%>", JConfig.queryConfiguration("savefile"));
//
//    Variables theReplacements = new Variables(vars);
//    ReplaceRule.define("var_rule", theReplacements);
//
//    r = Regex.perlCode("s/<%\\w+%>/${var_rule}/");
//
//    String out = r.replaceAll(munge);
//    //  Run it a second time, to catch TOC entries.
//    out = r.replaceAll(out);
//
//    return out;
//  }
}
