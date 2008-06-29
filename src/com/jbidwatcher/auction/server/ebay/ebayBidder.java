package com.jbidwatcher.auction.server.ebay;

import com.jbidwatcher.auction.server.*;
import com.jbidwatcher.auction.*;
import com.jbidwatcher.auction.BadBidException;
import com.jbidwatcher.auction.LoginManager;
import com.jbidwatcher.auction.AuctionServerInterface;
import com.jbidwatcher.util.html.JHTML;
import com.jbidwatcher.util.http.CookieJar;
import com.jbidwatcher.util.http.Http;
import com.jbidwatcher.util.Externalized;
import com.jbidwatcher.util.config.ErrorManagement;
import com.jbidwatcher.util.StringTools;
import com.jbidwatcher.util.Currency;
import com.jbidwatcher.util.UpdateBlocker;
import com.jbidwatcher.util.config.JConfig;
import com.jbidwatcher.util.queue.MQFactory;
import com.jbidwatcher.my.MyJBidwatcher;

import java.net.URLConnection;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.HashMap;

/**
 * Created by IntelliJ IDEA.
* User: mrs
* Date: Feb 26, 2007
* Time: 1:38:12 AM
* To change this template use File | Settings | File Templates.
*/
public class ebayBidder implements com.jbidwatcher.auction.Bidder {
  private LoginManager mLogin;
  private HashMap<String, Integer> mResultHash = null;
  private String mBidResultRegex = null;

  private Pattern mFindBidResult;

  public ebayBidder(LoginManager login) {
    mLogin = login;
    /**
     * Build a simple hashtable of results that bidding might get.
     * Not the greatest solution, but it's working okay.  A better one
     * would be great.
     */
    if(mResultHash == null) {
      mResultHash = new HashMap<String, Integer>();
      mResultHash.put("you('re| are) not permitted to bid on their listings.", AuctionServer.BID_ERROR_BANNED);
      mResultHash.put("the item is no longer available because the auction has ended.", AuctionServer.BID_ERROR_ENDED);
      mResultHash.put("cannot proceed", AuctionServer.BID_ERROR_CANNOT);
      mResultHash.put("problem with bid amount", AuctionServer.BID_ERROR_AMOUNT);
      mResultHash.put("your bid must be at least ", AuctionServer.BID_ERROR_TOO_LOW);
      mResultHash.put("you('ve| have) been outbid by another bidder", AuctionServer.BID_ERROR_OUTBID);
      mResultHash.put("you('ve| have) just been outbid", ebayServer.BID_ERROR_OUTBID);
      mResultHash.put("your bid is confirmed!", AuctionServer.BID_DUTCH_CONFIRMED);
      mResultHash.put("you('re| are) bidding on this multiple item auction", AuctionServer.BID_DUTCH_CONFIRMED);
      mResultHash.put("you('re| are) the high bidder on all items you bid on", AuctionServer.BID_DUTCH_CONFIRMED);
      mResultHash.put("you('re| are) the current high bidder", AuctionServer.BID_WINNING);
      mResultHash.put("you('re| are) the first bidder", AuctionServer.BID_WINNING);
      mResultHash.put("you('re| are) the high bidder and currently in the lead", AuctionServer.BID_WINNING);
      mResultHash.put("you('re| are) currently the highest bidder", AuctionServer.BID_WINNING);
      mResultHash.put("you purchased the item", AuctionServer.BID_WINNING);
      mResultHash.put("you('re| are) currently the high bidder, but the reserve hasn.t been met", AuctionServer.BID_ERROR_RESERVE_NOT_MET);
      mResultHash.put("the reserve price has not been met", AuctionServer.BID_ERROR_RESERVE_NOT_MET);
      mResultHash.put("the reserve (price )?has(n.t| not) been met", AuctionServer.BID_ERROR_RESERVE_NOT_MET);
      mResultHash.put("your new total must be higher than your current total", AuctionServer.BID_ERROR_TOO_LOW_SELF);
      mResultHash.put("this exceeds or is equal to your current bid", AuctionServer.BID_ERROR_TOO_LOW_SELF);
      mResultHash.put("you (just )?bought this item", AuctionServer.BID_BOUGHT_ITEM);
      mResultHash.put("you committed to buy", AuctionServer.BID_BOUGHT_ITEM);
      mResultHash.put("congratulations! you won!", AuctionServer.BID_BOUGHT_ITEM);
      mResultHash.put("account suspended", AuctionServer.BID_ERROR_ACCOUNT_SUSPENDED);
      mResultHash.put("to enter a higher maximum bid, please enter", AuctionServer.BID_ERROR_TOO_LOW_SELF);
      mResultHash.put("you are registered in a country to which the seller doesn.t ship.", AuctionServer.BID_ERROR_WONT_SHIP);
      mResultHash.put("this seller has set buyer requirements for this item and only sells to buyers who meet those requirements.", AuctionServer.BID_ERROR_REQUIREMENTS_NOT_MET);
    }

    //"If you want to submit another bid, your new total must be higher than your current total";
    StringBuffer superRegex = null;
    for(String key : mResultHash.keySet()) {
      if (superRegex == null) {
        superRegex = new StringBuffer(".*(");
      } else {
        superRegex.append('|');
      }
      superRegex.append(key);
    }
    if(superRegex != null) superRegex.append(").*");
    mBidResultRegex = new StringBuilder().append("(?msi)").append(superRegex).toString();
    mBidResultRegex = mBidResultRegex.replace(" ", "\\s+");
    mFindBidResult = Pattern.compile(mBidResultRegex);
    mResultHash.put("sign in", AuctionServer.BID_ERROR_CANT_SIGN_IN);
  }

