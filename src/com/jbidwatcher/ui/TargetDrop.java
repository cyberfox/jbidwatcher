package com.jbidwatcher.ui;
/*
 * Copyright (c) 2000-2007, CyberFOX Software, Inc. All Rights Reserved.
 *
 * Developed by mrs (Morgan Schweers)
 */

import com.jbidwatcher.util.queue.DropQObject;
import com.jbidwatcher.util.queue.MQFactory;
import com.jbidwatcher.util.html.JHTML;
import com.jbidwatcher.util.config.JConfig;
import com.jbidwatcher.ui.util.JDropHandler;

import java.util.List;

public class TargetDrop implements JDropHandler
{
  private static boolean sUberDebug = false;
  private String mTargetName;

  public TargetDrop(String tableName) {
    super();
    mTargetName = tableName;
  }

  /**
   * @brief This allows creation of a 'retarget to current' drop handler.
   */
  public TargetDrop() {
    super();
    mTargetName = null;
  }

  private String cleanText(String instr) {
    String outstr = instr;

    int index = Math.min(outstr.indexOf('\n'), outstr.indexOf('\0'));
    while(index != -1) {
      outstr = outstr.substring(0, index) + outstr.substring(index+1);
      index = Math.min(outstr.indexOf('\n'), outstr.indexOf('\0'));
    }
    return outstr;
  }

  public void receiveDropString(StringBuffer dropped) {
    if(dropped == null) {
      JConfig.log().logDebug("Dropped is (null)");
      return;
    }

    dropped = new StringBuffer(cleanText(dropped.toString()));
    if(sUberDebug) JConfig.log().logDebug("Dropping :" + dropped + ":");

    //  Is it an 'HTML Fragment' as produced by Mozilla, NS6, and IE5+?
    //  BOY it's a small bit to test against, but Mozilla starts with <HTML>,
    //  and IE5 starts with <!DOCTYPE...  The only commonality I can trust is
    //  that they'll start with a tag, not content.  I could look for <HTML>
    //  someplace in the document...  --  mrs: 28-September-2001 03:53
    if(dropped.charAt(0) == '<') {
      JHTML tinyDocument = new JHTML(dropped);
      List<String> allItemsOnPage = tinyDocument.getAllURLsOnPage(true);
      String auctionURL;

      if(allItemsOnPage == null) return;

      for (String anAllItemsOnPage : allItemsOnPage) {
        auctionURL = anAllItemsOnPage;
        if (auctionURL != null) {
          JConfig.log().logDebug("Adding: " + auctionURL.trim());
          MQFactory.getConcrete("drop").enqueueBean(new DropQObject(auctionURL.trim(), mTargetName, true));
        }
      }
    } else {
      String newEntry = dropped.toString();

      MQFactory.getConcrete("drop").enqueueBean(new DropQObject(newEntry.trim(), mTargetName, true));
    }
  }
}
