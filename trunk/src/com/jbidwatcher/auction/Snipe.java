package com.jbidwatcher.auction;

/*
 * Copyright (c) 2000-2007, CyberFOX Software, Inc. All Rights Reserved.
 *
 * Developed by mrs (Morgan Schweers)
 * Date: Aug 17, 2005
 * Time: 9:59:47 PM
 *
 * TODO - Create a 'Bid' equivalent of this class.
 */

import com.jbidwatcher.auction.server.AuctionServer;
import com.jbidwatcher.util.UpdateBlocker;
import com.jbidwatcher.util.config.JConfig;
import com.jbidwatcher.util.html.JHTML;
import com.jbidwatcher.util.http.CookieJar;
import com.jbidwatcher.util.queue.MQFactory;

public class Snipe {
  public final static int SUCCESSFUL=0;
  public final static int RESNIPE=1;
  public final static int FAIL=2;
  public static final int DONE = 3;

  private CookieJar mCJ = null;
  private AuctionEntry mEntry;
  private JHTML.Form mBidForm = null;
  private LoginManager mLogin;
  private Bidder mBidder;

  public Snipe(LoginManager login, Bidder bidder, AuctionEntry ae) {
    mLogin = login;
    mEntry = ae;
    mBidder = bidder;
  }

  public int fire() {
    if(mEntry.getSnipeAmount().getValue() < 0.0) {
      mEntry.setLastStatus("Snipe amount is negative.  Not sniping.");
      return FAIL;
    }
    //  Two stage firing.  First we fill the cookie jar.  The second time we submit the
    //  bid confirmation form.
    if(mCJ == null) {
      return preSnipe();
    } else {
      return doSnipe();
    }
  }

  private int doSnipe() {
    //  Just punt if we had failed to get the bidding form initially.
    if(mBidForm == null) return FAIL;
    UpdateBlocker.startBlocking();
    if(mEntry.isMultiSniped()) {
      MultiSnipe ms = mEntry.getMultiSnipe();
      //  Make sure there aren't any update-unfinished items.
      if(ms.anyEarlier(mEntry.getEndDate())) {
        mEntry.setLastStatus("An earlier snipe in this multisnipe group has not been updated.");
        mEntry.setLastStatus("This snipe is NOT being fired, as it could end up winning two items.");
        UpdateBlocker.endBlocking();
        return RESNIPE;
      }
    }
    MQFactory.getConcrete("Swing").enqueue("Sniping on " + mEntry.getTitle());
    mEntry.setLastStatus("Firing actual snipe.");

    int rval = mBidder.placeFinalBid(mCJ, mBidForm, mEntry, mEntry.getSnipeAmount(), mEntry.getSnipeQuantity());
    String snipeResult = getSnipeResult(rval, mEntry.getTitle(), mEntry);
    mEntry.setLastStatus(snipeResult);

    MQFactory.getConcrete("Swing").enqueue("NOTIFY " + snipeResult);
    JConfig.log().logDebug(snipeResult);

    mEntry.snipeCompleted();
    UpdateBlocker.endBlocking();
    return DONE;
  }

  private int preSnipe() {
    UpdateBlocker.startBlocking();
    mEntry.setLastStatus("Preparing snipe.");
    //  Log in
    mCJ = mLogin.getSignInCookie(null);
    if (mCJ == null) {
      //  Alert somebody that we couldn't log in?
      mEntry.setLastStatus("Pre-snipe login failed.  Snipe will be retried, but is unlikely to fire.");
      MQFactory.getConcrete("Swing").enqueue("NOTIFY Pre-snipe login failed.");
      JConfig.log().logDebug("Pre-snipe login failed.");
      UpdateBlocker.endBlocking();
      return RESNIPE;
    }

    int presnipeResult = SUCCESSFUL;

    //  Get Bid Key/Form
    try {
      mBidForm = mBidder.getBidForm(mCJ, mEntry, mEntry.getSnipeAmount(), mEntry.getSnipeQuantity());
    } catch (BadBidException bbe) {
      String result = getSnipeResult(bbe.getResult(), mEntry.getTitle(), mEntry);
      mEntry.setLastStatus(result);
      MQFactory.getConcrete("Swing").enqueue("NOTIFY " + result);
      JConfig.log().logDebug(result);
      presnipeResult = FAIL;
    }
    UpdateBlocker.endBlocking();

    return presnipeResult;
  }

  public static String getSnipeResult(int snipeResult, String aucTitle, AuctionEntry aeFire) {
    String snipeOutput;
    if(snipeResult == AuctionServerInterface.BID_WINNING || snipeResult == AuctionServerInterface.BID_SELFWIN) {
      snipeOutput = "Successfully sniped a high bid on " + aucTitle + '!';
    } else if(snipeResult == AuctionServerInterface.BID_DUTCH_CONFIRMED) {
      snipeOutput = "Successfully sniped a high dutch bid on " + aucTitle + '!';
    } else {
      switch(snipeResult) {
        case AuctionServerInterface.BID_ERROR_UNKNOWN:
          snipeOutput = "Unknown error sniping on " + aucTitle;
          break;
        case AuctionServerInterface.BID_ERROR_ENDED:
        case AuctionServerInterface.BID_ERROR_CANNOT:
          snipeOutput = "Snipe apparently failed, as the auction cannot be bid on anymore: " + aucTitle;
          break;
        case AuctionServerInterface.BID_ERROR_BANNED:
          snipeOutput = "Snipe failed, as you are disallowed from bidding on " + aeFire.getSeller() + "'s items.";
          break;
        case AuctionServerInterface.BID_ERROR_TOO_LOW:
          snipeOutput = "Snipe was too low, and was not accepted.";
          break;
        case AuctionServerInterface.BID_ERROR_TOO_LOW_SELF:
          snipeOutput = "Your bid was below or equal to your previous high bid, and was not accepted.";
          break;
        case AuctionServerInterface.BID_ERROR_RESERVE_NOT_MET:
          snipeOutput = "Your snipe was successful, but it did not meet the reserve price.";
          break;
        case AuctionServerInterface.BID_ERROR_AMOUNT:
          snipeOutput = "There is an error with the amount for the snipe on " + aucTitle + " (Probably snipe too low vs. current bids).";
          break;
        case AuctionServerInterface.BID_ERROR_OUTBID:
          snipeOutput = "You have been outbid in your snipe on " + aucTitle;
          break;
        case AuctionServerInterface.BID_ERROR_CONNECTION:
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
        case AuctionServerInterface.BID_ERROR_MULTI:
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
    return mEntry;
  }
}
