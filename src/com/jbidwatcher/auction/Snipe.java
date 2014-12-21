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
  private final MultiSnipeManager mMultiManager;

  private CookieJar mCJ = null;
  private AuctionEntry mEntry;
  private JHTML.Form mBidForm = null;
  private LoginManager mLogin;
  private Bidder mBidder;

  public Snipe(MultiSnipeManager multiManager, LoginManager login, Bidder bidder, AuctionEntry ae) {
    mLogin = login;
    mEntry = ae;
    mBidder = bidder;
    mMultiManager = multiManager;
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
    MultiSnipe ms = mMultiManager.getForAuctionIdentifier(mEntry.getIdentifier());
    if(ms != null) {
      //  Make sure there aren't any update-unfinished items.
      if(ms.anyEarlier(mEntry)) {
        mEntry.setLastStatus("An earlier snipe in this multisnipe group has not ended, or has not been updated after ending.");
        mEntry.setLastStatus("This snipe is NOT being fired, as it could end up winning two items.");
        UpdateBlocker.endBlocking();
        return RESNIPE;
      }
    }
    MQFactory.getConcrete("Swing").enqueue("Sniping on " + mEntry.getTitle());
    mEntry.setLastStatus("Firing actual snipe.");

    // Metrics
    JConfig.getMetrics().trackEvent("snipe", "fired");
    int rval = mBidder.placeFinalBid(mCJ, mBidForm, mEntry, mEntry.getSnipeAmount(), mEntry.getSnipeQuantity());
    boolean success = (rval == AuctionServer.BID_WINNING || rval == AuctionServer.BID_SELFWIN);
    // Metrics
    if(success) {
      JConfig.getMetrics().trackEvent("snipe", "success");
    } else {
      JConfig.getMetrics().trackEventValue("snipe", "fail", Integer.toString(rval));
    }
    JConfig.increment("stats.sniped");
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
      JConfig.increment("stats.presniped");
      mBidForm = mBidder.getBidForm(mCJ, mEntry, mEntry.getSnipeAmount());
      if(mBidForm.getInputValue("maxbid").length() == 0) {
        // We have a problem.
        mBidForm.setText("maxbid", mEntry.getSnipeAmount().getValueString());
      }
      // Metrics
      JConfig.getMetrics().trackEvent("presnipe", "success");
    } catch (BadBidException bbe) {
      String result = getSnipeResult(bbe.getResult(), mEntry.getTitle(), mEntry);
      mEntry.setLastStatus(result);
      MQFactory.getConcrete("Swing").enqueue("NOTIFY " + result);
      JConfig.log().logDebug(result);
      presnipeResult = FAIL;
      // Metrics
      JConfig.getMetrics().trackEventValue("presnipe", "fail", Integer.toString(bbe.getResult()));
    }
    UpdateBlocker.endBlocking();

    return presnipeResult;
  }

  public static String getSnipeResult(int snipeResult, String aucTitle, AuctionEntry aeFire) {
    String snipeOutput;
    if(snipeResult == AuctionServerInterface.BID_WINNING || snipeResult == AuctionServerInterface.BID_SELFWIN) {
      snipeOutput = "Successfully sniped a high bid on " + aucTitle + '!';
      JConfig.increment("stats.sniped.success");
    } else {
      switch(snipeResult) {
        case AuctionServerInterface.BID_ERROR_UNKNOWN:
          snipeOutput = "Unknown error sniping on " + aucTitle + " (" + aeFire.getIdentifier() + ")";
          JConfig.increment("stats.sniped.unknown_error");
          break;
        case AuctionServerInterface.BID_ERROR_ENDED:
        case AuctionServerInterface.BID_ERROR_CANNOT:
          snipeOutput = "Snipe apparently failed, as the auction cannot be bid on anymore: " + aucTitle;
          JConfig.increment("stats.sniped.too_late");
          break;
        case AuctionServerInterface.BID_ERROR_BANNED:
          snipeOutput = "Snipe failed, as you are disallowed from bidding on " + aeFire.getSellerName() + "'s items.";
          JConfig.increment("stats.sniped.banned");
          break;
        case AuctionServerInterface.BID_ERROR_TOO_LOW:
          snipeOutput = "Snipe was too low, and was not accepted.";
          JConfig.increment("stats.sniped.too_low");
          break;
        case AuctionServerInterface.BID_ERROR_TOO_LOW_SELF:
          snipeOutput = "Your bid was below or equal to your previous high bid, and was not accepted.";
          JConfig.increment("stats.sniped.too_low");
          break;
        case AuctionServerInterface.BID_ERROR_RESERVE_NOT_MET:
          snipeOutput = "Your snipe was successful, but it did not meet the reserve price.";
          JConfig.increment("stats.sniped.too_low");
          break;
        case AuctionServerInterface.BID_ERROR_AMOUNT:
          snipeOutput = "There is an error with the amount for the snipe on " + aucTitle + " (Probably snipe too low vs. current bids).";
          JConfig.increment("stats.sniped.too_low");
          break;
        case AuctionServerInterface.BID_ERROR_OUTBID:
          snipeOutput = "You have been outbid in your snipe on " + aucTitle;
          JConfig.increment("stats.sniped.outbid");
          break;
        case AuctionServerInterface.BID_ERROR_CONNECTION:
          snipeOutput = "Snipe failed due to connection problem.  Probably a timeout trying to reach eBay.";
          JConfig.increment("stats.sniped.connection_error");
          break;
        case AuctionServer.BID_ERROR_AUCTION_GONE:
          snipeOutput = "Your snipe failed because the item was removed from JBidwatcher before the bid executed.";
          JConfig.increment("stats.sniped.removed");
          break;
        case AuctionServer.BID_ERROR_ACCOUNT_SUSPENDED:
          snipeOutput = "You cannot interact with any auctions, your account has been suspended.";
          JConfig.increment("stats.sniped.suspended");
          break;
        case AuctionServer.BID_ERROR_CANT_SIGN_IN:
          snipeOutput = "Sign in failed repeatedly during bid.  Check your username and password information in the Configuration Manager.";
          JConfig.increment("stats.sniped.sign_in");
          break;
        case AuctionServer.BID_ERROR_WONT_SHIP:
          snipeOutput = "You are registered in a country to which the seller doesn't ship.";
          JConfig.increment("stats.sniped.wont_ship");
          break;
        case AuctionServer.BID_ERROR_REQUIREMENTS_NOT_MET:
          snipeOutput = "You don't meet some requirement the seller has set for the item.  Check the item details for more information.";
          JConfig.increment("stats.sniped.requirement_not_met");
          break;
        case AuctionServer.BID_ERROR_SELLER_CANT_BID:
          snipeOutput = "Sellers are not allowed to bid on their own items.";
          break;
        case AuctionServerInterface.BID_ERROR_MULTI:
          snipeOutput = "There is a problem with the multisnipe, an earlier entry hasn't finished updating.  Trying again shortly.";
          JConfig.increment("stats.sniped.multisnipe_problem");
          break;
        default:
          snipeOutput = "Something really bad happened, and I don't know what.";
          JConfig.increment("stats.sniped.really_bad");
          break;
      }
    }
    return snipeOutput;
  }

  public AuctionEntry getItem() {
    return mEntry;
  }
}
