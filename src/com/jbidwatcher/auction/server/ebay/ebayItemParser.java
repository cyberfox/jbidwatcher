package com.jbidwatcher.auction.server.ebay;

import com.jbidwatcher.util.Record;
import com.jbidwatcher.util.StringTools;
import com.jbidwatcher.util.TT;
import com.jbidwatcher.util.html.JHTML;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.Map;

/**
 * Created by mrs on 12/23/14.
 */
public class ebayItemParser implements com.jbidwatcher.auction.ItemParser {
  protected DeprecatedEbayAuction deprecated = null;
  protected TT T;
  Map<String, String> microFormat = null;
  protected JHTML mDocument;
  protected Document mDocument2;
  protected StringBuffer mBuffer;
  protected Record updateData;
  protected Record highBidderData;

  public ebayItemParser(TT countryProperties, StringBuffer sb, Record updateData, Record highBidderData) {
    T = countryProperties;
    deprecated = new DeprecatedEbayAuction(T);
    mBuffer = sb;
  }

  public Record parseItemDetails() {
    mDocument2 = Jsoup.parse(mBuffer.toString());
    deprecated.setPage(mBuffer);

    mDocument = new JHTML(mBuffer);
    microFormat = mDocument.extractMicroformat();

    final Record parse = new Record();

    parse.put("title", extractMicroformatInfo("title"));

    // This is used to set the 'default currency' for this item later; it has no indication of BIN/Auction/Best Offer/2nd chance/etc.
    parse.put("price", extractMicroformatInfo("price"));
    insertChild(parse, "price", parsePrices());

    parse.put("thumbnail_url", parseThumbnailURL());
    parse.put("url", parseURL());

    // These are optional; we don't care about them failing. They may be unavailable, or parsing has changed, and it doesn't matter.
    try { parse.put("location", parseLocation()); } catch (Exception e) { /* Ignored */ }
    try { parse.put("paypal", Boolean.toString(supportsPaypal())); } catch (Exception e) { /* Ignored */ }
    try { insertChild(parse, "feedback", deprecated.parseFeedback(mDocument)); } catch(Exception e){ /* Ignored */ }
    try { insertChild(parse, "shipping", deprecated.parseShippingInsurance(mDocument)); } catch (Exception e) { /* Ignored */ }
    try { parse.put("seller", deprecated.parseSeller(mDocument)); } catch (Exception e) { /* Ignored */ }

    parse.put("fixed", Boolean.toString((parse.containsKey("price.bin") && !(parse.containsKey("price.current")))));

    boolean privateListing = parsePrivate();
    parse.put("private", Boolean.toString(privateListing));

    // high_bidder
    // Requires loading the bid page...and knowing the count of bids so far.
    parse.put("high_bidder", privateListing ? "(private)" : parseHighBidder());

    boolean reserve = parseReserveNotMet();
    parse.put("reserve", Boolean.toString(reserve));
    if(reserve) {
      parse.put("reserve_met", "false");
    }

    parse.put("ending_at", parseEndDate());

    parse.put("identifier", parseIdentifier());

    Record complex = deprecated.getBidCount(mDocument, 1);
    parse.put("bid_count", complex.get("bid_count"));

    if(mDocument.grep(T.s("ebayServer.ended")) != null) {
      parse.put("complete", "true");
    }

    // TODO(cyberfox) - Left to parse:
    // quantity (fixed price only)

    // Currency maxBid = Currency.getCurrency(parse.get("price.max"))
    // if(!maxBid.isNull()) ae.setBid(maxBid)

    return parse;
  }

  private boolean supportsPaypal() {
    boolean paypal = deprecated.parsePaypal(mDocument);
    paypal = paypal || !mDocument2.select(":containsOwn(Payments:)").parents().first().select("[alt=PayPal]").isEmpty();
    return paypal;
  }

  private boolean parseFixedPrice() {
    return mDocument.hasSequence("Price:", ".*", "(?i)Buy.It.Now") || !mDocument2.select("input[value=Buy It Now]").isEmpty();
  }

  //  Mozilla/5.0 (iPhone; CPU iPhone OS 6_1 like Mac OS X; en-us) AppleWebKit/536.26 (KHTML, like Gecko) CriOS/23.0.1271.100 Mobile/10B144 Safari/8536.25
  //  http://item.mobileweb.ebay.com/viewitem?itemId=200891621147
  private String parseEndDate() {
    String endDate;
    Elements leaves = mDocument2.getElementsContainingOwnText("Time left:");
    if(leaves != null && !leaves.isEmpty()) {
      for(Element leaf : leaves) {
        Element parent = leaf.parent();
        Elements kids = parent.getElementsMatchingOwnText("^\\(.*");
        if(kids != null && !kids.isEmpty()) {
          Elements lastly = kids.first().parent().getElementsMatchingOwnText("^.*\\)$");
          if(lastly != null && !lastly.isEmpty()) {
            endDate = kids.first().text() + " " + lastly.text();
            endDate = endDate.replaceAll("\\(|\\)", "");
            return endDate;
          }
        }
      }
    }

    //  Ended date stamps are formatted a little peculiarly.
    Elements endedDates = mDocument2.select(".endedDate");
    if(!endedDates.isEmpty()) {
      for(Element e : endedDates) {
        if("Ended:".equals(e.parent().parent().previousElementSibling().text())) {
          return e.parent().text();
        }
      }
    }
    return null;
  }

