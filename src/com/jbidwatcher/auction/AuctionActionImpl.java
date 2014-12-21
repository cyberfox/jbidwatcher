package com.jbidwatcher.auction;

/*
 * Copyright (c) 2000-2007, CyberFOX Software, Inc. All Rights Reserved.
 *
 * Developed by mrs (Morgan Schweers)
 */

import com.jbidwatcher.util.Currency;
import com.jbidwatcher.util.config.JConfig;
import com.jbidwatcher.auction.server.AuctionServer;

/**
 * Created by IntelliJ IDEA.
 * User: Morgan Schweers
 * Date: Aug 18, 2005
 * Time: 12:26:21 AM
 *
 * Provide an abstract framework for both bids and buying to use, mostly to
 * reduce duplication of the bid/buy result text.
 */
public abstract class AuctionActionImpl implements AuctionAction {
  protected String mIdentifier;
  protected String mAmount;
  protected int mQuantity;
  int mResult = -1;

  protected AuctionActionImpl(String id, Currency amount, int quantity) {
    this(id, amount.fullCurrency(), quantity);
  }

  protected AuctionActionImpl(String id, String amount, int quantity) {
    mIdentifier = id;
    mAmount = amount;
    mQuantity = quantity;
  }

  public AuctionActionImpl() { }

  public String activate(EntryCorral corral) {
    Currency amount = Currency.getCurrency(mAmount);
    AuctionEntry entry = (AuctionEntry) corral.takeForWrite(mIdentifier);
    try {
      if (entry == null) {
        mResult = AuctionServer.BID_ERROR_AUCTION_GONE;
        return getBidResult(amount, mResult);
      }
      JConfig.increment("stats.bid");
      mResult = execute(entry, amount, mQuantity);
      boolean success = (mResult == AuctionServer.BID_WINNING || mResult == AuctionServer.BID_BOUGHT_ITEM || mResult == AuctionServer.BID_SELFWIN);
      // Metrics
      if(!success) {
        JConfig.getMetrics().trackEventValue("bid", "fail", Integer.toString(mResult));
      } else {
        JConfig.getMetrics().trackEvent("bid", "success");
      }
      String bidResultString = getBidResult(amount, mResult);
      entry.setLastStatus(bidResultString);
      entry.update();
      return bidResultString;
    } finally {
      corral.release(mIdentifier);
    }
  }

  protected abstract int execute(AuctionEntry ae, Currency curr, int quant);

  public void setIdentifier(String identifier) { mIdentifier = identifier; }
  public void setAmount(String amount) { mAmount = amount; }
  public void setQuantity(int quantity) { mQuantity = quantity; }

  public String getIdentifier() { return mIdentifier; }
  public String getAmount() { return mAmount; }
  public int getQuantity() { return mQuantity; }

  public int getResult() { return mResult; }
  public boolean isSuccessful() {
    return (mResult == AuctionServerInterface.BID_WINNING ||
            mResult == AuctionServerInterface.BID_SELFWIN);
  }

  public String getBidResult(Currency bidAmount, int bidResult) {
    String bidResultString;

    switch (bidResult) {
      case AuctionServerInterface.BID_ERROR_UNKNOWN:
        bidResultString = "Bidding " + bidAmount + " apparently failed for an unknown reason.  Check the auction in the browser, to see if the bid went through anyway.";
        JConfig.increment("stats.bid.unknown_error");
        break;
      case com.jbidwatcher.auction.AuctionServerInterface.BID_ERROR_ENDED:
      case AuctionServerInterface.BID_ERROR_CANNOT:
        bidResultString = "Bidding apparently failed, as the auction cannot be bid on anymore (probably ended)!";
        JConfig.increment("stats.bid.too_late");
        break;
      case AuctionServerInterface.BID_ERROR_BANNED:
        bidResultString = "Your bid failed, as you are disallowed from bidding on this seller's items.";
        JConfig.increment("stats.bid.banned");
        break;
      case com.jbidwatcher.auction.AuctionServerInterface.BID_ERROR_TOO_LOW:
        bidResultString = "Your bid was too low, and was not accepted.";
        JConfig.increment("stats.bid.too_low");
        break;
      case com.jbidwatcher.auction.AuctionServerInterface.BID_ERROR_TOO_LOW_SELF:
        bidResultString = "Your bid was below or equal to your previous high bid, and was not accepted.";
        JConfig.increment("stats.bid.too_low");
        break;
      case AuctionServerInterface.BID_ERROR_RESERVE_NOT_MET:
        bidResultString = "Your bid was successful, but it did not meet the reserve price.";
        JConfig.increment("stats.bid.too_low");
        break;
      case AuctionServerInterface.BID_ERROR_AMOUNT:
        bidResultString = "Bidding apparently failed, because of an an invalid amount (" + bidAmount + ").";
        JConfig.increment("stats.bid.too_low");
        break;
      case com.jbidwatcher.auction.AuctionServerInterface.BID_ERROR_OUTBID:
        bidResultString = "Your bid for " + bidAmount + " was submitted, but someone else's bid is still higher.";
        JConfig.increment("stats.bid.outbid");
        break;
      case com.jbidwatcher.auction.AuctionServerInterface.BID_ERROR_CONNECTION:
        bidResultString = "Bid failed due to connection problem.  Probably a timeout trying to reach eBay.";
        JConfig.increment("stats.bid.connection_error");
        break;
      case AuctionServer.BID_ERROR_AUCTION_GONE:
        bidResultString = "Your bid failed because the item was removed from JBidwatcher before the bid executed.";
        JConfig.increment("stats.bid.removed");
        break;
      case AuctionServerInterface.BID_WINNING:
      case AuctionServerInterface.BID_SELFWIN:
        bidResultString = "Congratulations!  You have the high bid with " + bidAmount + '.';
        JConfig.increment("stats.bid.success");
        break;
      case AuctionServer.BID_ERROR_ACCOUNT_SUSPENDED:
        bidResultString = "You cannot interact with any auctions, your account has been suspended.";
        JConfig.increment("stats.bid.suspended");
        break;
      case AuctionServer.BID_ERROR_CANT_SIGN_IN:
        bidResultString = "Sign in failed repeatedly during bid.  Check your username and password information in the Configuration Manager.";
        JConfig.increment("stats.bid.sign_in");
        break;
      case AuctionServer.BID_ERROR_WONT_SHIP:
        bidResultString = "You are registered in a country to which the seller doesn't ship.";
        JConfig.increment("stats.bid.wont_ship");
        break;
      case AuctionServer.BID_ERROR_REQUIREMENTS_NOT_MET:
        bidResultString = "You don't meet some requirement the seller has set for the item.  Check the item details for more information.";
        JConfig.increment("stats.bid.requirement_not_met");
        break;
      case AuctionServer.BID_ERROR_SELLER_CANT_BID:
        bidResultString = "Sellers are not allowed to bid on their own items.";
        break;
      default:
        bidResultString = "Something VERY wrong has happened, and I don't know what it is.  Check the auction to see if your bid went through.";
        JConfig.increment("stats.bid.really_bad");
        break;
    }
    return (bidResultString);
  }
}
