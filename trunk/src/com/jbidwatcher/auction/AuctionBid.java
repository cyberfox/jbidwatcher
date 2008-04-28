package com.jbidwatcher.auction;

/*
 * Copyright (c) 2000-2007, CyberFOX Software, Inc. All Rights Reserved.
 *
 * Developed by mrs (Morgan Schweers)
 */

import com.jbidwatcher.util.Currency;

public class AuctionBid extends AuctionActionImpl {
  public AuctionBid(String id, Currency amount, int quantity) {
    super(id, amount, quantity);
  }

  public AuctionBid(AuctionEntry ae, Currency amount, int quantity) {
    super(ae, amount, quantity);
  }

  protected int execute(AuctionEntry ae, Currency curr, int quant) {
    return ae.bid(curr, quant);
  }
}