  /**
   * Parse the prices from the item description; prices include current price, buy-it-now price, minimum price and US equivalents.
   * @return A hash containing some set of [current, bin, minimum, current_us, bin_us, minimum_us]
   */
  private Record parsePrices() {
    Elements offers = mDocument2.select("[itemprop=offers]");
    if(offers.first() == null) {
      Record r = checkTwitterMicroformat();
      if(r.isEmpty()) {
        return deprecated.parsePrices(mDocument);
      } else {
        return r;
      }
    }

    Record record = new Record();
    String optional_conversion = "";
    String price = offers.first().select("[itemprop=price]").text();
    String convertedBinPrice = offers.select(".convPrice #binPrice").text();
    String convertedBidPrice = offers.select(".convPrice #bidPrice").text();
    String key = "current";
    boolean minimum = false;

    if(offers.size() == 1) {
      // It's either FP or Current...TODO(cyberfox) but it's possible it's the minimum bid, and so minimum should be set to true!
      boolean auction = !parseFixedPrice();
//      auction = auction || offers.select(":contains(Price)").text().length() == 0;

      if(auction) {
        optional_conversion = convertedBidPrice;
      } else {
        key = "bin";
        optional_conversion = convertedBinPrice;
      }
    } else if(offers.size() == 2) {
      // If there are 2 offers, the last is the Buy-It-Now amount and the first is the minimum bid amount.
      record.put("bin", offers.last().select("[itemprop=price]").text());
      if (convertedBinPrice.length() != 0) record.put("bin_us", convertedBinPrice);

      minimum = true;
      optional_conversion = convertedBidPrice;
    }

    record.put("minimum", Boolean.toString(minimum));
    record.put(key, price);
    if (optional_conversion.length() != 0) record.put(key + "_us", optional_conversion);

    // Either bin, current, or [bin, current] will be set.
    // If minimum is true, then current is the minimum bid.
    // Both bin and current can have *_us versions, which is the converted price.
    return record;
  }

  private Record checkTwitterMicroformat() {
    Record r = new Record();

    //  Twitter microformat
    Elements twitter = mDocument2.select("meta[name=twitter:text:price]");
    if (twitter.isEmpty()) {
      twitter = mDocument2.select("meta[name=twitter:label1,content=Price]");
      if (!twitter.isEmpty()) {
        twitter = mDocument2.select("meta[name=twitter:data1]");
      }
    }

    if(!twitter.isEmpty()) {
      r.put("current", twitter.first().attr("content"));
    }
    return r;
  }

  /**
   * Insert all elements of a child hash as keys in `parent` prefixed by `prefix`.`child.key`.
   *
   * @param parent The record to insert the child keys into.
   * @param prefix The prefix to prepend before each child key when constructing the parent key.
   * @param child The record containing key/values to store in the parent.
   */
  private void insertChild(Record parent, String prefix, Record child) {
    for(String key : child.keySet()) {
      parent.put(prefix + "." + key, child.get(key));
    }
  }

  /**
   * Extracts a value from either a microformat, or the og:* meta namespace.
   *
   * @param key The microformat key to look up, or the og:* suffix to retrieve.
   * @return Any microformat content or meta content found or a blank string if it wasn't found or was empty.
   */
  /* @NotNull */
  private String extractMicroformatInfo(String key) {
    String value = microFormat.get(key);
    if(value == null || value.length() == 0) {
      value = mDocument2.select("meta[property=og:" + key + "]").attr("content");
    }

    return StringTools.nullSafe(value);
  }

  /**
   * Extract the URL for this item's details page.
   * Preferably extracted from the microformat or meta information, but alternatively falling back to the canonical URL.
   *
   * @return The URL for this item's details page or an empty string.
   */
  /* @NotNull */
  private String parseURL() {
    String url = extractMicroformatInfo("url");
    if(url.length() == 0) {
      url = mDocument2.select("link[rel=canonical]").attr("href");
    }

    return url;
  }

  /**
   * Returns the thumbnail URL for this item; if possible it converts it to the highest resolution image link it can.
   *
   * @return The URL for a thumbnail image of this item or an empty string.
   */
  /* @NotNull */
  private String parseThumbnailURL() {
    String thumbnailURL = extractMicroformatInfo("image");
    if (thumbnailURL.matches(".*_\\d+\\.[a-zA-Z]+")) {
      thumbnailURL = thumbnailURL.replaceFirst("_\\d+\\.", "_10.");
    }

    return thumbnailURL;
  }

  /**
   * Parse the location (where in the world) for the item.
   *
   * @return The item location or null.
   */
  /* @NotNull */
  private String parseLocation() {
    String location = StringTools.nullSafe(mDocument.getNextContentAfterRegex(T.s("ebayServer.itemLocationRegex")));
    location = location.replace("Post to: ", "");
    location = StringTools.decode(location, mDocument.getCharset());

    return location;
  }

  private boolean parsePrivate() {
    return mDocument.grep(T.s("ebayServer.privateListing")) != null;
    // Another way of detecting private bidder is as follows:
    //    String highBidder = getHighBidder();
    //    if (highBidder != null && highBidder.contains(T.s("ebayServer.keptPrivate"))) {
    //      return true;
    //    }
  }

  private boolean parseReserveNotMet() {
    return mDocument.hasSequence("Reserve.*", ".*", "not met");
  }

  private String parseIdentifier() {
    int resultIndex = 0;
    JHTML.SequenceResult result = mDocument.findSequence("\\d+", ".*[Ii]tem.[nN]umber:.*");
    if(result == null || result.isEmpty()) {
      result = mDocument.findSequence("\\s*(e[Bb]ay.)?[Ii]tem.[Nn]umber:\\s*", "\\d+");
      if (result == null || result.isEmpty()) {
        return null;
      } else {
        resultIndex = 1;
      }
    }
    return result.get(resultIndex);
  }

  //  TODO(cyberfox) - This needs to reach out to the eBay bid page and get the list of bidders. :-/
  private String parseHighBidder() {
    return "not implemented";
  }

}