  /**
   * Get the bidding form (with bid key) from the basic bid page.
   *
   * @param cj - The cookies for the current session.
   * @param inEntry - The auction being bid on.
   * @param inCurr - The amount to bid.
   * @param inQuant - The quantity to bid on (if it's a dutch auction, 1 otherwise).
   *
   * @return - A Form object containing all the input fields from the bid-confirmation page's form.
   *
   * @throws BadBidException - If there's some kind of an error on the bid confirmation page.
   */
  public JHTML.Form getBidForm(CookieJar cj, AuctionEntry inEntry, Currency inCurr, int inQuant) throws BadBidException {
    String bidRequest = Externalized.getString("ebayServer.protocol") + Externalized.getString("ebayServer.bidHost") + Externalized.getString("ebayServer.V3file");
    String bidInfo = getBidInfoURL(inEntry, inCurr, inQuant);
    BidFormReturn rval = null;

    try {
      String pageName = bidRequest + '?' + bidInfo;

      if (JConfig.debugging) inEntry.setLastStatus("Loading bid request...");
      rval = getBidFormInternal(pageName, inEntry, cj);

      if ( !rval.isSuccess() && (cj = checkSignin(inEntry, rval.getDocument())) != null) {
        rval = getBidFormInternal(pageName, inEntry, cj);
      }

      if( !rval.isSuccess() && (pageName = checkForWarning(inEntry, rval.getDocument())) != null) {
        rval = getBidFormInternal(pageName, inEntry, cj);
      }

      if (rval.isSuccess()) return rval.getForm();
    } catch (IOException e) {
      ErrorManagement.handleException("Failure to get the bid key!  BID FAILURE!", e);
    }

    //  If we never got a valid return value (e.g. an exception was thrown early), punt.
    if(rval == null) return null;

    if(rval.getDocument() != null) {
      checkSignOn(rval.getDocument());
      checkBidErrors(rval.getDocument());
    }

    if(JConfig.queryConfiguration("my.jbidwatcher.enabled", "false").equals("true") &&
       JConfig.queryConfiguration("my.jbidwatcher.id") != null) {
      String recognize = MyJBidwatcher.getInstance().recognizeBidpage(inEntry.getIdentifier(), rval.getBuffer());
      Integer remote_result = null;
      try {
        remote_result = Integer.parseInt(recognize);
      } catch(NumberFormatException nfe) {
        //  Ignore it for now...
        ErrorManagement.logDebug(recognize);
      }

      if(remote_result != null && remote_result != AuctionServer.BID_ERROR_UNKNOWN) {
        throw new BadBidException("Remote-checked result", remote_result);
      }
    }

    if(JConfig.debugging) inEntry.setLastStatus("Failed to bid. 'Show Last Error' from context menu to see the failure page from the bid attempt.");
    inEntry.setErrorPage(rval.getBuffer());

    //  We don't recognize this error.  Damn.  Log it and freak.
    ErrorManagement.logFile(bidInfo, rval.getBuffer());
    return null;
  }

