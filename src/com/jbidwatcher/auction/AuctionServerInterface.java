package com.jbidwatcher.auction;

import com.jbidwatcher.util.Currency;

import java.util.TimeZone;
import java.net.URL;
import java.io.FileNotFoundException;

/**
 * User: Morgan
 * Date: Feb 20, 2007
 * Time: 1:10:47 AM
 * To change this template use File | Settings | File Templates.
 */
public interface AuctionServerInterface {
  int BID_ERROR_UNKNOWN=-1;
  int BID_ERROR_CANNOT=1;
  int BID_ERROR_AMOUNT=2;
  int BID_ERROR_OUTBID=3;
  int BID_WINNING=4;
  int BID_SELFWIN=5;
//  int BID_DUTCH_CONFIRMED=6;  --  This is obsolete.
  int BID_ERROR_MULTI=7;
  int BID_ERROR_TOO_LOW=8;
  int BID_ERROR_ENDED=9;
  int BID_ERROR_BANNED=10;
  int BID_ERROR_RESERVE_NOT_MET=11;
  int BID_ERROR_CONNECTION=12;
  int BID_ERROR_TOO_LOW_SELF = 13; // You can't bid that low against yourself...
  int BID_ERROR_AUCTION_GONE = 14; // Auction vanished between bid creation and submission.
  int BID_ERROR_NOT_BIN = 15; // Trying to 'Buy' an item that isn't a BIN/Fixed Price listing.
  int BID_BOUGHT_ITEM = 16; //  Successfully bought an item via BIN.
  int BID_ERROR_ACCOUNT_SUSPENDED = 17; //  Your account has been (!) suspended, you can't bid.
  int BID_ERROR_CANT_SIGN_IN = 18; //  We tried to get bid pages, but it kept asking for login.
  int BID_ERROR_WONT_SHIP = 19; //  You are registered in a country to which the seller doesn't ship.
  int BID_ERROR_REQUIREMENTS_NOT_MET = 20; //  This seller has set buyer requirements for this item and only sells to buyers who meet those requirements.
  int BID_ERROR_SELLER_CANT_BID=21; // Sellers can not bid on their own items
  int BID_NOT_AVAILABLE_FOR_PURCHASE=22; // Item is not available for purchase (generally on eBay US).
  String UPDATE_LOGIN_COOKIE = "Update login cookie";

  int buy(String auctionId, int quantity);

  String extractIdentifierFromURLString(String urlStyle);

  int bid(String auctionId, Currency inBid, int inQuantity);

  Currency getMinimumBidIncrement(Currency currentBid, int bidCount);

  /**
   * @brief Get the URL (in String form that a browser can view with) for a given item ID on this auction server.
   *
   * @param itemID - The item to retrieve the URL for.
   *
   * @return - A String with the full URL of the item description on the auction server.
   */
  String getBrowsableURLFromItem(String itemID);

  String getTime();

  /**
   * @brief Load an auction from the server based on item ID, and return it.
   *
   * @param itemId - The auction item id to load and create.
   *
   * @return - The underlying 'AuctionInfo' object that contains all
   * the basic accessors for auction data.
   */
  AuctionInfo create(String itemId);

  /**
   * @brief Get the human-readable auction site name for this server.
   *
   * @return - A String with the human-readable auction site name.
   */
  String getName();

  /**
   * @brief Get the specific site name for this AuctionServer instance.
   *
   * @return - A String with the site name this server refers to; generally associated with the string bundle being used by the server.
   */
  String getFriendlyName();

  /**
   * @brief Returns the difference in time between the local machine's
   * normalized time, and the auction site's normalized time.
   *
   * @return The amount of milliseconds off the server time is from
   * local time.
   */
  long getServerTimeDelta();

  /**
   * @brief Retrieve what time zone the server is in.
   *
   * @return - The time zone of the auction server.
   */
  TimeZone getOfficialServerTimeZone();

  void reload(String auctionId);

  /**
   * @brief Returns the amount of time it takes to retrieve a page
   * from the auction server.
   *
   * @return The amount of milliseconds it takes to get a simple page
   * from the auction server.
   */
  long getPageRequestTime();

  public long getAdjustedTime();

  void reloadTime();

  public boolean validate(String username, String password);

  boolean isDefaultUser();

  String getStringURLFromItem(String identifier);

  StringBuffer getAuction(URL url) throws FileNotFoundException;

  boolean isCurrentUser(String checkUser);

  void updateHighBid(String auctionId);

  String stripId(String source);

  void setSnipe(String auctionId);

  void cancelSnipe(String identifier);
}
