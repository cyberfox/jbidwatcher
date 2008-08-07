package com.jbidwatcher.auction;

/*
 * Copyright (c) 2000-2007, CyberFOX Software, Inc. All Rights Reserved.
 *
 * Developed by mrs (Morgan Schweers)
 */

import com.jbidwatcher.util.Currency;
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
  protected AuctionEntry mEntry;
  protected Currency mAmount;
  protected int mQuantity;
  int mResult = -1;

  protected AuctionActionImpl(String id, Currency amount, int quantity) {
    mIdentifier = id;
    mEntry = null;
    mAmount = amount;
    mQuantity = quantity;
  }

  protected AuctionActionImpl(AuctionEntry ae, Currency amount, int quantity) {
    mEntry = ae;
    mIdentifier = ae.getIdentifier();
    mAmount = amount;
    mQuantity = quantity;
  }

  public String activate() {
    if(mEntry == null) {
      mEntry = AuctionEntry.findByIdentifier(mIdentifier);
      if(mEntry == null) {
        mResult = AuctionServer.BID_ERROR_AUCTION_GONE;
        return getBidResult(mAmount, mResult);
      }
    }
    mResult = execute(mEntry, mAmount, mQuantity);
    String bidResultString = getBidResult(mAmount, mResult);
    mEntry.setLastStatus(bidResultString);
    mEntry.update();
    return bidResultString;
  }

  protected abstract int execute(AuctionEntry ae, Currency curr, int quant);

  public int getResult() { return mResult; }
  public Currency getAmount() { return mAmount; }
  public int getQuantity() { return mQuantity; }
  public boolean isSuccessful() {
    return (mResult == AuctionServerInterface.BID_WINNING ||
            mResult == AuctionServerInterface.BID_DUTCH_CONFIRMED ||
            mResult == AuctionServerInterface.BID_SELFWIN);
  }

  public String getBidResult(Currency bidAmount, int bidResult) {
    String bidResultString;

    switch (bidResult) {
      case AuctionServerInterface.BID_ERROR_UNKNOWN:
        bidResultString = "Bidding " + bidAmount + " apparently failed for an unknown reason.  Check the auction in the browser, to see if the bid went through anyway.";
        break;
      case com.jbidwatcher.auction.AuctionServerInterface.BID_ERROR_ENDED:
      case AuctionServerInterface.BID_ERROR_CANNOT:
        bidResultString = "Bidding apparently failed, as the auction cannot be bid on anymore (probably ended)!";
        break;
      case com.jbidwatcher.auction.AuctionServerInterface.BID_DUTCH_CONFIRMED:
        bidResultString = "Your dutch bid was confirmed, and you are in the list of high bidders!";
        break;
      case AuctionServerInterface.BID_ERROR_BANNED:
        bidResultString = "Your bid failed, as you are disallowed from bidding on this seller's items.";
        break;
      case com.jbidwatcher.auction.AuctionServerInterface.BID_ERROR_TOO_LOW:
        bidResultString = "Your bid was too low, and was not accepted.";
        break;
      case com.jbidwatcher.auction.AuctionServerInterface.BID_ERROR_TOO_LOW_SELF:
          bidResultString = "Your bid was below or equal to your previous high bid, and was not accepted.";
          break;
      case AuctionServerInterface.BID_ERROR_RESERVE_NOT_MET:
        bidResultString = "Your bid was successful, but it did not meet the reserve price.";
        break;
      case AuctionServerInterface.BID_ERROR_AMOUNT:
        bidResultString = "Bidding apparently failed, because of an an invalid amount (" + bidAmount + ").";
        break;
      case com.jbidwatcher.auction.AuctionServerInterface.BID_ERROR_OUTBID:
        bidResultString = "Your bid for " + bidAmount + " was submitted, but someone else's bid is still higher.";
        break;
      case com.jbidwatcher.auction.AuctionServerInterface.BID_ERROR_CONNECTION:
        bidResultString = "Bid failed due to connection problem.  Probably a timeout trying to reach eBay.";
        break;
      case AuctionServer.BID_ERROR_AUCTION_GONE:
        bidResultString = "Your bid failed because the item was removed from JBidwatcher before the bid executed.";
        break;
      case AuctionServerInterface.BID_WINNING:
      case AuctionServerInterface.BID_SELFWIN:
        bidResultString = "Congratulations!  You have the high bid with " + bidAmount + '.';
        break;
      case AuctionServer.BID_ERROR_ACCOUNT_SUSPENDED:
        bidResultString = "You cannot interact with any auctions, your account has been suspended.";
        break;
      case AuctionServer.BID_ERROR_CANT_SIGN_IN:
        bidResultString = "Sign in failed repeatedly during bid.  Check your username and password information in the Configuration Manager.";
        break;
      case AuctionServer.BID_ERROR_WONT_SHIP:
        bidResultString = "You are registered in a country to which the seller doesn't ship.";
        break;
      case AuctionServer.BID_ERROR_REQUIREMENTS_NOT_MET:
        bidResultString = "You don't meet some requirement the seller has set for the item.  Check the item details for more information.";
        break;
      default:
        bidResultString = "Something VERY wrong has happened, and I don't know what it is.  Check the auction to see if your bid went through.";
        break;
    }
    return (bidResultString);
  }
}
