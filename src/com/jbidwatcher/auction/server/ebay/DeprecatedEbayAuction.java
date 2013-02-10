package com.jbidwatcher.auction.server.ebay;

import com.jbidwatcher.auction.AuctionEntry;
import com.jbidwatcher.util.*;
import com.jbidwatcher.util.html.JHTML;

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
    String shipString = doc.getNextContentAfterRegex(T.s("ebayServer.shipping"));
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
      }
    }

    if (sellerName == null) {
      sellerName = doc.getNextContentAfterRegex(T.s("ebayServer.seller"));
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
}
