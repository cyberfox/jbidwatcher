package com.jbidwatcher.auction;

import com.jbidwatcher.util.GZip;
import com.jbidwatcher.util.Currency;
import com.jbidwatcher.util.xml.XMLElement;

import java.util.Date;

/**
 * Created by IntelliJ IDEA.
* User: Morgan
* Date: Sep 27, 2006
* Time: 1:27:33 AM
* To change this template use File | Settings | File Templates.
*/
class MockSpecificAuction extends SpecificAuction {
  private MockAuctionInfo mAI;
  public MockSpecificAuction(MockAuctionInfo mockAuctionInfo) {
    mAI = mockAuctionInfo;
  }

  public ParseErrors parseAuction(AuctionEntry ae) {
    return ParseErrors.SUCCESS;
  }

  public void cleanup(StringBuffer sb) {
  }

  public String getThumbnailURL() {
    return mAI.getThumbnailURL();
  }

  public String getAlternateSiteThumbnail() {
    return mAI.getAlternateSiteThumbnail();
  }

  public String[] getTags() {
    return mAI.getTags();
  }

  public void handleTag(int i, XMLElement curElement) {
    mAI.handleTag(i, curElement);
  }

  public XMLElement toXML() {
    return mAI.toXML();
  }

//  public void setThumbnail(ByteBuffer newThumb) {
//    mAI.setThumbnail(Thumbnail.getValidImagePath(getIdentifier(), newThumb));
//  }
//
  public void save() {
    mAI.save();
  }

  public boolean hasThumbnail() {
    return mAI.hasThumbnail();
  }

  public String getThumbnail() {
    return mAI.getThumbnail();
  }

  public void setRealContent(StringBuffer changedContent, boolean final_data) {
    mAI.setRealContent(changedContent, final_data);
  }

  public void setRealContent(byte[] changedContent, boolean final_data) {
    mAI.setRealContent(changedContent, final_data);
  }

  public GZip getRealContent() {
    return mAI.getRealContent();
  }

  public void setContent(StringBuffer inContent, boolean final_data) {
    mAI.setContent(inContent, final_data);
  }

  public StringBuffer getContent() {
    return mAI.getContent();
  }

  public void setIdentifier(String id) {
    mAI.setIdentifier(id);
  }

  public String getIdentifier() {
    return mAI.getIdentifier();
  }

  public Currency getCurBid() {
    return mAI.getCurBid();
  }

  public Currency getUSCurBid() {
    return mAI.getUSCurBid();
  }

  public Currency getMinBid() {
    return mAI.getMinBid();
  }

  public Currency getShipping() {
    return mAI.getShipping();
  }

  public Currency getInsurance() {
    return mAI.getInsurance();
  }

  public boolean isInsuranceOptional() {
    return mAI.isInsuranceOptional();
  }

  public Currency getBuyNow() {
    return mAI.getBuyNow();
  }

  public int getQuantity() {
    return mAI.getQuantity();
  }

  public int getNumBidders() {
    return mAI.getNumBidders();
  }

  public String getSellerName() {
    return mAI.getSellerName();
  }

  public String getHighBidder() {
    return mAI.getHighBidder();
  }

  public String getHighBidderEmail() {
    return mAI.getHighBidderEmail();
  }

  public String getTitle() {
    return mAI.getTitle();
  }

  public Date getStartDate() {
    return mAI.getStartDate();
  }

  public Date getEndDate() {
    return mAI.getEndDate();
  }

  public boolean isDutch() {
    return mAI.isDutch();
  }

  public boolean isReserve() {
    return mAI.isReserve();
  }

  public boolean isPrivate() {
    return mAI.isPrivate();
  }

  public boolean isFixed() {
    return mAI.isFixedPrice();
  }

  public boolean isReserveMet() {
    return mAI.isReserveMet();
  }

  public boolean isOutbid() {
    return mAI.isOutbid();
  }

  public boolean hasPaypal() {
    return mAI.hasPaypal();
  }

  public String getItemLocation() {
    return mAI.getItemLocation();
  }

  public String getPositiveFeedbackPercentage() {
    return mAI.getPositiveFeedbackPercentage();
  }

  public int getFeedbackScore() {
    return mAI.getFeedbackScore();
  }

  public void fromXML(XMLElement inXML) {
    mAI.fromXML(inXML);
  }
}
