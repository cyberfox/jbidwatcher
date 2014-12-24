package com.jbidwatcher.auction;

/*
 * Copyright (c) 2000-2007, CyberFOX Software, Inc. All Rights Reserved.
 *
 * Developed by mrs (Morgan Schweers)
 */

import com.jbidwatcher.util.Record;

public abstract class SpecificAuction extends AuctionInfo {
  public abstract ItemParser.ParseErrors setFields(Record parse, String seller);
}
