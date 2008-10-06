package com.jbidwatcher.auction.server.ebay;

import com.jbidwatcher.auction.AuctionEntry;
import com.jbidwatcher.auction.SpecificAuction;
import com.jbidwatcher.auction.ThumbnailLoader;
import com.jbidwatcher.util.config.*;
import com.jbidwatcher.util.Externalized;
import com.jbidwatcher.util.queue.MQFactory;
import com.jbidwatcher.util.*;
import com.jbidwatcher.util.html.JHTML;
import com.jbidwatcher.util.html.htmlToken;
import com.jbidwatcher.util.Constants;

import java.net.URL;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by IntelliJ IDEA.
* User: Morgan
* Date: Feb 25, 2007
* Time: 4:08:52 PM
* To change this template use File | Settings | File Templates.
*/
class ebayAuction extends SpecificAuction {
  private static Currency zeroDollars = new Currency("$0.00");
  String _bidCountScript = null;
  String _startComment = null;
  private static final int TITLE_LENGTH = 60;
  private static final int HIGH_BIT_SET = 0x80;
  private final Pattern p = Pattern.compile(Externalized.getString("ebayServer.thumbSearch"), Pattern.DOTALL | Pattern.MULTILINE | Pattern.CASE_INSENSITIVE);
  private final Pattern p2 = Pattern.compile(Externalized.getString("ebayServer.thumbSearch2"), Pattern.DOTALL | Pattern.MULTILINE | Pattern.CASE_INSENSITIVE);
  private String potentialThumbnail = null;

  private static final String dateMatch = "(?i)(Ends|end.time).+?([A-Za-z]+(.[0-9]+)+.[A-Z]+)";
  private static Pattern datePat = Pattern.compile(dateMatch);

  private void checkThumb(StringBuffer sb) {
    Matcher imgMatch = p.matcher(sb);
    if(imgMatch.find()) {
      potentialThumbnail = imgMatch.group(1);
    } else {
      imgMatch = p2.matcher(sb);
      if(imgMatch.find()) {
        potentialThumbnail = imgMatch.group(1);
      }
    }
  }

  /**
   * @brief Delete the 'description' portion of a page, all scripts, and comments.
   *
   * @param sb - The StringBuffer to clean of description and scripts.
   */
  public void cleanup(StringBuffer sb) {
    checkThumb(sb);

    //  We ignore the result of this, because it's just useful if it
    //  works, it's not critical.
    StringTools.deleteFirstToLast(sb, Externalized.getString("ebayServer.description"), Externalized.getString("ebayServer.descriptionMotors"), Externalized.getString("ebayServer.descriptionEnd"), Externalized.getString("ebayServer.descriptionClosedEnd"));
    StringTools.deleteFirstToLast(sb, Externalized.getString("ebayServer.descStart"), Externalized.getString("ebayServer.descriptionMotors"), Externalized.getString("ebayServer.descEnd"), Externalized.getString("ebayServer.descriptionClosedEnd"));

    String skimOver = sb.toString();

    Matcher startCommentSearch = Pattern.compile(Externalized.getString("ebayServer.startedRegex")).matcher(skimOver);
    if(startCommentSearch.find())
      _startComment = startCommentSearch.group(1);
    else
      _startComment = "";

    Matcher bidCountSearch = Pattern.compile(Externalized.getString("ebayServer.bidCountRegex")).matcher(skimOver);
    if(bidCountSearch.find())
      _bidCountScript = bidCountSearch.group(1);
    else
      _bidCountScript = "";

    //  Use eBay's cleanup method to finish up with.
    new ebayCleaner().cleanup(sb);
  }

  boolean checkValidTitle(String auctionTitle) {
    if(auctionTitle.startsWith(Externalized.getString("ebayServer.liveAuctionsTitle"))) {
      ErrorManagement.logMessage("JBidwatcher cannot handle live auctions!");
      return false;
    }

    if(auctionTitle.startsWith(Externalized.getString("ebayServer.greatCollectionsTitle"))) {
      ErrorManagement.logMessage("JBidwatcher cannot handle Great Collections items yet.");
      return false;
    }

    String[] eBayTitles = new String[]{
        Externalized.getString("ebayServer.titleEbay"),
        Externalized.getString("ebayServer.titleEbay2"),
        Externalized.getString("ebayServer.titleEbay3"),
        Externalized.getString("ebayServer.titleMotors"),
        Externalized.getString("ebayServer.titleMotors2"),
        Externalized.getString("ebayServer.titleDisney"),
        Externalized.getString("ebayServer.titleCollections")};

    for (String eBayTitle : eBayTitles) {
      if(auctionTitle.matches(eBayTitle)) return true;
    }

    return false;
  }

