package com.jbidwatcher.auction.server.ebay;

import com.jbidwatcher.auction.AuctionEntry;
import com.jbidwatcher.util.*;
import com.jbidwatcher.util.html.JHTML;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;

import java.util.Date;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Home of deprecated (e.g. unnecessarily complex, dated, or unused) eBay parsing code that I can't replace yet for some reason.
 *
 * User: mrs
 * Date: 12/1/12
 * Time: 7:39 PM
 */
public class DeprecatedEbayAuction {
  private String mBidCountScript = null;
  private String mStartComment = null;
  private static Currency zeroDollars = new Currency("$0.00");
  protected TT T;

  public DeprecatedEbayAuction(TT t) {
    T = t;
  }

  private static String getResult(JHTML doc, String regex, int match) {
    String rval = doc.grep(regex);
    if (rval != null) {
      if (match == 0) return rval;
      Pattern searcher = Pattern.compile(regex);
      Matcher matcher = searcher.matcher(rval);
      if (matcher.matches()) return matcher.group(match);
    }

    return null;
  }

  /**
   * Check for Paypal support.
   *
   * @param doc The document to search for Paypal references in.
   * @return true if it appears to support Paypal, false if we can't find references where we'd expect them.
   */
  protected boolean parsePaypal(JHTML doc) {
    String pbp = getResult(doc, T.s("ebayServer.paypalMatcherRegex"), 0);
    boolean usePaypal = (pbp != null);

    if (!usePaypal) {
      String preferred = doc.getNextContentAfterRegex("PayPal.?");
      if (preferred != null) {
        if (preferred.contains("preferred")) usePaypal = true;
        if (preferred.contains("accepted")) usePaypal = true;
      }
      String methods = doc.getNextContentAfterRegex("Payment methods:?");
      //  If it's not the first payment method...
      //  It might be the second.
      int i = 0;
      while (i < 3 && !usePaypal) {
        if (methods != null && methods.equalsIgnoreCase("paypal")) usePaypal = true;
        else methods = doc.getNextContent();
        i++;
      }
    }

    String payments = doc.getNextContentAfterContent("Payments:");
    if (payments != null && payments.matches("(?si).*paypal.*")) usePaypal = true;

    return usePaypal;
  }

  /**
   * Extract the seller's feedback information from the item details page, if it's available.
   *
   * @param doc The item details page.
   * @return A hash containing some set of [seller, feedback, percentage]
   */
  protected Record parseFeedback(JHTML doc) {
    Record feedback = new Record();
    String score = doc.getContentBeforeContent(T.s("ebayServer.feedback"));
    String newPercent = null;

    if (score != null && StringTools.isNumberOnly(score)) {
      feedback.put("feedback", score);
    } else {
      score = doc.getNextContentAfterRegex(T.s("ebayServer.sellerInfoPrequel"));
      if (score != null && score.equals("Seller:")) score = doc.getNextContent();
      if (score != null) score = doc.getNextContent();
      if (score == null || !StringTools.isNumberOnly(score)) {
        score = doc.getNextContentAfterRegex("(?i)Feedback score of");
      }
      if (score != null && StringTools.isNumberOnly(score)) {
        feedback.put("feedback", score);
        score = doc.getNextContent(); //  Next after the feedback amount is the close parenthesis.
        if (score != null) score = doc.getNextContent();
        if (score != null && score.matches("[0-9]+(\\.[0-9])?%")) {
          newPercent = score;
        }
      }

      // If nothing else, it's possible the item is ended, and the seller info is less detailed.
      if (score == null) {
        JHTML.SequenceResult result = doc.findSequence("Seller:", ".*", "\\d+");
        if(result != null) {
          score = result.get(2);
          feedback.put("feedback", score);
        }
      }
    }

    if (newPercent == null) {
      Matcher percentMatched = doc.realGrep("(?i)([0-9]+(\\.[0-9])?%)(.|&#160;)positive.feedback");
      if (percentMatched != null) {
        newPercent = percentMatched.group(1);
      }
    }
    if (newPercent == null) newPercent = doc.getContentBeforeContent("&#160;Positive feedback");
    if (newPercent != null && newPercent.matches("[0-9]+(\\.[0-9])?%")) {
      feedback.put("percentage", newPercent);
    } else {
      String percentage = doc.getNextContentAfterContent(T.s("ebayServer.feedback"));
      if (percentage != null) {
        feedback.put("percentage", percentage);
      } else {
        feedback.put("percentage", "100");
      }
    }

    return feedback;
  }

  private Pattern amountPat = Pattern.compile("([0-9]+\\.[0-9]+|(?i)free)");

