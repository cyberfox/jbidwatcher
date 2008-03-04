package com.jbidwatcher.ui;

/*
 * Copyright (c) 2000-2007, CyberFOX Software, Inc. All Rights Reserved.
 *
 * Developed by mrs (Morgan Schweers)
 */

import com.jbidwatcher.util.queue.DropQObject;
import com.jbidwatcher.util.queue.MessageQueue;
import com.jbidwatcher.util.queue.MQFactory;
import com.jbidwatcher.util.ErrorManagement;
import com.jbidwatcher.auction.server.AuctionServerManager;
import com.jbidwatcher.auction.AuctionEntry;
import com.jbidwatcher.auction.AuctionsManager;
import com.jbidwatcher.auction.server.AuctionServer;

public class JBWDropHandler implements MessageQueue.Listener {
  private static boolean do_uber_debug = false;

  public JBWDropHandler() {
    MQFactory.getConcrete("drop").registerListener(this);
  }

  static String lastSeen = null;

  public void messageAction(Object deQ) {
    DropQObject dObj;
    if (deQ instanceof String) {
      dObj = new DropQObject((String)deQ, null, false);
    } else {
      dObj = (DropQObject) deQ;
    }
    String auctionURL = (String)dObj.getData();
    String label = dObj.getLabel();

    if(do_uber_debug) {
      ErrorManagement.logDebug("Dropping (action): " + auctionURL);
    }

    String aucId;
    AuctionServer aucServ;

    //  Check to see if it's got a protocol ({protocol}:{path})
    //  If not, treat it as an item number alone, in the space of the default auction server.
    if(auctionURL.indexOf(":") != -1) {
      aucServ = AuctionServerManager.getInstance().getServerForUrlString(auctionURL);
      aucId = aucServ.extractIdentifierFromURLString(auctionURL);
    } else {
      aucServ = AuctionServerManager.getInstance().getDefaultServer();
      aucId = auctionURL;
    }
    //  TODO -- WTF?  Why do we get the URL from the Id, then create an
    //  TODO -- auction entry from the URL instead of just creating it from the Id?
    String cvtURL = aucServ.getStringURLFromItem(aucId);

    if(dObj.isInteractive()) {
      AuctionsManager.getInstance().undelete(aucId);
    }
    AuctionEntry aeNew = AuctionsManager.getInstance().newAuctionEntry(cvtURL);
    if(aeNew != null && aeNew.isLoaded()) {
      if(label != null) {
        aeNew.setCategory(label);
      }
      aeNew.clearNeedsUpdate();
      ErrorManagement.logDebug("Loaded " + aeNew.getIdentifier() + '.');
      lastSeen = aeNew.getIdentifier();
      AuctionsManager.getInstance().addEntry(aeNew);
    } else {
      if(lastSeen == null || !aucId.equals(lastSeen)) {
        ErrorManagement.logDebug("Not loaded (url " + cvtURL + ").");
        lastSeen = aucId;
      }
      if(aeNew != null) {
        AuctionServerManager.getInstance().deleteEntry(aeNew);
      }
    }
  }
}
