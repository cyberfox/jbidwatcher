package com.jbidwatcher.auction;

/*
 * Copyright (c) 2000-2007, CyberFOX Software, Inc. All Rights Reserved.
 *
 * Developed by mrs (Morgan Schweers)
 */

import com.jbidwatcher.queue.MQFactory;
import com.jbidwatcher.util.html.JHTML;
import com.jbidwatcher.util.http.CookieJar;
import com.jbidwatcher.util.ErrorManagement;
import com.jbidwatcher.auction.server.AuctionServer;
import com.jbidwatcher.auction.Auctions;
import com.jbidwatcher.auction.AuctionEntry;

/**
 * Created by IntelliJ IDEA.
 * User: Morgan Schweers
 * Date: Aug 17, 2005
 * Time: 9:59:47 PM
 * To change this template use File | Settings | File Templates.
 */
public class Snipe {
  public final static int SUCCESSFUL=0;
  public final static int RESNIPE=1;
  public final static int FAIL=2;
  public static final int DONE = 3;

  private CookieJar m_cj = null;
  private AuctionEntry m_auction;
  private JHTML.Form m_bidForm = null;

  public Snipe(AuctionEntry ae) {
    m_auction = ae;
  }

  public String toString() {
    return "Snipe{" +
            "m_cj=" + m_cj +
            ", m_auction=" + m_auction +
            ", m_bidForm=" + m_bidForm +
            '}';
  }

  public int fire() {
    if(m_auction.getSnipeBid().getValue() < 0.0) {
      m_auction.setLastStatus("Snipe amount is negative.  Not sniping.");
      return FAIL;
    }
    //  Two stage firing.  First we fill the cookie jar.  The second time we submit the
    //  bid confirmation form.
    if(m_cj == null) {
      return presnipe();
    } else {
      return do_snipe();
    }
  }

  private int do_snipe() {
    //  Just punt if we had failed to get the bidding form initially.
    if(m_bidForm == null) return FAIL;
    Auctions.startBlocking();
    if(m_auction.isMultiSniped()) {
      MultiSnipe ms = m_auction.getMultiSnipe();
      //  Make sure there aren't any update-unfinished items.
      if(ms.anyEarlier(m_auction)) {
        m_auction.setLastStatus("An earlier snipe in this multisnipe group has not been updated.");
        m_auction.setLastStatus("This snipe is NOT being fired, as it could end up winning two items.");
        Auctions.endBlocking();
        return RESNIPE;
      }
    }
    MQFactory.getConcrete("Swing").enqueue("Sniping on " + m_auction.getTitle());
    AuctionServer as = m_auction.getServer();
    m_auction.setLastStatus("Firing actual snipe.");

    int rval = as.placeFinalBid(m_cj, m_bidForm, m_auction, m_auction.getSnipeBid(), m_auction.getSnipeQuantity());
    String snipeResult = getSnipeResult(rval, m_auction.getTitle(), m_auction);
    m_auction.setLastStatus(snipeResult);

    MQFactory.getConcrete("Swing").enqueue("NOTIFY " + snipeResult);
    ErrorManagement.logDebug(snipeResult);

    m_auction.snipeCompleted();
    Auctions.endBlocking();
    return DONE;
  }

  private int presnipe() {
    Auctions.startBlocking();
    AuctionServer as = m_auction.getServer();
    m_auction.setLastStatus("Preparing snipe.");
    //  Log in
    m_cj = as.getSignInCookie(null);
    if (m_cj == null) {
      //  Alert somebody that we couldn't log in?
      m_auction.setLastStatus("Pre-snipe login failed.  Snipe will be retried, but is unlikely to fire.");
      MQFactory.getConcrete("Swing").enqueue("NOTIFY Pre-snipe login failed.");
      ErrorManagement.logDebug("Pre-snipe login failed.");
      Auctions.endBlocking();
      return RESNIPE;
    }

    //  Get Affiliate Page if necessary.
    //try {
    //  as.safeGetAffiliate(m_cj, m_auction);
    //} catch (CookieJar.CookieException ignore) {
    //  //  Ignore this, it just means we won't get the affiliate bonus for it.
    //}
    int presnipeResult = SUCCESSFUL;

    //  Get Bid Key/Form
    try {
      m_bidForm = as.getBidForm(m_cj, m_auction, m_auction.getSnipeBid(), m_auction.getSnipeQuantity());
    } catch (AuctionServer.BadBidException bbe) {
      String result = getSnipeResult(bbe.getResult(), m_auction.getTitle(), m_auction);
      m_auction.setLastStatus(result);
      MQFactory.getConcrete("Swing").enqueue("NOTIFY " + result);
      ErrorManagement.logDebug(result);
      presnipeResult = FAIL;
    }
    Auctions.endBlocking();

    return presnipeResult;
  }

