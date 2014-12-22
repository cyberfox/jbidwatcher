package com.jbidwatcher.auction;

import com.jbidwatcher.util.Currency;

/**
* Created by mrs on 12/22/14.
*/
public class AuctionBid extends AuctionActionImpl {
  public AuctionBid() { }

  public AuctionBid(AuctionEntry ae, Currency bidAmount) {super(ae.getIdentifier(), bidAmount, 1);}

  @Override
  protected int execute(AuctionEntry ae, Currency curr, int quant) {
    return ae.bid(curr, quant);
  }
}
