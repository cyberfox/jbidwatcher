package com.jbidwatcher.auction;

/*
 * Copyright (c) 2000-2007, CyberFOX Software, Inc. All Rights Reserved.
 *
 * Developed by mrs (Morgan Schweers)
 */

import com.jbidwatcher.util.Currency;

public class AuctionBid extends AuctionActionImpl {
  public AuctionBid() { }
  public AuctionBid(AuctionEntry ae, Currency amount, int quantity) {
    super(ae.getIdentifier(), amount.fullCurrency(), quantity);
    EntryCorral.getInstance().put(ae);
  }

  protected int execute(AuctionEntry ae, Currency curr, int quant) {
    return ae.bid(curr, quant);
  }
}