  public Record parseShippingInsurance(JHTML doc) {
    Record shipping = new Record();
    String shipString = doc.getNextContentAfterRegexIgnoring(T.s("ebayServer.shipping"), "(?i)approximately");
    //  Sometimes the next content might not be the shipping amount, it might be the next-next.
    Matcher amount = null;
    boolean amountFound = false;
    if (shipString != null) {
      amount = amountPat.matcher(shipString);
      amountFound = amount.find();
      if (!amountFound) {
        shipString = doc.getNextContent();
        amount = amountPat.matcher(shipString);
        if (shipString != null) amountFound = amount.find();
      }
    }
    //  This will result in either 'null' or the amount.
    if (shipString != null && amountFound) shipString = amount.group();

    //  Step back two contents, to check if it's 'Payment
    //  Instructions', in which case, the shipping and handling
    //  came from their instructions box, not the
    //  standard-formatted data.
    String shipStringCheck = doc.getPrevContent(2);

    String insureString = doc.getNextContentAfterRegex(T.s("ebayServer.shippingInsurance"));
    String insuranceOptionalCheck = doc.getNextContent();

    //  Default to thinking it's optional if the word 'required' isn't found.
    //  You don't want to make people think it's required if it's not.
    shipping.put("optional_insurance",
        Boolean.toString(insuranceOptionalCheck == null ||
                         (!insuranceOptionalCheck.toLowerCase().contains(T.s("ebayServer.requiredInsurance")))));

    insureString = sanitizeOptionalPrices(insureString);

    if (shipStringCheck != null && !shipStringCheck.equals(T.s("ebayServer.paymentInstructions"))) {
      shipString = sanitizeOptionalPrices(shipString);
    } else {
      shipString = null;
    }

    //  Don't override non-null shipping with the null object.  Don't
    //  bother setting shipping to the null object if it's already the
    //  null object...
    if (shipString != null) {
      if (shipString.equalsIgnoreCase("free")) {
        shipping.put("shipping", "0.0");
      } else {
        try {
          shipping.put("shipping", shipString);
        } catch (NumberFormatException ignore) {
        }
      }
    }

    //  Don't override non-null insurance with the null object.  Don't
    //  bother setting insurance to the null object if it's already the
    //  null object...
    try {
      shipping.put("insurance", Currency.getCurrency(insureString).toString());
    } catch (NumberFormatException ignore) {
    }

    return shipping;
  }

  private static String sanitizeOptionalPrices(String insureString) {
    if (insureString != null) {
      if (insureString.equals("-") || insureString.equals("--")) {
        insureString = null;
      } else {
        insureString = insureString.trim();
      }
    }
    return insureString;
  }

  public String parseSeller(JHTML doc) {
    String sellerName = null;
//    String feedbackCount = null;

    List<String> sellerInfo = doc.findSequence("(?i)top.rated.seller", ".*", ".*", "\\d+");
    if (sellerInfo != null) {
      sellerName = sellerInfo.get(2);
//      feedbackCount = sellerInfo.get(3);
    } else {
      sellerInfo = doc.findSequence(T.s("ebayServer.sellerInfoPrequel"), T.s("ebayServer.seller"), ".*");

      if (sellerInfo != null) {
        sellerName = sellerInfo.get(2);
      } else {
        sellerInfo = doc.findSequence(T.s("ebayServer.sellerInfoPrequel"), ".*", "\\d+");
        if(sellerInfo != null) {
          sellerName = sellerInfo.get(1);
        }
      }
    }

    if (sellerName == null) {
      sellerName = doc.getNextContentAfterRegex(T.s("ebayServer.seller"));
    }

    if (sellerName == null) {
      sellerInfo = doc.findSequence("Seller:", ".*", "\\d+");
      if(sellerInfo != null) {
        sellerName = sellerInfo.get(1);
      }
    }

    if (sellerName == null) {
      sellerName = doc.getNextContentAfterRegex(T.s("ebayServer.sellerInfoPrequel"));
    }

    if (sellerName == null && doc.grep(T.s("ebayServer.sellerAwayRegex")) == null) {
      sellerName = "";
    }

    return sellerName;
//    if (feedbackCount != null) {
//      return "(" + feedbackCount + ")";
//    }
  }