  private String checkForWarning(AuctionEntry inEntry, JHTML htmlDocument) throws UnsupportedEncodingException {
    String pageName = null;
    if (htmlDocument.grep(Externalized.getString("ebayServer.warningPage")) != null) {
      JHTML.Form continueForm = htmlDocument.getFormWithInput("firedFilterId");
      if (continueForm != null) {
        inEntry.setLastStatus("Trying to 'continue' for the actual bid.");
        pageName = continueForm.getCGI();
        pageName = pageName.replaceFirst("%[A-F][A-F0-9]%A0", "%A0");
      }
    }
    return pageName;
  }

  private String getBidInfoURL(AuctionEntry inEntry, Currency inCurr, int inQuant) {
    String bidInfo = Externalized.getString("ebayServer.bidCmd") + "&co_partnerid=" + Externalized.getString("ebayServer.itemCGI") + inEntry.getIdentifier() + "&fb=2";
    if(inEntry.isDutch()) {
      bidInfo += Externalized.getString("ebayServer.quantCGI") + inQuant;
    }
    bidInfo += Externalized.getString("ebayServer.bidCGI") + inCurr.getValue();
    return bidInfo;
  }

  private CookieJar checkSignin(AuctionEntry inEntry, JHTML htmlDocument) {
    String signOn = htmlDocument.getTitle();
    if (signOn != null) {
      ErrorManagement.logDebug("Checking sign in as bid key load failed!");
      if (StringTools.startsWithIgnoreCase(signOn, "sign in")) {
        //  This means we somehow failed to keep the login in place.  Bad news, in the middle of a snipe.
        ErrorManagement.logDebug("Being prompted again for sign in, retrying.");
        if(JConfig.debugging) inEntry.setLastStatus("Not done loading bid request, got re-login request...");
        CookieJar cj = mLogin.getSignInCookie(null);
        if(JConfig.debugging) inEntry.setLastStatus("Done re-logging in, retrying load bid request.");
        return cj;
      }
    }
    return null;
  }

  private class BidFormReturn {
    private boolean mSuccess;
    private JHTML.Form mForm;
    private JHTML mDocument;
    private StringBuffer mBuffer;

    public boolean isSuccess() { return mSuccess; }
    public JHTML.Form getForm() { return mForm; }
    public JHTML getDocument() { return mDocument; }
    public StringBuffer getBuffer() { return mBuffer; }

    private BidFormReturn(boolean success, JHTML.Form form, JHTML document, StringBuffer buffer) {
      mSuccess = success;
      mForm = form;
      mDocument = document;
      mBuffer = buffer;
    }
  }

  private BidFormReturn getBidFormInternal(String pageName, AuctionEntry inEntry, CookieJar cj) throws IOException {
    URLConnection huc = cj.getAllCookiesFromPage(pageName, null, false);
    StringBuffer loadedPage;

    //  If we failed to load, punt.  Treat it as success, but with a null form result.
    if (huc == null || (loadedPage = Http.receivePage(huc)) == null) {
      return new BidFormReturn(true, null, null, null);
    }

    JHTML htmlDocument = new JHTML(loadedPage);
    JHTML.Form bidForm = htmlDocument.getFormWithInput("key");

    if (bidForm != null) {
      if (JConfig.debugging) inEntry.setLastStatus("Done loading bid request, got form...");
      return new BidFormReturn(true, bidForm, htmlDocument, loadedPage);
    }
    return new BidFormReturn(false, null, htmlDocument, loadedPage);
  }

  private void checkBidErrors(JHTML htmlDocument) throws BadBidException {
    String errMsg = htmlDocument.grep(mBidResultRegex);
    if(errMsg != null) {
      Matcher bidMatch = mFindBidResult.matcher(errMsg);
      bidMatch.find();
      String matched_error = bidMatch.group().toLowerCase();
      throw new BadBidException(matched_error, getMatchedResult(matched_error));
    } else {
      String amount = htmlDocument.getNextContentAfterRegex("\\s*Enter\\s*");
      if (amount != null) {
        String orMore = htmlDocument.getNextContent();
        if (orMore != null && orMore.indexOf("or more") != -1) {
          throw new BadBidException("Enter " + amount + orMore, ebayServer.BID_ERROR_TOO_LOW);
        }
      }
    }
  }

