package com.jbidwatcher.ui;

import com.jbidwatcher.queue.DropQObject;
import com.jbidwatcher.queue.MessageQueue;
import com.jbidwatcher.queue.MQFactory;
import com.jbidwatcher.util.ErrorManagement;
import com.jbidwatcher.auction.server.AuctionServerManager;
import com.jbidwatcher.auction.AuctionEntry;
import com.jbidwatcher.auction.AuctionsManager;
import com.jbidwatcher.auction.server.AuctionServer;
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

public class JBWDropHandler implements MessageQueue.Listener {
  private static boolean do_uber_debug = false;

  public JBWDropHandler() {
    MQFactory.getConcrete("drop").registerListener(this);
  }

  static String lastSeen = null;

  public void messageAction(Object deQ) {
    DropQObject dObj = (DropQObject) deQ;
    String auctionURL = (String)dObj.getData();
    String label = dObj.getLabel();

    if(do_uber_debug) {
      ErrorManagement.logDebug("Dropping (action): " + auctionURL);
    }

    AuctionServer aucServ = AuctionServerManager.getInstance().getServerForUrlString(auctionURL);
    String aucId = aucServ.extractIdentifierFromURLString(auctionURL);
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
        AuctionServerManager.getInstance().delete_entry(aeNew);
      }
    }
  }
}
