package com.jbidwatcher.auction;

/*
 * Copyright (c) 2000-2007, CyberFOX Software, Inc. All Rights Reserved.
 *
 * Developed by mrs (Morgan Schweers)
 */

import com.jbidwatcher.util.Currency;
import com.jbidwatcher.FilterManager;
import com.jbidwatcher.auction.server.AuctionServer;

/**
 * Created by IntelliJ IDEA.
 * User: Morgan Schweers
 * Date: Aug 18, 2005
 * Time: 12:26:21 AM
 * To change this template use File | Settings | File Templates.
 */
public abstract class AuctionActionImpl implements AuctionAction {
  protected String m_id;
  protected AuctionEntry m_auct;
  protected Currency m_amount;
  protected int m_quant;
  int m_result = -1;

  protected AuctionActionImpl(String id, Currency amount, int quantity) {
    m_id = id;
    m_auct = null;
    m_amount = amount;
    m_quant = quantity;
  }

  protected AuctionActionImpl(AuctionEntry ae, Currency amount, int quantity) {
    m_auct = ae;
    m_id = ae.getIdentifier();
    m_amount = amount;
    m_quant = quantity;
  }

  public String activate() {
    if(m_auct == null) {
      m_auct = FilterManager.getInstance().whereIsAuction(m_id).getEntry(m_id);
      if(m_auct == null) {
        m_result = AuctionServer.BID_ERROR_AUCTION_GONE;
        return getBidResult(m_amount, m_result);
      }
    }
    m_result = execute(m_auct, m_amount, m_quant);
    String bidResultString = getBidResult(m_amount, m_result);
    m_auct.setLastStatus(bidResultString);
    m_auct.update();
    return bidResultString;
  }

  protected abstract int execute(AuctionEntry ae, Currency curr, int quant);

  public int getResult() { return m_result; }
  public Currency getAmount() { return m_amount; }
  public int getQuantity() { return m_quant; }
  public boolean isSuccessful() {
    return (m_result == AuctionServer.BID_WINNING ||
            m_result == AuctionServer.BID_DUTCH_CONFIRMED ||
            m_result == AuctionServer.BID_SELFWIN);
  }

  public String getBidResult(Currency bidAmount, int bidResult) {
    String bidResultString;

    switch (bidResult) {
      case AuctionServer.BID_ERROR_UNKNOWN:
        bidResultString = "Bidding " + bidAmount + " apparently failed for an unknown reason.  Check the auction in the browser, to see if the bid went through anyway.";
        break;
      case AuctionServer.BID_ERROR_ENDED:
      case AuctionServer.BID_ERROR_CANNOT:
        bidResultString = "Bidding apparently failed, as the auction cannot be bid on anymore (probably ended)!";
        break;
      case AuctionServer.BID_DUTCH_CONFIRMED:
        bidResultString = "Your dutch bid was confirmed, and you are in the list of high bidders!";
        break;
      case AuctionServer.BID_ERROR_BANNED:
        bidResultString = "Your bid failed, as you are disallowed from bidding on this seller's items.";
        break;
      case AuctionServer.BID_ERROR_TOO_LOW:
        bidResultString = "Your bid was too low, and was not accepted.";
        break;
      case AuctionServer.BID_ERROR_TOO_LOW_SELF:
          bidResultString = "Your bid was below or equal to your previous high bid, and was not accepted.";
          break;
      case AuctionServer.BID_ERROR_RESERVE_NOT_MET:
        bidResultString = "Your bid was successful, but it did not meet the reserve price.";
        break;
      case AuctionServer.BID_ERROR_AMOUNT:
        bidResultString = "Bidding apparently failed, because of an an invalid amount (" + bidAmount + ").";
        break;
      case AuctionServer.BID_ERROR_OUTBID:
        bidResultString = "Your bid for " + bidAmount + " was submitted, but someone else's bid is still higher.";
        break;
      case AuctionServer.BID_ERROR_CONNECTION:
        bidResultString = "Bid failed due to connection problem.  Probably a timeout trying to reach eBay.";
        break;
      case AuctionServer.BID_ERROR_AUCTION_GONE:
        bidResultString = "Your bid failed because the item was removed from JBidwatcher before the bid executed.";
        break;
      case AuctionServer.BID_WINNING:
      case AuctionServer.BID_SELFWIN:
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
