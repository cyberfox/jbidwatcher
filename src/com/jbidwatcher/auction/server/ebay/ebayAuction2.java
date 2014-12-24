package com.jbidwatcher.auction.server.ebay;

import com.jbidwatcher.auction.ItemParser;
import com.jbidwatcher.auction.SpecificAuction;
import com.jbidwatcher.util.*;
import com.jbidwatcher.util.config.JConfig;
import com.jbidwatcher.util.queue.MQFactory;
import com.jbidwatcher.util.queue.PlainMessageQueue;

import java.util.Date;

/**
 * Extract information from a parsed auction details page.
 *
 * User: mrs
 * Date: 12/1/12
 * Time: 5:53 PM
 */
public class ebayAuction2 extends SpecificAuction {
  protected TT T;

  /**
   * Construct with a specific country property list.
   *
   * @param countryProperties The country file/property list to pull overrides and matchable text from.
   */
  protected ebayAuction2(TT countryProperties) {
    super();
    T = countryProperties;
  }

  /**
   * Sets title, url, thumbnail url, location, paypal, fixed price, end date, current price[+US], minimum bid, BIN price[+US],
   * shipping, insurance[+optionality], and identifier.
   * @param parse The record created containing all the fields detected.
   * @param seller The previous name of the seller, if we know it in advance.
   *
   * @return The parsing status; typically SELLER_AWAY, or SUCCESS right now. TODO: What other errors are possible...
   */
  public ItemParser.ParseErrors setFields(Record parse, String seller) {
    if(setSellerInfo(parse, seller)) return ItemParser.ParseErrors.SELLER_AWAY;

    setTitle(parse.get("title"));
    if(parse.get("thumbnail_url") != null) {
      setThumbnailURL(parse.get("thumbnail_url"));
      loadThumbnail();
    }
    setItemLocation(parse.get("location"));
    setPaypal(Boolean.parseBoolean(parse.get("paypal")));
    setFixedPrice(Boolean.parseBoolean(parse.get("fixed")));

    if (parse.containsKey("ending_at")) {
      Date endDate = StringTools.figureDate(parse.get("ending_at"), T.s("ebayServer.itemDateFormat")).getDate();
      setEnd(endDate);
    }

    if (parse.containsKey("price.current")) setCurBid(Currency.getCurrency(parse.get("price.current")));
    if (parse.containsKey("price.current_us")) setUSCur(Currency.getCurrency(parse.get("price.current_us")));
    if ("true".equals(parse.get("price.minimum"))) setMinBid(getCurBid());

    if(parse.containsKey("price.bin")) setBuyNow(Currency.getCurrency(parse.get("price.bin")));
    if(parse.containsKey("price.bin_us")) setBuyNowUS(Currency.getCurrency(parse.get("price.bin_us")));

    if(parse.containsKey("shipping.shipping")) setShipping(Currency.getCurrency(parse.get("shipping.shipping")));
    if(parse.containsKey("shipping.insurance")) setInsurance(Currency.getCurrency(parse.get("shipping.insurance")));
    if(parse.containsKey("shipping.insurance_optional")) setInsuranceOptional(Boolean.valueOf(parse.get("shipping.insurance_optional")));

    if(parse.containsKey("identifier")) setIdentifier(parse.get("identifier"));
    if(parse.containsKey("bid_count")) setNumBids(Integer.valueOf(parse.get("bid_count")));

    if("true".equals(parse.get("complete"))) setEnded(true);

    return ItemParser.ParseErrors.SUCCESS;
  }

  private boolean setSellerInfo(Record parse, String seller) {
    String sellerName = handleSellerName(parse, seller);
    if (sellerName == null) return true;
    setSellerName(sellerName);
    if(mSeller != null) {
      if(parse.containsKey("feedback.feedback")) {
        mSeller.setFeedback(Integer.parseInt(parse.get("feedback.feedback")));
      }

      if(parse.containsKey("feedback.percentage")) {
        mSeller.setPositivePercentage(parse.get("feedback.percentage"));
      }
    }
    return false;
  }

  private String handleSellerName(Record parse, String seller) {
    String sellerName = parse.get("seller");

    if (sellerName != null) {
      if(sellerName.length() == 0) {
        if (seller == null) {
          sellerName = "(unknown)";
        } else {
          sellerName = seller;
        }
      }
    }

    return sellerName;
  }

  private void requestHighBidder() {
    MQFactory.getConcrete("high_bidder").enqueue(getIdentifier());
  }

  private void requestEndDate() {
    MQFactory.getConcrete("end_date").enqueue(getIdentifier());
  }

  private void loadThumbnail() {
    try {
      if (JConfig.queryConfiguration("show.images", "true").equals("true")) {
        if (!hasNoThumbnail() && !hasThumbnail()) {
          ((PlainMessageQueue) MQFactory.getConcrete("thumbnail")).enqueueObject(this);
        }
      }
    } catch (Exception e) {
      JConfig.log().handleException("Error handling thumbnail loading", e);
      JConfig.getMetrics().trackEvent("failure", "thumbnail_load");
    }
  }
}
