package com.jbidwatcher.auction;

import com.jbidwatcher.util.Currency;
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

public class AuctionBuy extends AuctionActionImpl {
  public AuctionBuy(String id, Currency amount, int quantity) {
    super(id, amount, quantity);
  }

  public AuctionBuy(AuctionEntry ae, Currency amount, int quantity) {
    super(ae, amount, quantity);
  }

  protected int execute(AuctionEntry ae, Currency curr, int quant) {
    return ae.buy(quant);
  }

  public String getBidResult(Currency bidAmount, int bidResult) {
    String bidResultString;

    switch (bidResult) {
      case AuctionServer.BID_ERROR_UNKNOWN:
        bidResultString = "Purchasing " + bidAmount + " apparently failed for an unknown reason.  Check the auction in the browser, to see if it went through anyway.";
        break;
      case AuctionServer.BID_ERROR_ENDED:
      case AuctionServer.BID_ERROR_CANNOT:
        bidResultString = "Purchasing apparently failed, as the auction cannot be bought from anymore (probably ended)!";
        break;
      case AuctionServer.BID_ERROR_BANNED:
        bidResultString = "Your purchase failed, as you are disallowed from buying this seller's items.";
        break;
      case AuctionServer.BID_ERROR_CONNECTION:
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
