package com.jbidwatcher.auction.server.ebay;

import com.jbidwatcher.auction.SpecificAuction;
import com.jbidwatcher.auction.AuctionEntry;
import com.jbidwatcher.auction.ThumbnailManager;
import com.jbidwatcher.auction.server.AuctionServer;
import com.jbidwatcher.util.*;
import com.jbidwatcher.util.html.JHTML;
import com.jbidwatcher.util.html.htmlToken;
import com.jbidwatcher.queue.MQFactory;
import com.jbidwatcher.platform.Platform;
import com.jbidwatcher.config.JConfig;

import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.util.Date;
import java.io.UnsupportedEncodingException;
import java.net.URL;

/**
 * Created by IntelliJ IDEA.
* User: Morgan
* Date: Feb 25, 2007
* Time: 4:08:52 PM
* To change this template use File | Settings | File Templates.
*/
class ebayAuction extends SpecificAuction {
  String _bidCountScript = null;
  String _startComment = null;
  private static final int TITLE_LENGTH = 60;
  private static final int HIGH_BIT_SET = 0x80;
  //private final Pattern p = Pattern.compile("src=\"(http://[a-z0-9]+?\\.ebayimg\\.com.*?(jpg|gif|png))\"", Pattern.DOTALL | Pattern.MULTILINE | Pattern.CASE_INSENSITIVE);
  private final Pattern p = Pattern.compile(Externalized.getString("ebayServer.thumbSearch"), Pattern.DOTALL | Pattern.MULTILINE | Pattern.CASE_INSENSITIVE);
  private final Pattern p2 = Pattern.compile(Externalized.getString("ebayServer.thumbSearch2"), Pattern.DOTALL | Pattern.MULTILINE | Pattern.CASE_INSENSITIVE);
  private String potentialThumbnail = null;

  private static final String dateMatch = "(?i)(Ends|end.time).([A-Za-z]+(.[0-9]+)+.[A-Z]+)";
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
    if(startCommentSearch.find()) _startComment = startCommentSearch.group(1);
    else _startComment = "";

    Matcher bidCountSearch = Pattern.compile(Externalized.getString("ebayServer.bidCountRegex")).matcher(skimOver);
    if(bidCountSearch.find()) _bidCountScript = bidCountSearch.group(1);
    else _bidCountScript = "";

