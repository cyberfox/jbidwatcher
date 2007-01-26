package com.jbidwatcher.ui;
/*
 * Copyright (c) 2000-2007, CyberFOX Software, Inc. All Rights Reserved.
 *
 * Developed by mrs (Morgan Schweers)
 */

import com.jbidwatcher.queue.DropQObject;
import com.jbidwatcher.queue.MQFactory;
import com.jbidwatcher.util.html.JHTML;
import com.jbidwatcher.util.ErrorManagement;

import java.util.Iterator;
import java.util.List;

public class JTableDrop implements JDropHandler {
  private static boolean do_uber_debug = false;
  private String _name;

  public JTableDrop(String tableName) {
    super();
    _name = tableName;
  }

  /**
   * @brief This allows creation of a 'retarget to current' drop handler.
   */
  public JTableDrop() {
    super();
    _name = null;
  }

  private String newline_strip(String instr) {
    int newline_index;
    String outstr = instr;

    newline_index = outstr.indexOf('\n');
    while(newline_index != -1) {
      outstr = outstr.substring(0, newline_index) + outstr.substring(newline_index+1);
      newline_index = outstr.indexOf('\n');
    }
    return outstr;
  }

  public void receiveDropString(StringBuffer dropped) {
    if(dropped == null) {
      ErrorManagement.logDebug("Dropped is (null)");
      return;
    }

    dropped = new StringBuffer(newline_strip(dropped.toString()));
    if(do_uber_debug) ErrorManagement.logDebug("Dropping :" + dropped + ":");

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
          MQFactory.getConcrete("drop").enqueue(new DropQObject(auctionURL.trim(), _name, true));
        }
      }
    } else {
      String newEntry = dropped.toString();

      MQFactory.getConcrete("drop").enqueue(new DropQObject(newEntry.trim(), _name, true));
    }
  }
}
