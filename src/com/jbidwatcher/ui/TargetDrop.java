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

import java.awt.Point;
import java.util.List;

public class TargetDrop implements JDropHandler
{
  private static boolean sUberDebug = false;
  private String mTargetName;
  private ImageDropResolver imageResolver;

  public TargetDrop(String tableName, ImageDropResolver imageResolver) {
    super();
    mTargetName = tableName;
    this.imageResolver = imageResolver;
  }

  /**
   * @brief This allows creation of a 'retarget to current' drop handler.
   */
  public TargetDrop() {
    super();
    mTargetName = null;
  }

  private StringBuffer cleanString(StringBuffer instr) {
    int i;
    int len = instr.length();
    StringBuffer s = new StringBuffer(len);
    for (i = 0; i < len; i++) {
      char c = instr.charAt(i);
      if (c != '\u0000' && c != '\n') s.append(c);
    }
    return s;
  }

  public void receiveDropString(StringBuffer dropped, Point location) {
    if(dropped == null) {
      JConfig.log().logDebug("Dropped is (null)");
      return;
    }

    dropped = new StringBuffer(cleanString(dropped));
    if(sUberDebug) JConfig.log().logDebug("Dropping :" + dropped + ":");

    //  Is it an 'HTML Fragment' as produced by Mozilla, NS6, and IE5+?
    //  BOY it's a small bit to test against, but Mozilla starts with <HTML>,
    //  and IE5 starts with <!DOCTYPE...  The only commonality I can trust is
    //  that they'll start with a tag, not content.  I could look for <HTML>
    //  someplace in the document...  --  mrs: 28-September-2001 03:53
    if(dropped.charAt(0) == '<') {
      JHTML tinyDocument = new JHTML(dropped);
      List<String> allItemsOnPage = tinyDocument.getAllURLsOnPage(true);

      if(allItemsOnPage == null) {
        //  This could be an image drop.
        tinyDocument.reset();
        List<String> allImagesOnPage = tinyDocument.getAllImages();
        if(allImagesOnPage.isEmpty()) {
          return;
        }

        //  Get the first image
        String img = allImagesOnPage.get(0);

        imageResolver.handle(img, location);
        return;
      }

      for (String auctionURL : allItemsOnPage) {
        if (auctionURL != null) {
          JConfig.log().logDebug("Adding: " + auctionURL.trim());
          MQFactory.getConcrete("drop").enqueueBean(new DropQObject(auctionURL.trim(), mTargetName, true));
        }
      }
    } else {
      String newEntry = dropped.toString();
      if(newEntry.matches("^.*(\\.(jpg|png|jpeg|gif|bmp))$")) {
        imageResolver.handle(newEntry, location);
      } else {
        MQFactory.getConcrete("drop").enqueueBean(new DropQObject(newEntry.trim(), mTargetName, true));
      }
    }
  }
}
