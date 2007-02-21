package com.jbidwatcher.auction;

import com.jbidwatcher.util.Currency;
import com.jbidwatcher.util.ByteBuffer;

import java.util.Date;

/**
 * Created by IntelliJ IDEA.
* User: Morgan
* Date: Sep 27, 2006
* Time: 1:22:07 AM
* To change this template use File | Settings | File Templates.
*/
class MockAuctionInfo extends AuctionInfo {
  private long truncThousands(long time) {
    return (time / 1000) * 1000;
  }

  public MockAuctionInfo() {
    _identifier = "12345678";
    _curBid = Currency.getCurrency("$9.99");
    _minBid = Currency.getCurrency("$9.99");
    _shipping = Currency.getCurrency("$1.99");
    _insurance = Currency.getCurrency("$0.99");
    _us_cur = Currency.getCurrency("$9.99");
    _buy_now_us = Currency.getCurrency("$19.99");
    _insurance_optional = false;
    _fixed_price = false;
    _no_thumbnail = true;
    _buy_now = Currency.getCurrency("$19.99");
    _start = new Date(truncThousands(System.currentTimeMillis() - 1000 * 60 * 60 * 24 * 6));
    _end = new Date(truncThousands(System.currentTimeMillis() + 1000 * 60 * 60 * 24));
    _seller = "cyberfox";
    _highBidder = "test-jbidwatcher-bids";
    _title = "A test auction.";
    _highBidderEmail = null;
    _quantity = 1;
    _numBids = 1;
    _isDutch = false;
    _isReserve = false;
    _isPrivate = false;
    _reserveMet = false;
    _hasThumb = false;
    _outbid = false;
    _feedback = 139;
    _postivePercentage = "99.6%";
    _itemLocation = "Test County, USA";
    _paypal = true;
    _loadedPage = null;
  }

  public ByteBuffer getSiteThumbnail() {
    return null;
  }

  public ByteBuffer getAlternateSiteThumbnail() {
    return null;
  }
}