  private void checkSignOn(JHTML htmlDocument) throws BadBidException {
    String signOn = htmlDocument.getTitle();
    if(signOn != null && signOn.equalsIgnoreCase("Sign In")) throw new BadBidException("sign in", AuctionServerInterface.BID_ERROR_CANT_SIGN_IN);
  }

  private Integer getMatchedResult(String matched_text) {
    for (String regex : mResultHash.keySet()) {
      String hacked = "(?msi).*" + regex.replace(" ", "\\s+") + ".*";
      if(matched_text.matches(hacked)) return mResultHash.get(regex);
    }

    return null;
  }

  public int buy(AuctionEntry ae, int quantity) {
    String buyRequest = "http://offer.ebay.com/ws/eBayISAPI.dll?MfcISAPICommand=BinConfirm&fb=1&co_partnerid=&item=" + ae.getIdentifier() + "&quantity=" + quantity;

    StringBuffer sb;

    try {
      sb = mLogin.getNecessaryCookie(false).getAllCookiesAndPage(buyRequest, null, false);
      JHTML doBuy = new JHTML(sb);
      JHTML.Form buyForm = doBuy.getFormWithInput("uiid");

      if (buyForm != null) {
        buyForm.delInput("BIN_button");
        CookieJar cj = mLogin.getNecessaryCookie(false);
        StringBuffer loadedPage = cj.getAllCookiesAndPage(buyForm.getCGI(), buyRequest, false);
        if (loadedPage == null) return AuctionServerInterface.BID_ERROR_CONNECTION;
        return handlePostBidBuyPage(cj, loadedPage, buyForm, ae);
      }
    } catch (CookieJar.CookieException ignored) {
      return AuctionServerInterface.BID_ERROR_CONNECTION;
    } catch (UnsupportedEncodingException uee) {
      ErrorManagement.handleException("UTF-8 not supported locally, can't URLEncode buy form.", uee);
      return AuctionServerInterface.BID_ERROR_CONNECTION;
    }

    ae.setErrorPage(sb);
    return AuctionServerInterface.BID_ERROR_UNKNOWN;
  }

  public int bid(AuctionEntry inEntry, Currency inBid, int inQuantity) {
    UpdateBlocker.startBlocking();
    if(JConfig.queryConfiguration("sound.enable", "false").equals("true")) MQFactory.getConcrete("sfx").enqueue("/audio/bid.mp3");

    JHTML.Form bidForm;

    try {
      bidForm = getBidForm(mLogin.getNecessaryCookie(false), inEntry, inBid, inQuantity);
    } catch(BadBidException bbe) {
      UpdateBlocker.endBlocking();
      return bbe.getResult();
    }

    if (bidForm != null) {
      int rval = placeFinalBid(mLogin.getNecessaryCookie(false), bidForm, inEntry, inBid, inQuantity);
      UpdateBlocker.endBlocking();
      return rval;
    }
    ErrorManagement.logMessage("Bad/nonexistent key read in bid, or connection failure!");

    UpdateBlocker.endBlocking();
    return AuctionServerInterface.BID_ERROR_UNKNOWN;
  }

