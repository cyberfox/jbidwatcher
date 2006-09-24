package com.jbidwatcher.auction;

import com.jbidwatcher.util.html.JHTML;
import com.jbidwatcher.auction.CleanupHandler;
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

public abstract class SpecificAuction extends AuctionInfo implements CleanupHandler {
  protected JHTML _htmlDocument;

  public abstract boolean parseAuction(AuctionEntry ae);

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
