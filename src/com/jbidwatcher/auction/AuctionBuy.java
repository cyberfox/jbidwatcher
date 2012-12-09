package com.jbidwatcher.auction;

/*
 * Copyright (c) 2000-2007, CyberFOX Software, Inc. All Rights Reserved.
 *
 * Developed by mrs (Morgan Schweers)
 */

import com.jbidwatcher.util.Currency;
import com.jbidwatcher.auction.server.AuctionServer;

public class AuctionBuy extends AuctionActionImpl {
  public AuctionBuy(AuctionEntry ae, Currency amount, int quantity) {
    super(ae.getIdentifier(), amount.fullCurrency(), quantity);
  }

  protected int execute(AuctionEntry ae, Currency curr, int quant) {
    return ae.buy(quant);
  }

  public String getBidResult(Currency bidAmount, int bidResult) {
    String bidResultString;

    switch (bidResult) {
      case AuctionServerInterface.BID_ERROR_UNKNOWN:
        bidResultString = "Purchasing apparently failed for an unknown reason.  Check the auction in the browser, to see if it went through anyway.";
        break;
      case com.jbidwatcher.auction.AuctionServerInterface.BID_ERROR_ENDED:
      case AuctionServerInterface.BID_ERROR_CANNOT:
        bidResultString = "Purchasing apparently failed, as the auction cannot be bought from anymore (probably ended)!";
        break;
      case AuctionServerInterface.BID_ERROR_BANNED:
        bidResultString = "Your purchase failed, as you are disallowed from buying this seller's items.";
        break;
      case com.jbidwatcher.auction.AuctionServerInterface.BID_ERROR_CONNECTION:
        bidResultString = "Purchase failed due to connection problem.  Probably a timeout trying to reach eBay.";
        break;
      case AuctionServer.BID_ERROR_AUCTION_GONE:
        bidResultString = "Your purchase failed because the item was removed from JBidwatcher before it executed.";
        break;
      case AuctionServer.BID_ERROR_NOT_BIN:
        bidResultString = "You cannot purchase this item, it is not a Buy It Now item.";
        break;
      case AuctionServer.BID_BOUGHT_ITEM:
        bidResultString = "Congratulations!  You successfully bought it!";
        break;
      default:
        return super.getBidResult(bidAmount, bidResult);
    }
    return bidResultString;
  }
}
