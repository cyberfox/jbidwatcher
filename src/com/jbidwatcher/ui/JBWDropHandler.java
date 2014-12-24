package com.jbidwatcher.ui;

/*
 * Copyright (c) 2000-2007, CyberFOX Software, Inc. All Rights Reserved.
 *
 * Developed by mrs (Morgan Schweers)
 */

import com.google.inject.Inject;
import com.jbidwatcher.auction.EntryFactory;
import com.jbidwatcher.util.queue.DropQObject;
import com.jbidwatcher.util.queue.MessageQueue;
import com.jbidwatcher.util.queue.MQFactory;
import com.jbidwatcher.util.config.JConfig;
import com.jbidwatcher.util.StringTools;
import com.jbidwatcher.auction.server.AuctionServerManager;
import com.jbidwatcher.auction.AuctionEntry;
import com.jbidwatcher.auction.EntryCorral;

public class JBWDropHandler implements MessageQueue.Listener {
  private static boolean do_uber_debug = false;
  private static String lastSeen = null;

  private EntryFactory entryFactory;
  private AuctionServerManager auctionServerManager;
  private EntryCorral entryCorral;
  private AuctionsManager auctionsManager;

  @Inject
  public JBWDropHandler(EntryFactory entryFactory, AuctionServerManager auctionServerManager, EntryCorral corral, AuctionsManager auctionsManager) {
    this.entryFactory = entryFactory;
    this.auctionServerManager = auctionServerManager;
    this.entryCorral = corral;
    this.auctionsManager = auctionsManager;

    MQFactory.getConcrete("drop").registerListener(this);
  }

  public void messageAction(Object deQ) {
    if (deQ instanceof String && StringTools.isNumberOnly((String)deQ)) {
      AuctionEntry ae = entryCorral.takeForRead((String) deQ);
      if (ae != null) {
        boolean lostAuction = !ae.hasAuction();
        ae.update();
        if (lostAuction) auctionsManager.addEntry(ae);
        return;
      }
    }

    String auctionURL;
    String label;
    boolean interactive;

    if(deQ instanceof String) {
      auctionURL = deQ.toString();
      label = null;
      interactive = true;
    } else {
      DropQObject dObj = (DropQObject) deQ;
      auctionURL = (String) dObj.getData();
      label = dObj.getLabel();
      interactive = dObj.isInteractive();
    }

    loadDroppedEntry(auctionURL, label, interactive);
  }

  private void loadDroppedEntry(String auctionURL, String label, boolean interactive) {
    if (do_uber_debug) JConfig.log().logDebug("Dropping (action): " + auctionURL);

    //  Check to see if it's got a protocol ({protocol}:{path})
    //  If not, treat it as an item number alone, in the space of the default auction server.
    //  If so, we get the identifier from the URL (which is multi-country),
    String aucId = auctionServerManager.getServer().stripId(auctionURL);

    if(EntryFactory.isInvalid(interactive, aucId)) return;

    //  Create an auction entry from the id.
    AuctionEntry aeNew = entryFactory.conditionallyAddEntry(interactive, aucId, label);
    if(aeNew == null) {
      if (lastSeen == null || !aucId.equals(lastSeen)) {
        JConfig.log().logDebug("Not loaded (" + aucId + ").");
        lastSeen = aucId;
      }
    } else {
      lastSeen = aeNew.getIdentifier();
    }
  }
}
