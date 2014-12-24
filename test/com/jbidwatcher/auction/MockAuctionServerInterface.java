package com.jbidwatcher.auction;

import com.jbidwatcher.util.Currency;

import java.util.TimeZone;
import java.net.URL;
import java.io.FileNotFoundException;

/**
 * Created by IntelliJ IDEA.
* User: mrs
* Date: Jan 16, 2009
* Time: 4:26:26 AM
* To change this template use File | Settings | File Templates.
*/
class MockAuctionServerInterface implements AuctionServerInterface {
  MockAuctionInfo mMock;

  public MockAuctionServerInterface(MockAuctionInfo mai) {
    mMock = mai;
  }

  public int buy(String auctionId, int quantity) {
    return 0;
  }

  public int bid(String auctionId, Currency inBid, int inQuantity) {
    return 0;
  }

  public String extractIdentifierFromURLString(String urlStyle) {
    return mMock.getIdentifier();
  }

  public Currency getMinimumBidIncrement(Currency currentBid, int bidCount) {
    return null;
  }

  public String getBrowsableURLFromItem(String itemID) {
    return "http://test.host/" + itemID;
  }

  public String getTime() {
    return null;
  }

  public AuctionInfo create(String itemId) {
    return mMock;
  }

  public String getName() {
    return "mock";
  }

  public String getFriendlyName() {
    return "ebay.mock";
  }

  public long getServerTimeDelta() {
    return 0;
  }

  public TimeZone getOfficialServerTimeZone() {
    return null;
  }

  public AuctionInfo reload(String auctionId) {
    return null;
  }

  public long getPageRequestTime() {
    return 0;
  }

  public long getAdjustedTime() {
    return 0;
  }

  public void reloadTime() { }

  public boolean validate(String username, String password) {
    return true;
  }

  public boolean isDefaultUser() {
    return false;
  }

  public String getStringURLFromItem(String identifier) {
    return "http://test.host/" + identifier;
  }

  public StringBuffer getAuction(URL url) throws FileNotFoundException {
    throw new FileNotFoundException("Mocks don't have files");
  }

  public boolean isCurrentUser(String checkUser) {
    return false;
  }

  public void updateHighBid(String auctionId) { }

  public String stripId(String source) {
    return mMock.getIdentifier();
  }

  public void setSnipe(String auctionId) { }

  public void cancelSnipe(String identifier) { }
}