    //  Use eBay's cleanup method to finish up with.
    new ebayCleaner().cleanup(sb);
  }

  boolean checkTitle(String auctionTitle) {
    if(auctionTitle.startsWith(Externalized.getString("ebayServer.liveAuctionsTitle"))) {
      ErrorManagement.logMessage("JBidWatcher cannot handle live auctions!");
      return false;
    }

    if(auctionTitle.startsWith(Externalized.getString("ebayServer.greatCollectionsTitle"))) {
      ErrorManagement.logMessage("JBidWatcher cannot handle Great Collections items yet.");
      return false;
    }

    String[] eBayTitles = new String[]{
        Externalized.getString("ebayServer.titleEbay"),
        Externalized.getString("ebayServer.titleEbay2"),
        Externalized.getString("ebayServer.titleMotors"),
        Externalized.getString("ebayServer.titleMotors2"),
        Externalized.getString("ebayServer.titleDisney"),
        Externalized.getString("ebayServer.titleCollections")};

    for (String eBayTitle : eBayTitles) {
      if (auctionTitle.startsWith(eBayTitle)) return true;
    }

    return false;
  }

  private com.jbidwatcher.util.Currency getUSCurrency(com.jbidwatcher.util.Currency val, JHTML _htmlDoc) {
    com.jbidwatcher.util.Currency newCur = null;

    if(val != null && !val.isNull()) {
      if (val.getCurrencyType() == com.jbidwatcher.util.Currency.US_DOLLAR) {
        newCur = val;
      } else {
        String approxAmount = _htmlDoc.getNextContent();
        //  If the next text doesn't contain a USD amount, it's seperated somehow.
        //  Skim forwards until we either find something, or give up.  (6 steps for now.)
        int i = 0;
        while (i++ < 6 && approxAmount.indexOf(Externalized.getString("ebayServer.USD")) == -1) {
          approxAmount = _htmlDoc.getNextContent();
        }
        //  If we still have no values visible, punt and treat it as zero.
        if (approxAmount.indexOf(Externalized.getString("ebayServer.USD")) == -1) {
          newCur = com.jbidwatcher.util.Currency.getCurrency("$0.00");
        } else {
          approxAmount = approxAmount.substring(approxAmount.indexOf(Externalized.getString("ebayServer.USD")));
          newCur = com.jbidwatcher.util.Currency.getCurrency(approxAmount);
        }
      }
    }

    return newCur;
  }

  private Pattern digits = Pattern.compile("([0-9]+)");

  int getDigits(String digitsStarting) {
    Matcher m = digits.matcher(digitsStarting);
    m.find();
    String rawCount = m.group();
    if(rawCount != null) {
      return Integer.parseInt(rawCount);
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

  private void load_shipping_insurance(com.jbidwatcher.util.Currency sampleAmount) {
    String shipString = _htmlDocument.getNextContentAfterRegex(Externalized.getString("ebayServer.shipping"));
    //  Sometimes the next content might not be the shipping amount, it might be the next-next.
    Matcher amount = null;
    boolean amountFound = false;
    if(shipString != null) {
      amount = amountPat.matcher(shipString);
      amountFound = amount.find();
      if (!amountFound) {
        shipString = _htmlDocument.getNextContent();
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
    String shipStringCheck = _htmlDocument.getPrevContent(2);

    String insureString = _htmlDocument.getNextContentAfterRegex(Externalized.getString("ebayServer.shippingInsurance"));
    String insuranceOptionalCheck = _htmlDocument.getNextContent();

    //  Default to thinking it's optional if the word 'required' isn't found.
    //  You don't want to make people think it's required if it's not.
    _insurance_optional = insuranceOptionalCheck == null || (insuranceOptionalCheck.toLowerCase().indexOf(Externalized.getString("ebayServer.requiredInsurance")) == -1);

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
        _shipping = com.jbidwatcher.util.Currency.getCurrency(sampleAmount.fullCurrencyName(), "0.0");
      } else {
        try {
          _shipping = com.jbidwatcher.util.Currency.getCurrency(sampleAmount.fullCurrencyName(), shipString);
        } catch(NumberFormatException nfe) {
          _shipping = com.jbidwatcher.util.Currency.NoValue();
        }
      }
    } else {
      _shipping = com.jbidwatcher.util.Currency.NoValue();
    }
    try {
      _insurance = com.jbidwatcher.util.Currency.getCurrency(insureString);
    } catch(NumberFormatException nfe) {
      _insurance = com.jbidwatcher.util.Currency.NoValue();
    }
  }

  private void load_buy_now() {
    _buy_now = com.jbidwatcher.util.Currency.NoValue();
    _buy_now_us = com.jbidwatcher.util.Currency.NoValue();
    String buyNowString = _htmlDocument.getNextContentAfterContent(Externalized.getString("ebayServer.buyItNow"));
    if(buyNowString != null) {
      buyNowString = buyNowString.trim();
      while(buyNowString.length() == 0 || buyNowString.equals(Externalized.getString("ebayServer.buyNowFor"))) {
        buyNowString = _htmlDocument.getNextContent().trim();
      }
    }

    if(buyNowString != null && !buyNowString.equals(Externalized.getString("ebayServer.ended"))) {
      _buy_now = com.jbidwatcher.util.Currency.getCurrency(buyNowString);
      _buy_now_us = getUSCurrency(_buy_now, _htmlDocument);
    }
    if(buyNowString == null || buyNowString.equals(Externalized.getString("ebayServer.ended")) || _buy_now == null || _buy_now.isNull()) {
      String altBuyNowString1 = _htmlDocument.getNextContentAfterRegexIgnoring(Externalized.getString("ebayServer.price"), "[Ii]tem.[Nn]umber");
      if(altBuyNowString1 != null) {
        altBuyNowString1 = altBuyNowString1.trim();
      }
      if(altBuyNowString1 != null && altBuyNowString1.length() != 0) {
        _buy_now = com.jbidwatcher.util.Currency.getCurrency(altBuyNowString1);
        _buy_now_us = getUSCurrency(_buy_now, _htmlDocument);
      }
    }
  }

  private String getEndDate(String inTitle) {
    String result = null;

    Matcher dateMatch = datePat.matcher(inTitle);
    if(dateMatch.find()) result = dateMatch.group(2);

    return result;
  }

  private String decodeLatin(String latinString) {
    //  Why?  Because it seems to Just Work on Windows.  Argh.
    if(!Platform.isMac()) return latinString;
    try {
      return new String(latinString.getBytes(), "ISO-8859-1");
    } catch (UnsupportedEncodingException ignore) {
      return latinString;
    }
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

  private void loadSecondaryInformation(JHTML doc) {
    try {
      String score = getResult(doc, Externalized.getString("ebayServer.feedbackRegex"), 1);
      if(score != null && StringTools.isNumberOnly(score)) {
        _feedback = Integer.parseInt(score);
      }

      String percentage = getResult(doc, Externalized.getString("ebayServer.feedbackPercentageRegex"), 1);
      if(percentage != null) _postivePercentage = percentage;

      String location = doc.getNextContentAfterRegex(Externalized.getString("ebayServer.itemLocationRegex"));
      if(location != null) {
        _itemLocation = location;
      }

      String pbp = getResult(doc, Externalized.getString("ebayServer.paypalMatcherRegex"), 0);
      if(pbp != null) _paypal = true;
    } catch(Throwable t) {
      //  I don't actually CARE about any of this data, or any errors that occur on loading it, so don't mess things up on errors.
      ErrorManagement.logDebug(t.getMessage());
    }
  }

  /**
   * @brief - Not brief at all; wow...this is way too long.
   *
   * @param ae - The auction entry to update or null if it's a new auction.
   *
   * @return - false if the parse failed, true if it succeeded.  This needs
   * to be turned into a set of enums.
   */
  public AuctionServer.ParseErrors parseAuction(AuctionEntry ae) {
    //  Verify the title (in case it's an invalid page, the site is
    //  down for maintenance, etc).
    String prelimTitle = _htmlDocument.getFirstContent();
    if( prelimTitle == null) {
      prelimTitle = Externalized.getString("ebayServer.unavailable");
    }
    if(prelimTitle.equals(Externalized.getString("ebayServer.adultPageTitle")) || prelimTitle.indexOf("Terms of Use: ") != -1) {
      finish();
      return AuctionServer.ParseErrors.NOT_ADULT;
    }

    //  Is this a valid eBay item page?
    if(!checkTitle(prelimTitle)) {
      handle_bad_title(prelimTitle);
      finish();
      return AuctionServer.ParseErrors.BAD_TITLE;
    }

    //  If we got a valid title, mark the link as up, because it worked...
    MQFactory.getConcrete("Swing").enqueue("LINK UP");

    boolean ebayMotors = false;
    if(prelimTitle.indexOf(Externalized.getString("ebayServer.ebayMotorsTitle")) != -1) ebayMotors = true;
    //  This is mostly a hope, not a guarantee, as eBay might start
    //  cross-advertising eBay Motors in their normal pages, or
    //  something.
    if(doesLabelExist(Externalized.getString("ebayServer.ebayMotorsTitle"))) ebayMotors = true;

    _end = null;
    _title = null;

    //  This sucks.  They changed to: eBay: {title} (item # end time {endtime})
    if(prelimTitle.startsWith(Externalized.getString("ebayServer.titleEbay2")) ||
       prelimTitle.startsWith(Externalized.getString("ebayServer.titleMotors2"))) {
      //  Handle the new titles.
      Pattern newTitlePat = Pattern.compile(Externalized.getString("ebayServer.titleMatch"));
      Matcher newTitleMatch = newTitlePat.matcher(prelimTitle);
//        Regex newTitleR = new Regex(Externalized.getString("ebayServer.titleMatch"));
      if(newTitleMatch.find()) {
        _title = decodeLatin(newTitleMatch.group(2));
        String endDate = newTitleMatch.group(4);
        _end = StringTools.figureDate(endDate, Externalized.getString("ebayServer.dateFormat")).getDate();
      }
    }

    if(_title == null) {
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
        _title = decodeLatin(buildTitle(_htmlDocument));
      } else {
        _title = decodeLatin(prelimTitle.substring(titleIndex+4).trim());
      }
    }

    if(_title.length() == 0) _title = "(bad title)";
    _title = JHTML.deAmpersand(_title);

    // eBay Motors titles are really a combination of the make/model,
    // and the user's own text.  Under BIBO, the user's own text is
    // below the 'description' fold.  For now, we don't get the user
    // text.
    if(ebayMotors) {
      extractMotorsTitle();
    }

    //  Get the integer values (Quantity, Bidcount)
    _quantity = getNumberFromLabel(_htmlDocument, Externalized.getString("ebayServer.quantity"), Externalized.getString("ebayServer.postTitleIgnore"));

    _fixed_price = false;
    _numBids = getBidCount(_htmlDocument, _quantity);

    try {
      load_buy_now();
    } catch(Exception e) {
      ErrorManagement.handleException("Buy It Now Loading error", e);
    }

    if(_end == null) {
      String endDate = getEndDate(prelimTitle);
      _end = StringTools.figureDate(endDate, Externalized.getString("ebayServer.dateFormat")).getDate();
    }

    _start = StringTools.figureDate(_htmlDocument.getNextContentAfterRegexIgnoring(Externalized.getString("ebayServer.startTime"), Externalized.getString("ebayServer.postTitleIgnore")), Externalized.getString("ebayServer.dateFormat")).getDate();
    if(_start == null) {
      _start = StringTools.figureDate(_startComment, Externalized.getString("ebayServer.dateFormat")).getDate();
    }
    _start = (Date)ensureSafeValue(_start, ae!=null?ae.getStartDate():null, null);

    if(_start == null) {
      _start = new Date(-1);
    }

    if (_fixed_price) {
      if(_buy_now != null && !_buy_now.isNull()) {
        _minBid = _buy_now;
        _curBid = _buy_now;
        _us_cur = _buy_now_us;
      } else {
        //  The set of tags that indicate the current/starting/lowest/winning
        //  bid are 'Starts at', 'Current bid', 'Starting bid', 'Lowest bid',
        //  'Winning bid' so far.  'Starts at' is mainly for live auctions!
        String cvtCur = _htmlDocument.getNextContentAfterRegex(Externalized.getString("ebayServer.currentBid"));
        _curBid = Currency.getCurrency(cvtCur);
        _us_cur = getUSCurrency(_curBid, _htmlDocument);

        _curBid = (Currency)ensureSafeValue(_curBid, ae!=null?ae.getCurBid()  : Currency.NoValue(), Currency.NoValue());
        _us_cur = (Currency)ensureSafeValue(_us_cur, ae!=null?ae.getUSCurBid(): Currency.NoValue(), Currency.NoValue());
      }
    } else {
      //  The set of tags that indicate the current/starting/lowest/winning
      //  bid are 'Current bid', 'Starting bid', 'Lowest bid',
      //  'Winning bid' so far.
      String cvtCur = _htmlDocument.getNextContentAfterRegex(Externalized.getString("ebayServer.currentBid"));
      _curBid = Currency.getCurrency(cvtCur);
      _us_cur = getUSCurrency(_curBid, _htmlDocument);

      if(_curBid == null || _curBid.isNull()) {
        if(_quantity > 1) {
          _curBid = Currency.getCurrency(_htmlDocument.getNextContentAfterContent(Externalized.getString("ebayServer.lowestBid")));
          _us_cur = getUSCurrency(_curBid, _htmlDocument);
        }
      }

      _minBid = Currency.getCurrency(_htmlDocument.getNextContentAfterContent(Externalized.getString("ebayServer.firstBid")));
      //  Handle odd case...
      if(_end == null) {
        _end = StringTools.figureDate(_htmlDocument.getNextContentAfterContent(Externalized.getString("ebayServer.endsPrequel")), Externalized.getString("ebayServer.dateFormat")).getDate();
      }
      Currency maxBid = Currency.getCurrency(_htmlDocument.getNextContentAfterContent(Externalized.getString("ebayServer.yourMaxBid")));

      _end    =     (Date)ensureSafeValue(_end,    ae!=null?ae.getEndDate() :null, null);
      _minBid = (Currency)ensureSafeValue(_minBid, ae!=null?ae.getMinBid()  : Currency.NoValue(), Currency.NoValue());
      _curBid = (Currency)ensureSafeValue(_curBid, ae!=null?ae.getCurBid()  : Currency.NoValue(), Currency.NoValue());
      _us_cur = (Currency)ensureSafeValue(_us_cur, ae!=null?ae.getUSCurBid(): Currency.NoValue(), Currency.NoValue());

      if(_numBids == 0 && (_minBid == null || _minBid.isNull())) _minBid = _curBid;

      if(_minBid == null || _minBid.isNull()) {
        String original = _htmlDocument.grep(Externalized.getString("ebayServer.originalBid"));
        if(original != null) {
          Pattern bidPat = Pattern.compile(Externalized.getString("ebayServer.originalBid"));
          Matcher bidMatch = bidPat.matcher(original);
          if(bidMatch.find()) {
            _minBid = Currency.getCurrency(bidMatch.group(1));
          }
        }
      }

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
      _outbid = _htmlDocument.grep(Externalized.getString("ebayServer.outbid")) != null;
    }

    try {
      load_shipping_insurance(_curBid);
    } catch(Exception e) {
      ErrorManagement.handleException("Shipping / Insurance Loading Failed", e);
    }

    _seller = _htmlDocument.getNextContentAfterRegex(Externalized.getString("ebayServer.seller"));
    if(_seller == null) {
      _seller = _htmlDocument.getNextContentAfterContent(Externalized.getString("ebayServer.sellerInfoPrequel"), false, true);
    }
    if(_seller == null) {
      if(_htmlDocument.grep(Externalized.getString("ebayServer.sellerAway")) != null) {
        if(ae != null) {
          ae.setLastStatus("Seller away - item unavailable.");
        }
        finish();
        return AuctionServer.ParseErrors.SELLER_AWAY;
      } else {
        _seller = "(unknown)";
      }
    }
    _seller = _seller.trim();

    if(_end.getTime() > System.currentTimeMillis()) {
      //  Item is not ended yet.
      if(ae != null) {
        ae.setEnded(false);
        ae.setSticky(false);
      }
    }
    /**
     * THIS is absurd.  This needs to be cleaned up.  -- mrs: 18-September-2003 21:08
     */
    if (_fixed_price) {
      _highBidder = _htmlDocument.getNextContentAfterRegex(Externalized.getString("ebayServer.buyer"));
      if (_highBidder != null) {
        _numBids = 1;
        _highBidder = _highBidder.trim();
        _highBidderEmail = _htmlDocument.getNextContentAfterContent(_highBidder, true, false);
        if (_highBidderEmail != null) {
          _highBidderEmail = _highBidderEmail.trim();
          if (_highBidderEmail.charAt(0) == '(' && _highBidderEmail.charAt(_highBidderEmail.length()-1) == ')' && _highBidderEmail.indexOf('@') != -1) {
            _highBidderEmail = _highBidderEmail.substring(1, _highBidderEmail.length() - 1);
          }
        }
        if (_highBidderEmail == null || _highBidderEmail.equals("(")) {
          _highBidderEmail = "(unknown)";
        }
      } else {
        _highBidder = "";
      }
    } else {
      if (_quantity > 1) {
        _highBidder = "(dutch)";
        _isDutch = true;
      } else {
        _highBidder = "";
        if (_numBids != 0) {
          _highBidder = _htmlDocument.getNextContentAfterRegex(Externalized.getString("ebayServer.highBidder"));
          if (_highBidder != null) {
            _highBidder = _highBidder.trim();

            _highBidderEmail = _htmlDocument.getNextContentAfterContent(_highBidder, true, false);
            if (_highBidderEmail.charAt(0) == '(' && _highBidderEmail.charAt(_highBidderEmail.length()-1) == ')' && _highBidderEmail.indexOf('@') != -1) {
              _highBidderEmail = _highBidderEmail.substring(1, _highBidderEmail.length() - 1);
            }
          } else {
            _highBidder = "(unknown)";
          }
        }
      }
    }

    if(doesLabelExist(Externalized.getString("ebayServer.reserveNotMet1")) ||
       doesLabelExist(Externalized.getString("ebayServer.reserveNotMet2"))) {
      _isReserve = true;
      _reserveMet = false;
    } else {
      if(doesLabelExist(Externalized.getString("ebayServer.reserveMet1")) ||
         doesLabelExist(Externalized.getString("ebayServer.reserveMet2"))) {
        _isReserve = true;
        _reserveMet = true;
      }
    }
    if(_highBidder.indexOf(Externalized.getString("ebayServer.keptPrivate")) != -1) {
      _isPrivate = true;
      _highBidder = "(private)";
    }
    loadSecondaryInformation(_htmlDocument);
    try {
      if(JConfig.queryConfiguration("show.images", "true").equals("true")) {
        if(!_no_thumbnail && !hasThumbnail()) {
          MQFactory.getConcrete("thumbnail").enqueue(this);
        }
      }
    } catch(Exception e) {
      ErrorManagement.handleException("Error handling thumbnail loading", e);
    }
    finish();

    return AuctionServer.ParseErrors.SUCCESS;
  }

  private int getBidCount(JHTML doc, int quantity) {
    String rawBidCount = doc.getNextContentAfterRegex(Externalized.getString("ebayServer.bidCount"));
    int bidCount = 0;
    if(rawBidCount != null) {
      if(rawBidCount.equals(Externalized.getString("ebayServer.purchasesBidCount")) ||
         rawBidCount.endsWith(Externalized.getString("ebayServer.offerRecognition")) ||
         rawBidCount.equals(Externalized.getString("ebayServer.offerRecognition"))) {
        _fixed_price = true;
        bidCount = -1;
      } else {
        if(rawBidCount.equals(Externalized.getString("ebayServer.bidderListCount"))) {
          bidCount = Integer.parseInt(_bidCountScript);
        } else {
          bidCount = getDigits(rawBidCount);
        }
      }
    }

    //  If we can't match any digits in the bidcount, or there is no match for the eBayBidCount regex, then
    //  this is a store or FP item.  Still true under BIBO?
    if (rawBidCount == null || _numBids == -1) {
      _highBidder = Externalized.getString("ebayServer.fixedPrice");
      _fixed_price = true;

      if (doesLabelExist(Externalized.getString("ebayServer.hasBeenPurchased")) ||
          doesLabelPrefixExist(Externalized.getString("ebayServer.endedEarly"))) {
        bidCount = quantity;
        _start = _end = new Date();
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
      tmpThumb = ThumbnailManager.downloadThumbnail(new URL(url));
    } catch(Exception ignored) {
      tmpThumb = null;
    }
    return tmpThumb;
  }

  private int getNumberFromLabel(JHTML doc, String label, String ignore) {
    String rawQuantity;
    if(ignore == null) {
      rawQuantity = doc.getNextContentAfterRegex(label);
    } else {
      rawQuantity = doc.getNextContentAfterRegexIgnoring(label, ignore);
    }
    int quant2;
    if(rawQuantity != null) {
      quant2 = getDigits(rawQuantity);
    } else {
      //  Why would I set quantity to 0?
      quant2 = 1;
    }
    return quant2;
  }

  private void extractMotorsTitle() {
    String motorsTitle = _htmlDocument.getContentBeforeContent(Externalized.getString("ebayServer.itemNum"));
    if(motorsTitle != null) {
      motorsTitle = motorsTitle.trim();
    }
    if(motorsTitle != null && motorsTitle.length() != 0 && !_title.equals(motorsTitle)) {
      if(motorsTitle.length() != 1 || motorsTitle.charAt(0) < HIGH_BIT_SET) {
        if(_title.length() == 0) {
          _title = decodeLatin(motorsTitle);
        } else {
          _title = decodeLatin(motorsTitle + " (" + _title + ')');
        }
      }
    }
  }
}