  public Record parsePrices(JHTML doc) {
    Record record = new Record();
    String foundBid;

    record.put("minimum", "false");

    //  The set of tags that indicate the current/starting/lowest/winning
    //  bid are 'Current bid', 'Starting bid', 'Lowest bid',
    //  'Winning bid' so far.
    List<String> curBidSequence = doc.findSequence(T.s("ebayServer.currentBid"), Currency.NAME_REGEX, Currency.VALUE_REGEX);
    if (curBidSequence != null) {
      String currency = curBidSequence.get(1);
      String value = curBidSequence.get(2);

      foundBid = currency.trim() + " " + value.trim();
    } else {
      foundBid = doc.getNextContentAfterRegex(T.s("ebayServer.currentBid"));
    }

    if (foundBid != null && foundBid.length() != 0) {
      record.put("current", foundBid);
      record.put("current_us", getUSCurrency(Currency.getCurrency(foundBid), doc).toString());
    }

    String firstBid = doc.getNextContentAfterContent(T.s("ebayServer.firstBid"));
    if(firstBid != null) {
      record.put("current", firstBid);
      record.put("minimum", "true");
    }

    String maxBid = doc.getNextContentAfterContent(T.s("ebayServer.yourMaxBid"));
    if(maxBid != null && maxBid.length() != 0) record.put("max", maxBid);

    return record;
  }

  private static Currency getUSCurrency(Currency val, JHTML htmlDoc) {
    Currency newCur = zeroDollars;

    if (val != null && !val.isNull()) {
      if (val.getCurrencyType() == Currency.US_DOLLAR) {
        newCur = val;
      } else {
        newCur = walkForUSCurrency(htmlDoc);
      }
    }

    return newCur;
  }

  /**
   * If the next text doesn't contain a USD amount, it's separated somehow.
   * Skim forward until we either find something, or give up.  (6 steps for now.)
   *
   * @param html - The document to search.
   * @return - Either zeroDollars or the approximate USD equivalent of the value of the item.
   */
  private static Currency walkForUSCurrency(JHTML html) {
    Currency newCur = zeroDollars;
    int count = 0;

    String usdPattern = Externalized.getString("ebayServer.USD");
    do {
      String approxAmount = html.getNextContent();
      approxAmount = StringTools.stripHigh(approxAmount, "");
      approxAmount = approxAmount.replaceAll("\\s+", " ");
      int matchAtIndex = approxAmount.indexOf(usdPattern);
      if (matchAtIndex != -1) {
        approxAmount = approxAmount.substring(matchAtIndex); //$NON-NLS-1$
        newCur = Currency.getCurrency(approxAmount);
        if (newCur.getCurrencyType() != Currency.US_DOLLAR) newCur = zeroDollars;
      }
    } while (count++ < 6 && newCur == zeroDollars);

    return newCur;
  }

  public void setPage(StringBuffer sb) {
    String skimOver = sb.toString();

    Matcher startCommentSearch = Pattern.compile(Externalized.getString("ebayServer.startedRegex")).matcher(skimOver);
    if (startCommentSearch.find())
      mStartComment = startCommentSearch.group(1);
    else
      mStartComment = "";

    Matcher bidCountSearch = Pattern.compile(T.s("ebayServer.bidCountRegex")).matcher(skimOver);
    if (bidCountSearch.find())
      mBidCountScript = bidCountSearch.group(1);
    else
      mBidCountScript = "";
  }

  private Pair<String,Boolean> getRawBidCount(JHTML doc) {
    Boolean fixed = null;
    final String[][] SEQUENCES = {
        {T.s("ebayServer.currentBid"), ".*", ".*", ".*", "[0-9]+", "bids?"},
        {T.s("ebayServer.currentBid"), ".*", ".*", "[0-9]+", "bids?"},
        {T.s("ebayServer.currentBid"), ".*", "[0-9]+", "bids?"},
        {T.s("ebayServer.currentBid"), ".*", "[0-9]+ bids?"},
        {"[0-9]+", "bids?"}
    };
    final int[] SEQUENCE_GROUPS = {4, 3, 2, 2, 0};

    List<String> bidSequence;
    String rawBidCount = null;

    for (int i = 0; i < SEQUENCES.length; i++) {
      bidSequence = doc.findSequence(SEQUENCES[i]);
      if (bidSequence != null) {
        int index = SEQUENCE_GROUPS[i];
        rawBidCount = bidSequence.get(index);

        if (i == 3) rawBidCount = rawBidCount.substring(0, rawBidCount.indexOf(' '));
      }
    }

    if (rawBidCount == null) rawBidCount = doc.getNextContentAfterRegex(T.s("ebayServer.bidCount"));

    if (rawBidCount == null) {
      rawBidCount = doc.getContentBeforeContent("See history");
      if (rawBidCount != null && rawBidCount.matches("^(Purchased|Bid).*")) {
        if (rawBidCount.matches("^Purchased.*")) fixed = true;
        rawBidCount = doc.getPrevContent();
      }
      if (rawBidCount != null && !StringTools.isNumberOnly(rawBidCount)) rawBidCount = null;
    }

    return new Pair<String, Boolean>(rawBidCount, fixed);
  }

