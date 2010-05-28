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

  public int buy(AuctionEntry ae, int quantity) {
    return 0;
  }

  public int bid(AuctionEntry inEntry, Currency inBid, int inQuantity) {
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

  public AuctionInfo reload(AuctionEntry inEntry) {
    return mMock;
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

  public AuctionServerInterface getBackupServer() {
    return null;  //To change body of implemented methods use File | Settings | File Templates.
  }

  public void updateWatchers(AuctionEntry ae) {
    //To change body of implemented methods use File | Settings | File Templates.
  }

  public void updateHighBid(AuctionEntry eEntry) { }

  public String stripId(String source) {
    return mMock.getIdentifier();
  }

  public void setSnipe(AuctionEntry snipeOn) { }

  public void cancelSnipe(String identifier) { }
}
