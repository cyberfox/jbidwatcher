package com.jbidwatcher.auction;

/*!@class BadBidException
 *
 * @brief Sometimes we need to be able to throw an exception when a
 * bid is bad, to simplify the error handling.
 */
public class BadBidException extends Exception {
  String _associatedString;
  int _aucResult;

  public BadBidException(String inString, int auction_result) {
    _associatedString = inString;
    _aucResult = auction_result;
  }

  /** @noinspection RefusedBequest*/
  public String toString() {
    return _associatedString;
  }

  public int getResult() {
    return _aucResult;
  }
}
