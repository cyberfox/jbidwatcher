package com.jbidwatcher.ui;
/*
 * Copyright (c) 2000-2007, CyberFOX Software, Inc. All Rights Reserved.
 *
 * Developed by mrs (Morgan Schweers)
 */

import com.jbidwatcher.util.queue.DropQObject;
import com.jbidwatcher.util.queue.MQFactory;
import com.jbidwatcher.util.html.JHTML;
import com.jbidwatcher.util.config.ErrorManagement;
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

  private String stripNewlines(String instr) {
    int index;
    String outstr = instr;

    index = outstr.indexOf('\n');
    while(index != -1) {
      outstr = outstr.substring(0, index) + outstr.substring(index+1);
      index = outstr.indexOf('\n');
    }
    return outstr;
  }

  public void receiveDropString(StringBuffer dropped) {
    if(dropped == null) {
      ErrorManagement.logDebug("Dropped is (null)");
      return;
    }

    dropped = new StringBuffer(stripNewlines(dropped.toString()));
    if(sUberDebug) ErrorManagement.logDebug("Dropping :" + dropped + ":");

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
          ErrorManagement.logDebug("Adding: " + auctionURL.trim());
          MQFactory.getConcrete("drop").enqueue(new DropQObject(auctionURL.trim(), mTargetName, true));
        }
      }
    } else {
      String newEntry = dropped.toString();

      MQFactory.getConcrete("drop").enqueue(new DropQObject(newEntry.trim(), mTargetName, true));
    }
  }
}
