package com.jbidwatcher.auction;

/*
 * Copyright (c) 2000-2007, CyberFOX Software, Inc. All Rights Reserved.
 *
 * Developed by mrs (Morgan Schweers)
 */

import com.jbidwatcher.util.html.*;
import com.jbidwatcher.auction.server.AuctionServer;

public abstract class SpecificAuction extends AuctionInfo implements CleanupHandler
{
  protected JHTML mDocument;

  public abstract AuctionServer.ParseErrors parseAuction(AuctionEntry ae);

  protected void finish() {
    mDocument = null;
  }

  public boolean preParseAuction() {
    StringBuffer sb = getContent();
    if(sb == null) return(false);

    cleanup(sb);

    mDocument = new JHTML(sb);

    return true;
  }

  protected boolean doesLabelExist(String label) {
    return (mDocument.lookup(label, false) != null);
  }

  protected boolean doesLabelPrefixExist(String label) {
    return (mDocument.find(label, false) != null);
  }
}