  public int placeFinalBid(CookieJar cj, JHTML.Form bidForm, AuctionEntry inEntry, Currency inBid, int inQuantity) {
    String bidRequest = Externalized.getString("ebayServer.protocol") + Externalized.getString("ebayServer.bidHost") + Externalized.getString("ebayServer.V3file");
    String bidInfo = Externalized.getString("ebayServer.bidCmd") + Externalized.getString("ebayServer.itemCGI") + inEntry.getIdentifier() +
        Externalized.getString("ebayServer.quantCGI") + inQuantity +
        Externalized.getString("ebayServer.bidCGI") + inBid.getValue();
    String bidURL = bidRequest + '?' + bidInfo;

    bidForm.delInput("BIN_button");
    StringBuffer loadedPage = null;

    //  This SHOULD be POSTed, but only works if sent with GET.
    try {
      if (JConfig.debugging) inEntry.setLastStatus("Submitting bid form.");
      loadedPage = cj.getAllCookiesAndPage(bidForm.getCGI(), bidURL, false);
      if (JConfig.debugging) inEntry.setLastStatus("Done submitting bid form.");
    } catch (UnsupportedEncodingException uee) {
      ErrorManagement.handleException("UTF-8 not supported locally, can't URLEncode bid form.", uee);
    } catch (CookieJar.CookieException ignored) {
      return AuctionServerInterface.BID_ERROR_CONNECTION;
    }

    if (loadedPage == null) {
      return AuctionServerInterface.BID_ERROR_CONNECTION;
    }
    return handlePostBidBuyPage(cj, loadedPage, bidForm, inEntry);
  }

  private int handlePostBidBuyPage(CookieJar cj, StringBuffer loadedPage, JHTML.Form bidForm, AuctionEntry inEntry) {
    if(JConfig.debugging) inEntry.setLastStatus("Loading post-bid data.");
    JHTML htmlDocument = new JHTML(loadedPage);

    JHTML.Form continueForm = htmlDocument.getFormWithInput("firedFilterId");
    if(continueForm != null) {
      try {
        inEntry.setLastStatus("Trying to 'continue' to the bid result page.");
        String cgi = continueForm.getCGI();
        //  For some reason, the continue page represents the currency as
        //  separated from the amount with a '0xA0' character.  When encoding,
        //  this becomes...broken somehow, and adds an extra character, which
        //  does not work when bidding.
        cgi = cgi.replaceFirst("%[A-F][A-F0-9]%A0", "%A0");
        URLConnection huc = cj.getAllCookiesFromPage(cgi, null, false);
        //  We failed to load, entirely.  Punt.
        if (huc == null) return AuctionServerInterface.BID_ERROR_CONNECTION;

        loadedPage = Http.receivePage(huc);
        //  We failed to load.  Punt.
        if (loadedPage == null) return AuctionServerInterface.BID_ERROR_CONNECTION;

        htmlDocument = new JHTML(loadedPage);
      } catch(Exception ignored) {
        return AuctionServerInterface.BID_ERROR_CONNECTION;
      }
    }

    String errMsg = htmlDocument.grep(mBidResultRegex);
    if (errMsg != null) {
      Matcher bidMatch = mFindBidResult.matcher(errMsg);
      bidMatch.find();
      String matched_error = bidMatch.group().toLowerCase();
      Integer bidResult = getMatchedResult(matched_error);

      int result = 0;
      if (bidResult != null) {
        result = bidResult;
        if (result == ebayServer.BID_ERROR_BANNED ||
            result == ebayServer.BID_ERROR_WONT_SHIP ||
            result == ebayServer.BID_ERROR_REQUIREMENTS_NOT_MET) {
          inEntry.setErrorPage(loadedPage);
        }
      } else {
        String amount = htmlDocument.getNextContentAfterRegex("Enter");
        if (amount != null) {
          String orMore = htmlDocument.getNextContent();
          if (orMore != null && orMore.indexOf("or more") != -1) {
            result = ebayServer.BID_ERROR_TOO_LOW;
          }
        }
      }

      if(JConfig.debugging) inEntry.setLastStatus("Done loading post-bid data.");

      if(bidResult != null) return result;
    }

    // Skipping the userID and Password, so this can be submitted as
    // debugging info.
    bidForm.setText("user", "HIDDEN");
    bidForm.setText("pass", "HIDDEN");
    String safeBidInfo = "";
    try {
      safeBidInfo = bidForm.getCGI();
    } catch(UnsupportedEncodingException uee) {
      ErrorManagement.handleException("UTF-8 not supported locally, can't URLEncode CGI for debugging.", uee);
    }

    if(JConfig.debugging) inEntry.setLastStatus("Failed to load post-bid data. 'Show Last Error' from context menu to see the failure page from the post-bid page.");
    inEntry.setErrorPage(loadedPage);

    ErrorManagement.logFile(safeBidInfo, loadedPage);
    return AuctionServerInterface.BID_ERROR_UNKNOWN;
  }
}
