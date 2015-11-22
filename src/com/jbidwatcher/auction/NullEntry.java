package com.jbidwatcher.auction;

import com.jbidwatcher.util.Constants;
import com.jbidwatcher.util.Currency;

import java.util.Date;

/**
 * Created by mrs on 11/21/15.
 * Provide an 'empty' Auction Entry for various purposes, and so we don't have to pass nulls around.
 */
public class NullEntry extends AuctionEntry {
  @Override
  public Currency getSnipeAmount() {
    return Currency.NoValue();
  }

  @Override
  public int getSnipeQuantity() {
    return 0;
  }

  @Override
  AuctionSnipe getSnipe() {
    return null;
  }

  @Override
  public boolean isSnipeValid() {
    return false;
  }

  @Override
  public boolean isSniped() {
    return false;
  }

  @Override
  public boolean isBidOn() {
    return false;
  }

  @Override
  public boolean isHighBidder() {
    return false;
  }

  @Override
  public boolean isWinning() {
    return false;
  }

  @Override
  public boolean isSeller() {
    return false;
  }

  @Override
  public Currency getBid() {
    return Currency.NoValue();
  }

  @Override
  public long getSnipeTime() {
    return Constants.FAR_FUTURE.getTime();
  }

  @Override
  public boolean hasDefaultSnipeTime() {
    return true;
  }

  @Override
  public boolean isJustAdded() {
    return true; // So it'll show up annotated a little differently.
  }

  @Override
  public boolean isInvalid() {
    return false;
  }

  @Override
  public String getComment() {
    return "";
  }

  @Override
  public String getLastStatus() {
    return "No status.";
  }

  @Override
  public String getStatusHistory() {
    return "";
  }

  @Override
  public int getStatusCount() {
    return 0;
  }

  @Override
  public Currency getCancelledSnipe() {
    return Currency.NoValue();
  }


  @Override
  public Date getLastUpdated() {
    return Constants.FAR_FUTURE;
  }

  @Override
  public String getCategory() {
    return "";
  }

  @Override
  public boolean isSticky() {
    return false;
  }

  @Override
  public String getTimeLeft() {
    return "N/a";
  }

  @Override
  public String getTitleAndComment() {
    return "";
  }

  @Override
  public int getFlags() {
    return 0;
  }

  @Override
  public boolean isNullAuction() {
    return true;
  }

  @Override
  public AuctionInfo getAuction() {
    return null;
  }

  @Override
  public Currency getCurrentPrice() {
    return Currency.NoValue();
  }

  @Override
  public Currency getCurrentUSPrice() {
    return Currency.NoValue();
  }

  @Override
  public String getSellerName() {
    return "";
  }

  @Override
  public Date getStartDate() {
    return Constants.LONG_AGO;
  }

  @Override
  public Date getSnipeDate() {
    return null;
  }

  @Override
  public String getBrowseableURL() {
    return "";
  }

  private static final StringBuffer emptyBuffer = new StringBuffer();
  @Override
  public StringBuffer getErrorPage() {
    return emptyBuffer;
  }

  @Override
  public Currency getShippingWithInsurance() {
    return Currency.NoValue();
  }

  @Override
  public boolean isDeleted() {
    return false;
  }

  @Override
  public String getAuctionId() {
    return "";
  }

  @Override
  public boolean isUpdateRequired() {
    return false;
  }

  @Override
  public String getUnique() {
    return "nullEntry";
  }

  @Override
  public String getIdentifier() {
    return "***";
  }

  @Override
  public String getTitle() {
    return "Placeholder";
  }

  @Override
  public String getHighBidder() {
    return "";
  }

  @Override
  public String getItemLocation() {
    return "";
  }

  @Override
  public boolean isComplete() {
    return false;
  }

  @Override
  public Currency getBestPrice() {
    return Currency.NoValue();
  }

  @Override
  public Currency getCurBid() {
    return Currency.NoValue();
  }

  @Override
  public Currency getUSCurBid() {
    return Currency.NoValue();
  }

  @Override
  public Currency getMinBid() {
    return Currency.NoValue();
  }

  @Override
  public Currency getShipping() {
    return Currency.NoValue();
  }

  @Override
  public Currency getInsurance() {
    return Currency.NoValue();
  }

  @Override
  public Currency getBuyNow() {
    return Currency.NoValue();
  }

  @Override
  public int getQuantity() {
    return 0;
  }

  @Override
  public int getNumBidders() {
    return 0;
  }

  @Override
  public int getNumBids() {
    return 0;
  }

  @Override
  public Date getEndDate() {
    return Constants.FAR_FUTURE;
  }

  @Override
  public boolean isReserve() {
    return false;
  }

  @Override
  public boolean isPrivate() {
    return false;
  }

  @Override
  public boolean isFixed() {
    return false;
  }

  @Override
  public boolean hasPaypal() {
    return false;
  }

  @Override
  public boolean isReserveMet() {
    return true;
  }

  @Override
  boolean isInsuranceOptional() {
    return true;
  }

  @Override
  protected boolean hasNoThumbnail() {
    return true;
  }

  @Override
  public Currency getUSCur() {
    return Currency.NoValue();
  }

  @Override
  public Date getStart() {
    return Constants.LONG_AGO;
  }

  @Override
  public Currency getBuyNowUS() {
    return Currency.NoValue();
  }

  @Override
  public Date getEnd() {
    return Constants.FAR_FUTURE;
  }

  @Override
  public String getSellerId() {
    return "";
  }

  @Override
  protected boolean hasThumbnail() {
    return false;
  }

  @Override
  public String getThumbnail() {
    return "";
  }
}