  private Pattern digits = Pattern.compile("([0-9]+)");

  int getDigits(String digitsStarting) {
    Matcher m = digits.matcher(digitsStarting);
    if (m.find()) {
      String rawCount = m.group();
      if (rawCount != null) {
        return Integer.parseInt(rawCount);
      }
    }
    return -1;
  }

  public Record getBidCount(JHTML doc, int quantity) {
    Record result = new Record();
    Pair<String, Boolean> rawBidCountResult = getRawBidCount(doc);
    String rawBidCount = rawBidCountResult.getFirst();
    if(rawBidCountResult.getLast() != null) {
      result.put("fixed", rawBidCountResult.getLast().toString());
    }
    int bidCount = 0;
    if (rawBidCount != null) {
      if (rawBidCount.equals(T.s("ebayServer.purchasesBidCount")) ||
          rawBidCount.matches(T.s("ebayServer.offerRecognition"))) {
        result.put("fixed", "true");
        bidCount = -1;
      } else {
        if (rawBidCount.matches(T.s("ebayServer.bidderListCount"))) {
          bidCount = Integer.parseInt(mBidCountScript);
          mBidCountScript = null;
        } else {
          bidCount = getDigits(rawBidCount);
        }
      }
    }

    //  If we can't match any digits in the bidcount, or there is no match for the eBayBidCount regex, then
    //  this is a store or FP item.  Still true under BIBO?
    if (rawBidCount == null || bidCount == -1) {
      result.put("high_bidder", T.s("ebayServer.fixedPrice"));
      result.put("fixed", "true");

      if (doc.lookup(T.s("ebayServer.hasBeenPurchased"), false) != null ||
          doc.lookup(T.s("ebayServer.endedEarly"), false) != null) {
        bidCount = quantity;
        Date now = new Date();
        result.put("ended_at", "now");
        result.put("started_at", "now"); // TODO - Make sure this only overrides if no other start date is set.
      } else {
        bidCount = 0;
      }
    }

    result.put("bid_count", Integer.toString(bidCount));
    return result;
  }

  private Date extractEndDate(Document doc) {
    String endTime = "";
    Elements parents = doc.select("span.endedDate").parents();

    if (parents.isEmpty()) {
      parents = doc.select(":matchesOwn((?i)ended:)").parents();
      if (parents.isEmpty()) {
        endTime = doc.select(":matchesOwn((?i)time.left:)").parents().first().select(":matchesOwn((^\\()|(\\)$))").text();
      }
    }

    if (endTime.length() == 0) {
      if (parents.isEmpty()) {
        return null;
      }
      endTime = parents.first().text();
    }
    endTime = endTime.replaceAll("([\\(\\s\\)])+", " ").trim();

    ZoneDate endDate = StringTools.figureDate(endTime, T.s("ebayServer.itemDateFormat"), true, true);

    if (endDate != null && !endDate.isNull()) {
      return endDate.getDate();
    } else {
      return null;
    }
  }

  private boolean checkSeller(JHTML doc, AuctionEntry ae) {
    String sellerName = null;
    String feedbackCount = null;

    List<String> sellerInfo = doc.findSequence("(?i)top.rated.seller", ".*", ".*", "\\d+");
    if (sellerInfo != null) {
      sellerName = sellerInfo.get(2);
      feedbackCount = sellerInfo.get(3);
    } else {
      sellerInfo = doc.findSequence(T.s("ebayServer.sellerInfoPrequel"), T.s("ebayServer.seller"), ".*");

      if (sellerInfo != null) {
        sellerName = sellerInfo.get(2);
      }
    }

    if (sellerName == null) {
      sellerName = doc.getNextContentAfterRegex(T.s("ebayServer.seller"));
    }

    if (sellerName == null) {
      sellerName = doc.getNextContentAfterRegex(T.s("ebayServer.sellerInfoPrequel"));
    }

    if (sellerName == null) {
      if (doc.grep(T.s("ebayServer.sellerAwayRegex")) != null) {
        if (ae != null) {
          ae.setLastStatus("Seller away - item unavailable.");
        }
        return true;
      } else {
        if (ae == null)
          sellerName = "(unknown)";
        else
          sellerName = ae.getSellerName();
      }
    }
    Record r = new Record();
    r.put("seller_name", sellerName);
    if (feedbackCount != null) {
      r.put("feedback_count", Integer.toString(Integer.parseInt(feedbackCount)));
    }

    return false;
  }
}
