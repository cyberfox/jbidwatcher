package com.jbidwatcher.auction;

/*
 * Copyright (c) 2000-2007, CyberFOX Software, Inc. All Rights Reserved.
 *
 * Developed by mrs (Morgan Schweers)
 */

import com.jbidwatcher.util.html.JHTML;
import com.jbidwatcher.auction.server.AuctionServer;

public abstract class SpecificAuction extends AuctionInfo implements CleanupHandler {
  protected JHTML _htmlDocument;

  public abstract AuctionServer.ParseErrors parseAuction(AuctionEntry ae);

  protected void finish() {
    _htmlDocument = null;
  }

  public boolean preParseAuction() {
    StringBuffer sb = getContent();
    if(sb == null) return(false);

    cleanup(sb);

    _htmlDocument = new JHTML(sb);

    return true;
  }

  protected boolean doesLabelExist(String label) {
    return (_htmlDocument.lookup(label, false) != null);
  }

  protected boolean doesLabelPrefixExist(String label) {
    return (_htmlDocument.find(label, false) != null);
  }
}
