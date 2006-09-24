package com.jbidwatcher.config;
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

import com.jbidwatcher.platform.WindowsBrowserLauncher;
import com.jbidwatcher.config.JConfig;
import com.jbidwatcher.queue.MessageQueue;
import com.jbidwatcher.queue.MQFactory;
import com.jbidwatcher.Constants;
import com.jbidwatcher.util.BrowserLauncher;
import com.jbidwatcher.util.ErrorManagement;

import java.io.*;

public class JBConfig extends JConfig implements MessageQueue.Listener {
  public JBConfig() {
    MQFactory.getConcrete("browse").registerListener(this);
  }

  public void messageAction(Object deQ) {
    String msg = (String)deQ;

    launchBrowser(msg);
  }

  public static boolean doAffiliate(long end_time) {
    //  An end time of '0' should just return whether the affiliate program is enabled in the broader sense.
    if(end_time != 0) {
      //  If they have selected the affiliate program, or have not
      // made a choice, AND the current time is earlier than 5 minutes
      // from the end time, then default to going through the affiliate
      // program.
      return JConfig.queryConfiguration("ebay.affiliate", "true").equals("true") && ((end_time - Constants.ONE_MINUTE * 5) > System.currentTimeMillis());
    }
    return JConfig.queryConfiguration("ebay.affiliate", "false").equals("true") && JConfig.queryConfiguration("ebay.affiliate.override", "false").equals("true");
  }

  public static String getBrowserCommand() {
    String osName = getOS();

    if(osName.equalsIgnoreCase("windows")) {
      return WindowsBrowserLauncher.getBrowser("http");
    } else {
      return "netscape";
    }
  }

  public static boolean launchBrowser(String url) {
    String rawOSName = System.getProperty("os.name");
    int spaceIndex = rawOSName.indexOf(' ');
    String osName;

    if (spaceIndex == -1) {
      osName = rawOSName;
    } else {
      osName = rawOSName.substring(0, spaceIndex);
    }

    String launchCommand = JConfig.queryConfiguration("browser.launch." + osName);

    if(launchCommand == null) {
      launchCommand = JConfig.queryConfiguration("browser.launch");
      if(launchCommand == null) {
        launchCommand = "netscape";
      }
    }

    try {
      BrowserLauncher.openURL(url, launchCommand, JConfig.queryConfiguration("browser.override","false").equals("true"));
    } catch(IOException e) {
      ErrorManagement.handleException("Launching browser", e);
      return false;
    }
    return true;
  }
}