  public static String getSnipeResult(int snipeResult, String aucTitle, AuctionEntry aeFire) {
    String snipeOutput;
    if(snipeResult == AuctionServer.BID_WINNING || snipeResult == AuctionServer.BID_SELFWIN) {
      snipeOutput = "Successfully sniped a high bid on " + aucTitle + '!';
    } else if(snipeResult == AuctionServer.BID_DUTCH_CONFIRMED) {
      snipeOutput = "Successfully sniped a high dutch bid on " + aucTitle + '!';
    } else {
      switch(snipeResult) {
        case AuctionServer.BID_ERROR_UNKNOWN:
          snipeOutput = "Unknown error sniping on " + aucTitle;
          break;
        case AuctionServer.BID_ERROR_ENDED:
        case AuctionServer.BID_ERROR_CANNOT:
          snipeOutput = "Snipe apparently failed, as the auction cannot be bid on anymore: " + aucTitle;
          break;
        case AuctionServer.BID_ERROR_BANNED:
          snipeOutput = "Snipe failed, as you are disallowed from bidding on " + aeFire.getSeller() + "'s items.";
          break;
        case AuctionServer.BID_ERROR_TOO_LOW:
          snipeOutput = "Snipe was too low, and was not accepted.";
          break;
        case AuctionServer.BID_ERROR_TOO_LOW_SELF:
          snipeOutput = "Your bid was below or equal to your previous high bid, and was not accepted.";
          break;
        case AuctionServer.BID_ERROR_RESERVE_NOT_MET:
          snipeOutput = "Your snipe was successful, but it did not meet the reserve price.";
          break;
        case AuctionServer.BID_ERROR_AMOUNT:
          snipeOutput = "There is an error with the amount for the snipe on " + aucTitle + " (Probably snipe too low vs. current bids).";
          break;
        case AuctionServer.BID_ERROR_OUTBID:
          snipeOutput = "You have been outbid in your snipe on " + aucTitle;
          break;
        case AuctionServer.BID_ERROR_CONNECTION:
          snipeOutput = "Snipe failed due to connection problem.  Probably a timeout trying to reach eBay.";
          break;
        case AuctionServer.BID_ERROR_AUCTION_GONE:
          snipeOutput = "Your snipe failed because the item was removed from JBidwatcher before the bid executed.";
          break;
        case AuctionServer.BID_ERROR_ACCOUNT_SUSPENDED:
          snipeOutput = "You cannot interact with any auctions, your account has been suspended.";
          break;
        case AuctionServer.BID_ERROR_CANT_SIGN_IN:
          snipeOutput = "Sign in failed repeatedly during bid.  Check your username and password information in the Configuration Manager.";
          break;
        case AuctionServer.BID_ERROR_WONT_SHIP:
          snipeOutput = "You are registered in a country to which the seller doesn't ship.";
          break;
        case AuctionServer.BID_ERROR_REQUIREMENTS_NOT_MET:
          snipeOutput = "You don't meet some requirement the seller has set for the item.  Check the item details for more information.";
          break;
        case AuctionServer.BID_ERROR_MULTI:
          snipeOutput = "There is a problem with the multisnipe, an earlier entry hasn't finished updating.  Trying again shortly.";
          break;
        default:
          snipeOutput = "Something really bad happened, and I don't know what.";
          break;
      }
    }
    return snipeOutput;
  }

  public AuctionEntry getItem() {
    return m_auction;
  }
}