  private Currency getUSCurrency(Currency val, JHTML _htmlDoc) {
    Currency newCur = zeroDollars;

    if(val != null && !val.isNull()) {
      if (val.getCurrencyType() == Currency.US_DOLLAR) {
        newCur = val;
      } else {
        newCur = walkForUSCurrency(_htmlDoc);
      }
    }

    return newCur;
  }

  /**
   * If the next text doesn't contain a USD amount, it's separated somehow.
   * Skim forward until we either find something, or give up.  (6 steps for now.)
   *
   * @param html   - The document to search.
   * @return - Either zeroDollars or the approximate USD equivalent of the value of the item.
   */
  private Currency walkForUSCurrency(JHTML html) {
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

  private Pattern digits = Pattern.compile("([0-9]+)");

  int getDigits(String digitsStarting) {
    Matcher m = digits.matcher(digitsStarting);
    if(m.find()) {
      String rawCount = m.group();
      if (rawCount != null) {
        return Integer.parseInt(rawCount);
      }
    }
    return -1;
  }

  /**
   * @brief Check the title for unavailable or 'removed item' messages.
   *
   * @param in_title - The title from the web page, to check.
   */
  private void handle_bad_title(String in_title) {
    if(in_title.indexOf(Externalized.getString("ebayServer.unavailable")) != -1) {
      MQFactory.getConcrete("Swing").enqueue("LINK DOWN eBay (or the link to eBay) appears to be down.");
      MQFactory.getConcrete("Swing").enqueue("eBay (or the link to eBay) appears to be down for the moment.");
    } else if(in_title.indexOf(Externalized.getString("ebayServer.invalidItem")) != -1) {
      ErrorManagement.logDebug("Found bad/deleted item.");
    } else {
      ErrorManagement.logDebug("Failed to load auction title from header: \"" + in_title + '\"');
    }
  }

  /**
   * @brief Build the title from the data on the web page, pulling HTML tokens out as it goes.
   *
   * @param doc - The document to pull the title from.
   *
   * @return - A string consisting of just the title part of the page, with tags stripped.
   */
  private String buildTitle(JHTML doc) {
    //  This is an HTML title...  Suck.
    doc.reset();
    doc.getNextTag();
    StringBuffer outTitle = new StringBuffer(TITLE_LENGTH);
    //  Iterate over the tokens, adding all content to the
    //  title tag until the end of the title.
    htmlToken jh;
    do {
      jh = doc.nextToken();
      if(jh.getTokenType() == htmlToken.HTML_CONTENT) {
        outTitle.append(jh.getToken());
      }
    } while(!(jh.getTokenType() == htmlToken.HTML_ENDTAG &&
              jh.getToken().equalsIgnoreCase("/title")));

    return outTitle.toString();
  }

  private Pattern amountPat = Pattern.compile("(([0-9]+\\.[0-9]+|(?i)free))");

  private void load_shipping_insurance(Currency sampleAmount) {
    String shipString = mDocument.getNextContentAfterRegex(Externalized.getString("ebayServer.shipping"));
    //  Sometimes the next content might not be the shipping amount, it might be the next-next.
    Matcher amount = null;
    boolean amountFound = false;
    if(shipString != null) {
      amount = amountPat.matcher(shipString);
      amountFound = amount.find();
      if (!amountFound) {
        shipString = mDocument.getNextContent();
        amount = amountPat.matcher(shipString);
        if (shipString != null) amountFound = amount.find();
      }
    }
    //  This will result in either 'null' or the amount.
    if(shipString != null && amountFound) shipString = amount.group();

    //  Step back two contents, to check if it's 'Payment
    //  Instructions', in which case, the shipping and handling
    //  came from their instructions box, not the
    //  standard-formatted data.
    String shipStringCheck = mDocument.getPrevContent(2);

    String insureString = mDocument.getNextContentAfterRegex(Externalized.getString("ebayServer.shippingInsurance"));
    String insuranceOptionalCheck = mDocument.getNextContent();

    //  Default to thinking it's optional if the word 'required' isn't found.
    //  You don't want to make people think it's required if it's not.
    setInsuranceOptional(insuranceOptionalCheck == null || (insuranceOptionalCheck.toLowerCase().indexOf(Externalized.getString("ebayServer.requiredInsurance")) == -1));

    if(insureString != null) {
      if(insureString.equals("-") || insureString.equals("--")) {
        insureString = null;
      } else {
        insureString = insureString.trim();
      }
    }

    if(shipStringCheck != null && !shipStringCheck.equals(Externalized.getString("ebayServer.paymentInstructions"))) {
      if(shipString != null) {
        if(shipString.equals("-")) {
          shipString = null;
        } else {
          shipString = shipString.trim();
        }
      }
    } else {
      shipString = null;
    }

    if(shipString != null) {
      if(shipString.equalsIgnoreCase("free")) {
        setShipping(Currency.getCurrency(sampleAmount.fullCurrencyName(), "0.0"));
      } else {
        try {
          setShipping(Currency.getCurrency(sampleAmount.fullCurrencyName(), shipString));
        } catch(NumberFormatException nfe) {
          setShipping(Currency.NoValue());
        }
      }
    } else {
      setShipping(Currency.NoValue());
    }
    try {
      setInsurance(Currency.getCurrency(insureString));
    } catch(NumberFormatException nfe) {
      setInsurance(Currency.NoValue());
    }
  }

  private void load_buy_now() {
    setBuyNow(Currency.NoValue());
    setBuyNowUS(zeroDollars);

    String altBuyNowString1 = mDocument.getNextContentAfterRegexIgnoring(Externalized.getString("ebayServer.price"), "([Ii]tem.[Nn]umber|^\\s*[0-9]+\\s*$)");
    if(altBuyNowString1 != null) {
      altBuyNowString1 = altBuyNowString1.trim();
    }
    if(altBuyNowString1 != null && altBuyNowString1.length() != 0) {
      setBuyNow(Currency.getCurrency(altBuyNowString1));
      setBuyNowUS(getUSCurrency(getBuyNow(), mDocument));
    }
  }

  private String getEndDate(String inTitle) {
    String result = null;

    Matcher when = datePat.matcher(inTitle);
    if(when.find()) result = when.group(2);

    return result;
  }

  /**
   * A utility function to check the provided preferred object against an arbitrary 'bad' value,
   * and return the preferred object if it's not bad, and an alternative object if it the preferred
   * object is bad.
   *
   * @param preferred - The preferred object (to be compared against the 'bad' value)
   * @param alternate - The alternative object, if the first object is bad.
   * @param bad - The bad object to validate the preferred object against.
   *
   * @return - preferred if it's not bad, alternate if the preferred object is bad.
   * @noinspection ObjectEquality
   **/
  private Object ensureSafeValue(Object preferred, Object alternate, Currency bad) {
    return (preferred == bad)?alternate:preferred;
  }

  private String getResult(JHTML doc, String regex, int match) {
    String rval = doc.grep(regex);
    if(rval != null) {
      if(match == 0) return rval;
      Pattern searcher = Pattern.compile(regex);
      Matcher matcher = searcher.matcher(rval);
      if(matcher.matches()) return matcher.group(match);
    }

    return null;
  }

  private void loadOptionalInformation(JHTML doc) {
    try {
      loadFeedback(doc);

      String location = doc.getNextContentAfterRegex(Externalized.getString("ebayServer.itemLocationRegex"));
      if(location != null) {
        setItemLocation(StringTools.decode(location, doc.getCharset()));
      }

      loadPaypal(doc);
    } catch(Throwable t) {
      //  I don't actually CARE about any of this data, or any errors that occur on loading it, so don't mess things up on errors.
      ErrorManagement.logDebug(t.getMessage());
    }
  }

  private void loadPaypal(JHTML doc) {
    String pbp = getResult(doc, Externalized.getString("ebayServer.paypalMatcherRegex"), 0);
    if(pbp != null) {
      setPaypal(true);
    } else {
      String preferred = doc.getNextContentAfterRegex("PayPal.?");
      if(preferred != null) {
        if(preferred.indexOf("preferred") != -1) setPaypal(true);
        if(preferred.indexOf("accepted") != -1) setPaypal(true);
      }
      String methods = doc.getNextContentAfterRegex("Payment methods:?");
      //  If it's not the first payment method...
      //  It might be the second.
      int i=0;
      while (i<3 && !hasPaypal()) {
        if (methods != null && methods.equalsIgnoreCase("paypal")) setPaypal(true);
        else methods = doc.getNextContent();
        i++;
      }
    }
  }

  private void loadFeedback(JHTML doc) {
    String score = doc.getContentBeforeContent("Feedback:");
    if(score != null && StringTools.isNumberOnly(score)) {
      mSeller.setFeedback(Integer.parseInt(score));
    }

    String percentage = doc.getNextContentAfterContent("Feedback:");
    if(percentage != null) mSeller.setPositivePercentage(percentage);
  }

  /**
   * @brief - Not brief at all; wow...this is way too long.
   *
   * @param ae - The auction entry to update or null if it's a new auction.
   *
   * @return - false if the parse failed, true if it succeeded.  This needs
   * to be turned into a set of enums.
   */
  public ParseErrors parseAuction(AuctionEntry ae) {
    //  Verify the title (in case it's an invalid page, the site is
    //  down for maintenance, etc).
    String prelimTitle;
    try {
      prelimTitle = checkTitle();
    } catch (ParseException e) {
      finish();
      return e.getError();
    }

    if(prelimTitle == null) {
      finish();
      return ParseErrors.BAD_TITLE;
    }

    Integer quant = getNumberFromLabel(mDocument, Externalized.getString("ebayServer.quantity"), Externalized.getString("ebayServer.postTitleIgnore"));

    //  Get the integer values (Quantity, Bidcount)
    setQuantity(quant == null ? 1 : quant);

    setFixedPrice(false);
    setNumBids(getBidCount(mDocument, getQuantity()));
    if(!isFixedPrice() && quant != null) setDutch(true);

    try {
      load_buy_now();
    } catch(Exception e) {
      ErrorManagement.handleException("Buy It Now Loading error", e);
    }

    if (isFixedPrice()) {
      establishCurrentBidFixedPrice(ae);
    } else {
      Currency maxBid = establishCurrentBid(ae);
      setOutbid(mDocument.grep(Externalized.getString("ebayServer.outbid")) != null);
      setMaxBidFromServer(ae, maxBid);
    }

    if(getMinBid() == null && getBuyNow() != null && !getBuyNow().isNull()) {
      setMinBid(getBuyNow());
    }
    try {
      load_shipping_insurance(getCurBid());
    } catch(Exception e) {
      ErrorManagement.handleException("Shipping / Insurance Loading Failed", e);
    }

    if (checkSeller(ae)) return ParseErrors.SELLER_AWAY;

    checkDates(prelimTitle, ae);
    checkHighBidder();
    checkReserve();
    checkPrivate();

    loadOptionalInformation(mDocument);
    checkThumbnail();

    finish();
    this.saveDB();
    return ParseErrors.SUCCESS;
  }

  private static class ParseException extends Exception {
    private ParseErrors mError;
    public ParseException(ParseErrors error) {
      mError = error;
    }

    public ParseErrors getError() {
      return mError;
    }
  }

  /**
   * Sets _title, and possibly _end.
   *
   * @return - The preliminary extraction of the title, in its entirety, for later parsing.  null if a failure occurred.
   * @throws com.jbidwatcher.auction.server.ebay.ebayAuction.ParseException - An exception that describes what's wrong with the title.
   */
  private String checkTitle() throws ParseException {
    String prelimTitle = mDocument.getTitle();
    if( prelimTitle == null) {
      prelimTitle = Externalized.getString("ebayServer.unavailable");
    }
    if(prelimTitle.equals(Externalized.getString("ebayServer.adultPageTitle")) || prelimTitle.indexOf("Terms of Use") != -1) {
      throw new ParseException(ParseErrors.NOT_ADULT);
    }

    if(prelimTitle.equals("Invalid Item")) {
      throw new ParseException(ParseErrors.DELETED);
    }

    if(prelimTitle.equals("Security Measure")) {
      throw new ParseException(ParseErrors.CAPTCHA);
    }

    //  Is this a valid eBay item page?
    if(prelimTitle != null && !checkValidTitle(prelimTitle)) {
      handle_bad_title(prelimTitle);
      throw new ParseException(ParseErrors.BAD_TITLE);
    }

    if(prelimTitle != null) {
      //  If we got a valid title, mark the link as up, because it worked...
      MQFactory.getConcrete("Swing").enqueue("LINK UP");

      boolean ebayMotors = false;
      if(prelimTitle.matches(Externalized.getString("ebayServer.ebayMotorsTitle"))) ebayMotors = true;
      //  This is mostly a hope, not a guarantee, as eBay might start
      //  cross-advertising eBay Motors in their normal pages, or
      //  something.
      if(doesLabelExist(Externalized.getString("ebayServer.ebayMotorsTitle"))) ebayMotors = true;

      setEnd(null);
      setTitle(null);

      //  This sucks.  They changed to: eBay: {title} (item # end time {endtime})
      if(prelimTitle.matches(Externalized.getString("ebayServer.titleEbay2")) ||
         prelimTitle.matches(Externalized.getString("ebayServer.titleMotors2"))) {
        //  Handle the new titles.
        Pattern newTitlePat = Pattern.compile(Externalized.getString("ebayServer.titleMatch"));
        Matcher newTitleMatch = newTitlePat.matcher(prelimTitle);
        if (newTitleMatch.find()) {
          setTitle(StringTools.decode(newTitleMatch.group(1), mDocument.getCharset()));
          String endDate = newTitleMatch.group(4);
          if(getEnd() != null) setEnd(StringTools.figureDate(endDate, Externalized.getString("ebayServer.dateFormat")).getDate());
        }
      }

      if(getTitle() == null) {
        boolean htmlTitle = false;
        //  The first element after the title is always the description.  Unfortunately, it's in HTML-encoded format,
        //  so there are &lt;'s, and such.  While I could translate that, that's something I can wait on.  --  HACKHACK
        //      _title = (String)_contentFields.get(1);
        //  For now, just load from the title, everything after ') - '.
        int titleIndex = prelimTitle.indexOf(") - ");
        if(titleIndex == -1) {
          titleIndex = prelimTitle.indexOf(") -");
          //  This is an HTML title...  Suck.
          htmlTitle = true;
        }

        //  Always convert, at this point, from iso-8859-1 (iso latin-1) to UTF-8.
        if(htmlTitle) {
          setTitle(StringTools.decode(buildTitle(mDocument), mDocument.getCharset()));
        } else {
          setTitle(StringTools.decode(prelimTitle.substring(titleIndex+4).trim(), mDocument.getCharset()));
        }
      }

      if(getTitle().length() == 0) setTitle("(bad title)");
      setTitle(JHTML.deAmpersand(getTitle()));

      // eBay Motors titles are really a combination of the make/model,
      // and the user's own text.  Under BIBO, the user's own text is
      // below the 'description' fold.  For now, we don't get the user
      // text.
      if(ebayMotors) {
        extractMotorsTitle();
      }
    }
    return prelimTitle;
  }

  /**
   * Sets _start and _end.
   *
   * @param prelimTitle - The preliminary title block, because sometimes it has date information in it.
   * @param ae - The old auction, in case we need to fall back because we can't figure out the ending date.
   */
  private void checkDates(String prelimTitle, AuctionEntry ae) {
    setStart(StringTools.figureDate(mDocument.getNextContentAfterRegexIgnoring(Externalized.getString("ebayServer.startTime"), Externalized.getString("ebayServer.postTitleIgnore")), Externalized.getString("ebayServer.dateFormat")).getDate());
    if (getStart() == null) {
      setStart(StringTools.figureDate(_startComment, Externalized.getString("ebayServer.dateFormat")).getDate());
    }
    setStart((Date) ensureSafeValue(getStart(), ae != null ? ae.getStartDate() : null, null));

    if (getEnd() == null) {
      String endDate = getEndDate(prelimTitle);
      setEnd(StringTools.figureDate(endDate, Externalized.getString("ebayServer.dateFormat")).getDate());
    }

    //  Handle odd case...
    if (getEnd() == null) {
      setEnd(StringTools.figureDate(mDocument.getNextContentAfterRegex(Externalized.getString("ebayServer.endsPrequel")), Externalized.getString("ebayServer.dateFormat")).getDate());
      if (getEnd() == null) {
        String postContent = mDocument.getNextContent().replaceAll("[()]", "");
        setEnd(StringTools.figureDate(postContent, Externalized.getString("ebayServer.dateFormat")).getDate());
      }
    }

    setEnd((Date) ensureSafeValue(getEnd(), ae != null ? ae.getEndDate() : null, null));
    if(getEnd() != null) {
      if (getEnd().getTime() > System.currentTimeMillis()) {
        //  Item is not ended yet.
        if (ae != null) {
          ae.setComplete(false);
          ae.setSticky(false);
        }
      }
    } else {
      if(ae != null) setEnd(ae.getEndDate());
      if(mDocument.grep(Externalized.getString("ebayServer.ended")) != null) {
        if(ae != null) ae.setComplete(true);
        setEnd(new Date());
      } else {
        if(isFixedPrice()) {
          String durationRaw = mDocument.getNextContentAfterContent("Duration:");
          if(durationRaw != null) {
            String duration = durationRaw.replaceAll("[^0-9]", "");
            long days = Long.parseLong(duration);
            if(getStart() != null && !getStart().equals(Constants.LONG_AGO)) {
              long endTime = getStart().getTime() + Constants.ONE_DAY * days;
              setEnd(new Date(endTime));
            } else {
              setEnd(Constants.FAR_FUTURE);
            }
          } else {
            ErrorManagement.logMessage("Setting auction #" + getIdentifier() + " to be a 'Far Future' listing, as it has no date info.");
            setEnd(Constants.FAR_FUTURE);
          }
        }
      }
    }

    if (getStart() == null) setStart(Constants.LONG_AGO);
    if (getEnd() == null) setEnd(Constants.FAR_FUTURE);
  }

  /**
   * Sets the user's max bid, based on what eBay thinks it is, to catch out-of-JBidwatcher bidding.
   *
   * @param ae - The auction entry, so we can set the max bid value.
   * @param maxBid - The max bid extracted from eBay.
   */
  private void setMaxBidFromServer(AuctionEntry ae, Currency maxBid) {
    // This is dangerously intimate with the AuctionEntry class,
    // and it won't work the first time, since the first time ae
    // is null.
    if(ae != null && !maxBid.isNull()) {
      try {
        if(!ae.isBidOn() || ae.getBid().less(maxBid)) ae.setBid(maxBid);
      } catch(Currency.CurrencyTypeException cte) {
        ErrorManagement.handleException("eBay says my max bid is a different type of currency than I have stored!", cte);
      }
    }
  }

  private Currency establishCurrentBid(AuctionEntry ae) {
    //  The set of tags that indicate the current/starting/lowest/winning
    //  bid are 'Current bid', 'Starting bid', 'Lowest bid',
    //  'Winning bid' so far.
    String cvtCur = mDocument.getNextContentAfterRegex(Externalized.getString("ebayServer.currentBid"));
    setCurBid(Currency.getCurrency(cvtCur));
    setUSCur(getUSCurrency(getCurBid(), mDocument));

    if(getCurBid() == null || getCurBid().isNull()) {
      if(getQuantity() > 1) {
        setCurBid(Currency.getCurrency(mDocument.getNextContentAfterContent(Externalized.getString("ebayServer.lowestBid"))));
        setUSCur(getUSCurrency(getCurBid(), mDocument));
      }
    }

    setMinBid(Currency.getCurrency(mDocument.getNextContentAfterContent(Externalized.getString("ebayServer.firstBid"))));
    Currency maxBid = Currency.getCurrency(mDocument.getNextContentAfterContent(Externalized.getString("ebayServer.yourMaxBid")));

    setMinBid((Currency)ensureSafeValue(getMinBid(), ae!=null?ae.getMinBid()  :Currency.NoValue(), Currency.NoValue()));
    setCurBid((Currency)ensureSafeValue(getCurBid(), ae!=null?ae.getCurBid()  :Currency.NoValue(), Currency.NoValue()));
    setUSCur((Currency)ensureSafeValue(getUSCur(), ae!=null?ae.getUSCurBid():zeroDollars, Currency.NoValue()));

    if(getNumBids() == 0 && (getMinBid() == null || getMinBid().isNull())) setMinBid(getCurBid());

    if(getMinBid() == null || getMinBid().isNull()) {
      String original = mDocument.grep(Externalized.getString("ebayServer.originalBid"));
      if(original != null) {
        Pattern bidPat = Pattern.compile(Externalized.getString("ebayServer.originalBid"));
        Matcher bidMatch = bidPat.matcher(original);
        if (bidMatch.find()) {
          setMinBid(Currency.getCurrency(bidMatch.group(1)));
        }
      }
    }
    return maxBid;
  }

  private void establishCurrentBidFixedPrice(AuctionEntry ae) {
    if(getBuyNow() != null && !getBuyNow().isNull()) {
      setMinBid(getBuyNow());
      setCurBid(getBuyNow());
      setUSCur(getBuyNowUS());
    } else {
      //  The set of tags that indicate the current/starting/lowest/winning
      //  bid are 'Starts at', 'Current bid', 'Starting bid', 'Lowest bid',
      //  'Winning bid' so far.  'Starts at' is mainly for live auctions!
      String cvtCur = mDocument.getNextContentAfterRegex(Externalized.getString("ebayServer.currentBid"));
      setCurBid(Currency.getCurrency(cvtCur));
      setUSCur(getUSCurrency(getCurBid(), mDocument));

      setCurBid((Currency)ensureSafeValue(getCurBid(), ae!=null?ae.getCurBid()  :Currency.NoValue(), Currency.NoValue()));
      setUSCur((Currency)ensureSafeValue(getUSCur(), ae!=null?ae.getUSCurBid():zeroDollars, Currency.NoValue()));
    }
  }

  private boolean checkSeller(AuctionEntry ae) {
    String sellerName = mDocument.getNextContentAfterRegex(Externalized.getString("ebayServer.seller"));
    if(sellerName == null) {
      sellerName = mDocument.getNextContentAfterRegex(Externalized.getString("ebayServer.sellerInfoPrequel"));
    }

    if(sellerName == null) {
      if(mDocument.grep(Externalized.getString("ebayServer.sellerAwayRegex")) != null) {
        if(ae != null) {
          ae.setLastStatus("Seller away - item unavailable.");
        }
        finish();
        return true;
      } else {
        sellerName = "(unknown)";
      }
    }
    setSellerName(sellerName);

    return false;
  }

  private void checkThumbnail() {
    try {
      if(JConfig.queryConfiguration("show.images", "true").equals("true")) {
        if(!hasNoThumbnail() && !hasThumbnail()) {
          MQFactory.getConcrete("thumbnail").enqueue(this);
        }
      }
    } catch(Exception e) {
      ErrorManagement.handleException("Error handling thumbnail loading", e);
    }
  }

  private void checkPrivate() {
    if(getHighBidder().indexOf(Externalized.getString("ebayServer.keptPrivate")) != -1) { //$NON-NLS-1$
      setPrivate(true);
      setHighBidder("(private)"); //$NON-NLS-1$
    }
  }

  private void checkReserve() {
    if(doesLabelExist(Externalized.getString("ebayServer.reserveNotMet1")) || //$NON-NLS-1$
       doesLabelExist(Externalized.getString("ebayServer.reserveNotMet2"))) { //$NON-NLS-1$
      setReserve(true);
      setReserveMet(false);
    } else {
      if(doesLabelExist(Externalized.getString("ebayServer.reserveMet1")) || //$NON-NLS-1$
         doesLabelExist(Externalized.getString("ebayServer.reserveMet2"))) { //$NON-NLS-1$
        setReserve(true);
        setReserveMet(true);
      }
    }
  }

  private void checkHighBidder() {
    String bidder = null;
    String email = null;

    if(mDocument.grep("This is a private listing.*") != null) {
      bidder = "(private)";
      setPrivate(true);
    } else {
      if (isFixedPrice()) {
        bidder = mDocument.getNextContentAfterRegex(Externalized.getString("ebayServer.buyer"));
        if (bidder != null) {
          setNumBids(1);
          bidder = bidder.trim();
          email = findHighBidderEmail(bidder);
        }
      } else {
        if (getQuantity() > 1 || isDutch()) {
          setDutch(true);
          bidder = "(dutch)";
        } else {
          if (getNumBids() != 0) {
            bidder = mDocument.getNextContentAfterRegex(Externalized.getString("ebayServer.highBidder"));
            if (bidder != null) {
              bidder = bidder.trim();
              email = findHighBidderEmail(bidder);
            } else {
              bidder = "(unknown)"; //  ...but present.
            }
          }
        }
      }
    }

    setHighBidder(bidder == null ? "" : bidder);
    setHighBidderEmail(email == null ? "(unknown)" : email);
  }

  private String findHighBidderEmail(String bidder) {
    String email = null;

    if(bidder != null) {
      email = mDocument.getNextContentAfterContent(bidder, true, false);
      if (email != null) {
        email = email.trim();
        if (email.charAt(0) == '(' && email.charAt(email.length() - 1) == ')' && email.indexOf('@') != -1) {
          email = (email.substring(1, email.length() - 1));
        }
        if (email.equals("(")) email = null;
      }
    }

    return email;
  }

  private int getBidCount(JHTML doc, int quantity) {
    String rawBidCount = doc.getNextContentAfterRegex(Externalized.getString("ebayServer.bidCount"));
    int bidCount = 0;
    if(rawBidCount != null) {
      if(rawBidCount.equals(Externalized.getString("ebayServer.purchasesBidCount")) ||
         rawBidCount.endsWith(Externalized.getString("ebayServer.offerRecognition")) ||
         rawBidCount.equals(Externalized.getString("ebayServer.offerRecognition"))) {
        setFixedPrice(true);
        bidCount = -1;
      } else {
        if(rawBidCount.matches(Externalized.getString("ebayServer.bidderListCount"))) {
          bidCount = Integer.parseInt(_bidCountScript);
          _bidCountScript = null;
        } else {
          bidCount = getDigits(rawBidCount);
        }
      }
    }

    //  If we can't match any digits in the bidcount, or there is no match for the eBayBidCount regex, then
    //  this is a store or FP item.  Still true under BIBO?
    if (rawBidCount == null || getNumBids() == -1) {
      setHighBidder(Externalized.getString("ebayServer.fixedPrice"));
      setFixedPrice(true);

      if (doesLabelExist(Externalized.getString("ebayServer.hasBeenPurchased")) ||
          doesLabelPrefixExist(Externalized.getString("ebayServer.endedEarly"))) {
        bidCount = quantity;
        Date now = new Date();
        setEnd(now);
        if(getStart() == null) setStart(now);
      } else {
        bidCount = 0;
      }
    }

    return bidCount;
  }

  public ByteBuffer getSiteThumbnail() {
    ByteBuffer thumb = null;
    if(potentialThumbnail != null) {
      thumb = getThumbnailByURL(potentialThumbnail);
    }
    if(thumb == null) {
      thumb = getThumbnailById(getIdentifier());
    }
    return thumb;
  }

  public ByteBuffer getAlternateSiteThumbnail() {
    return getThumbnailById(getIdentifier() + "6464");
  }

  private ByteBuffer getThumbnailById(String id) {
    return getThumbnailByURL("http://thumbs.ebaystatic.com/pict/" + id + ".jpg");
  }

  private ByteBuffer getThumbnailByURL(String url) {
    ByteBuffer tmpThumb;
    try {
      tmpThumb = ThumbnailLoader.downloadThumbnail(new URL(url));
    } catch(Exception ignored) {
      tmpThumb = null;
    }
    return tmpThumb;
  }

  private Integer getNumberFromLabel(JHTML doc, String label, String ignore) {
    String rawQuantity;
    if(ignore == null) {
      rawQuantity = doc.getNextContentAfterRegex(label);
    } else {
      rawQuantity = doc.getNextContentAfterRegexIgnoring(label, ignore);
    }
    Integer quant2;
    if(rawQuantity != null) {
      quant2 = getDigits(rawQuantity);
    } else {
      quant2 = null;
    }
    return quant2;
  }

  private void extractMotorsTitle() {
    String motorsTitle = mDocument.getContentBeforeContent(Externalized.getString("ebayServer.itemNum"));
    if(motorsTitle != null) {
      motorsTitle = motorsTitle.trim();
    }
    if(motorsTitle != null && motorsTitle.length() != 0 && !getTitle().equals(motorsTitle)) {
      if(motorsTitle.length() != 1 || motorsTitle.charAt(0) < HIGH_BIT_SET) {
        if(getTitle().length() == 0) {
          setTitle(StringTools.decodeLatin(motorsTitle));
        } else {
          setTitle(StringTools.decodeLatin(motorsTitle + " (" + getTitle() + ')'));
        }
      }
    }
  }
}
