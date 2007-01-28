package com.jbidwatcher.auction.server.ebay;

/*
 * Copyright (c) 2000-2007, CyberFOX Software, Inc. All Rights Reserved.
 *
 * Developed by mrs (Morgan Schweers)
 */

//  This is the concrete implementation of AuctionServer to handle
//  parsing eBay auction pages.  There should be *ZERO* eBay specific
//  logic outside this class.  A pipe-dream, perhaps, but it seems
//  mostly doable.

import com.jbidwatcher.platform.Platform;
import com.jbidwatcher.config.JConfig;
import com.jbidwatcher.config.JConfigTab;
import com.jbidwatcher.config.JBConfig;
import com.jbidwatcher.queue.*;
import com.jbidwatcher.util.html.JHTML;
import com.jbidwatcher.util.html.htmlToken;
import com.jbidwatcher.util.http.CookieJar;
import com.jbidwatcher.util.http.Http;
import com.jbidwatcher.util.*;
import com.jbidwatcher.util.Currency;
import com.jbidwatcher.ui.JPasteListener;
import com.jbidwatcher.ui.OptionUI;
import com.jbidwatcher.ui.ServerMenu;
import com.jbidwatcher.search.Searcher;
import com.jbidwatcher.search.SearchManager;
import com.jbidwatcher.search.SearchManagerInterface;
import com.jbidwatcher.auction.*;
import com.jbidwatcher.auction.server.AuctionServerManager;
import com.jbidwatcher.auction.server.AuctionServer;
import com.jbidwatcher.TimerHandler;
import com.jbidwatcher.Constants;
import com.jbidwatcher.auction.ThumbnailManager;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.FileWriter;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.io.FileNotFoundException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.util.*;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** @noinspection OverriddenMethodCallInConstructor*/
public final class ebayServer extends AuctionServer implements MessageQueue.Listener,CleanupHandler,JConfig.ConfigListener {
  protected static final int THIRTY_MINUTES = (30 * 60 * 1000);

  private HashMap<String, Integer> _resultHash = null;
  private String mBidResultRegex = null;
  private Pattern mFindBidResult;

  /** @noinspection FieldAccessedSynchronizedAndUnsynchronized*/
  private volatile CookieJar _signinCookie = null;
  private eBayTimeQueueManager _etqm;
  private Searcher _my_ebay = null;
  private Searcher sellerSearch = null;

  private final static String eBayDisplayName = "eBay"; //$NON-NLS-1$
  private final static String eBayServerName = "ebay"; //$NON-NLS-1$

  private String eBayHost;
  private String eBayViewHost;
  private String eBayBrowseHost;
  private String eBayDetectionHost;
  private String eBayTWDetectionHost;
  private String eBayESDetectionHost;
  private String eBayBidHost;
  private String eBayDutchRequest;
  private String eBayProtocol;
  private String eBayFile;
  private String eBayV3File;
  private String eBayWS3File;
  private String eBayViewItemCmd;
  private String eBayParseItemURL;
  private String eBayViewItemCGI;
  private String eBayViewDutchWinners;
  private String eBaySearchURL1;
  private String eBaySearchURL2;
  private String eBaySearchURLNoDesc;
  private String eBayBidItem;

  //  eBay fields for parsing...  Change when eBay's format changes!
  private String eBayItemCGI;
  private String eBayItemNumber;
  private String eBayPrequelTimeString;
  private String eBayPrequelTimeString2;
  private String eBayDateFormat;
  private String eBayOfficialTimeFormat;
  private String eBayAdultLoginPageTitle;
  private String[] eBayTitles;
  private String eBayCurrentBid;
  private String eBayLowestBid;
  private String eBayFirstBid;
  private String eBayQuantity;
  private String eBayBidCount;
  private String eBayStartTime;
  private String eBaySeller;
  private String eBayHighBidder;
  private String eBayBuyer;
  private String eBayShippingRegex;
  private String eBayInsurance;
  private String eBayBuyItNow;
  private String eBayItemEnded;
  private String eBayTemporarilyUnavailable;
  private String eBayPrice;
  private String eBayDescStart;
  private String eBayMotorsDescStart;
  private String eBayDescEnd;
  private String eBayPayInstructions;
  private String eBayClosedDescEnd;

  private static final int ITEMS_PER_PAGE = 100;
  /** @noinspection FieldCanBeLocal*/
  private TimerHandler eQueue;
  private Map<String, AuctionQObject> snipeMap = new HashMap<String, AuctionQObject>();
  private String mBadPassword = null;
  private String mBadUsername = null;

  {
    loadStrings();
  }

  /** @noinspection TooBroadScope,UnusedAssignment,UNUSED_SYMBOL*/
  private void loadStrings() {
    eBayHost = Externalized.getString("ebayServer.host"); //$NON-NLS-1$
    eBayViewHost = Externalized.getString("ebayServer.viewHost"); //$NON-NLS-1$
    eBayBrowseHost = Externalized.getString("ebayServer.browseHost"); //$NON-NLS-1$
    eBayDetectionHost = Externalized.getString("ebayServer.detectionHost"); //$NON-NLS-1$
    eBayTWDetectionHost = Externalized.getString("ebayServer.TaiwanDetectionHost"); // Taiwan is the only URL of the form tw.ebay.com.  *sigh* //$NON-NLS-1$
    eBayESDetectionHost = Externalized.getString("ebayServer.SpainDetectionHost"); // Scratch that 'only', looks like Spain does that too. //$NON-NLS-1$
    eBayBidHost = Externalized.getString("ebayServer.bidHost"); //$NON-NLS-1$
    eBayDutchRequest = Externalized.getString("ebayServer.dutchRequestHost"); //$NON-NLS-1$
    eBayProtocol = Externalized.getString("ebayServer.protocol"); //$NON-NLS-1$
    eBayFile = Externalized.getString("ebayServer.file"); //$NON-NLS-1$
    eBayV3File = Externalized.getString("ebayServer.V3file"); //$NON-NLS-1$
    eBayWS3File = Externalized.getString("ebayServer.V3WS3File"); //$NON-NLS-1$
    eBayViewItemCmd = Externalized.getString("ebayServer.viewCmd"); //$NON-NLS-1$
    eBayParseItemURL = Externalized.getString("ebayServer.itemNumberMatch"); //$NON-NLS-1$
    eBayViewItemCGI = Externalized.getString("ebayServer.viewCGI"); //$NON-NLS-1$
    eBayViewDutchWinners = Externalized.getString("ebayServer.viewDutch"); //$NON-NLS-1$
    eBaySearchURL1 = Externalized.getString("ebayServer.searchURL1"); //$NON-NLS-1$
    eBaySearchURL2 = Externalized.getString("ebayServer.searchURL2"); //$NON-NLS-1$
    eBaySearchURLNoDesc = Externalized.getString("ebayServer.searchURLNoDesc"); //$NON-NLS-1$

    eBayBidItem = Externalized.getString("ebayServer.bidCmd"); //$NON-NLS-1$
    eBayPrequelTimeString = Externalized.getString("ebayServer.timePrequel1"); //$NON-NLS-1$
    eBayPrequelTimeString2 = Externalized.getString("ebayServer.timePrequel2"); //$NON-NLS-1$
    eBayDateFormat = Externalized.getString("ebayServer.dateFormat"); //$NON-NLS-1$
    eBayOfficialTimeFormat = Externalized.getString("ebayServer.officialTimeFormat"); //$NON-NLS-1$
    eBayAdultLoginPageTitle = Externalized.getString("ebayServer.adultPageTitle"); //$NON-NLS-1$
    eBayTitles = new String[]{ Externalized.getString("ebayServer.titleEbay"),
                     Externalized.getString("ebayServer.titleEbay2"),
                     Externalized.getString("ebayServer.titleMotors"),
                     Externalized.getString("ebayServer.titleMotors2"),
                     Externalized.getString("ebayServer.titleDisney"),
                     Externalized.getString("ebayServer.titleCollections") };
    eBayCurrentBid = Externalized.getString("ebayServer.currentBid"); //$NON-NLS-1$
    eBayLowestBid = Externalized.getString("ebayServer.lowestBid"); //$NON-NLS-1$
    eBayFirstBid = Externalized.getString("ebayServer.firstBid"); //$NON-NLS-1$
    eBayQuantity = Externalized.getString("ebayServer.quantity"); //$NON-NLS-1$
    eBayBidCount = Externalized.getString("ebayServer.bidCount"); //$NON-NLS-1$
    eBayStartTime = Externalized.getString("ebayServer.startTime"); //$NON-NLS-1$
    eBaySeller = Externalized.getString("ebayServer.seller"); //$NON-NLS-1$
    eBayHighBidder = Externalized.getString("ebayServer.highBidder"); //$NON-NLS-1$
    eBayBuyer = Externalized.getString("ebayServer.buyer"); //$NON-NLS-1$
    eBayShippingRegex = Externalized.getString("ebayServer.shipping"); //$NON-NLS-1$
    eBayInsurance = Externalized.getString("ebayServer.shippingInsurance"); //$NON-NLS-1$
    eBayBuyItNow = Externalized.getString("ebayServer.buyItNow"); //$NON-NLS-1$
    eBayItemEnded = Externalized.getString("ebayServer.ended"); //$NON-NLS-1$
    eBayTemporarilyUnavailable = Externalized.getString("ebayServer.unavailable"); //$NON-NLS-1$
    eBayPrice = Externalized.getString("ebayServer.price"); //$NON-NLS-1$
    eBayDescStart = Externalized.getString("ebayServer.description"); //$NON-NLS-1$
    eBayMotorsDescStart = Externalized.getString("ebayServer.descriptionMotors"); //$NON-NLS-1$
    eBayDescEnd = Externalized.getString("ebayServer.descriptionEnd"); //$NON-NLS-1$
    eBayPayInstructions = Externalized.getString("ebayServer.paymentInstructions"); //$NON-NLS-1$
    eBayClosedDescEnd = Externalized.getString("ebayServer.descriptionClosedEnd"); //$NON-NLS-1$
    eBayItemCGI = Externalized.getString("ebayServer.itemCGI"); //$NON-NLS-1$
    eBayItemNumber = Externalized.getString("ebayServer.itemNum"); //$NON-NLS-1$
  }

  private String[] site_choices = {
    "ebay.com", //$NON-NLS-1$
    "ebay.de", //$NON-NLS-1$
    "ebay.ca", //$NON-NLS-1$
    "ebay.co.uk", //$NON-NLS-1$
    "tw.ebay.com", //$NON-NLS-1$
    "es.ebay.com", //$NON-NLS-1$
    "ebay.fr", //$NON-NLS-1$
    "ebay.it", //$NON-NLS-1$
    "ebay.com.au", //$NON-NLS-1$
    "ebay.at", //$NON-NLS-1$
    "benl.ebay.be", //$NON-NLS-1$
    "ebay.nl", //$NON-NLS-1$
    "ebay.com.sg", //$NON-NLS-1$
    "ebaysweden.com", //$NON-NLS-1$
    "ebay.ch", //$NON-NLS-1$
    "befr.ebay.be",
    "ebay.ie"}; //$NON-NLS-1$

  /** @noinspection RedundantIfStatement*/
  public boolean doHandleThisSite(URL checkURL) {
    if(checkURL == null) return false;
    if( (checkURL.getHost().startsWith(eBayDetectionHost)) ) return true;
    if( (checkURL.getHost().startsWith(eBayTWDetectionHost)) ) return true;
    if( (checkURL.getHost().startsWith(eBayESDetectionHost)) ) return true;

    return false;
  }

  private static com.jbidwatcher.util.Currency[][] incrementTable = {
    { new com.jbidwatcher.util.Currency(   "$0.99"), new com.jbidwatcher.util.Currency( "$0.05") }, //$NON-NLS-1$ //$NON-NLS-2$
    { new com.jbidwatcher.util.Currency(   "$4.99"), new com.jbidwatcher.util.Currency( "$0.25") }, //$NON-NLS-1$ //$NON-NLS-2$
    { new com.jbidwatcher.util.Currency(  "$24.99"), new com.jbidwatcher.util.Currency( "$0.50") }, //$NON-NLS-1$ //$NON-NLS-2$
    { new com.jbidwatcher.util.Currency(  "$99.99"), new com.jbidwatcher.util.Currency( "$1.00") }, //$NON-NLS-1$ //$NON-NLS-2$
    { new com.jbidwatcher.util.Currency( "$249.99"), new com.jbidwatcher.util.Currency( "$2.50") }, //$NON-NLS-1$ //$NON-NLS-2$
    { new com.jbidwatcher.util.Currency( "$499.99"), new com.jbidwatcher.util.Currency( "$5.00") }, //$NON-NLS-1$ //$NON-NLS-2$
    { new com.jbidwatcher.util.Currency( "$999.99"), new com.jbidwatcher.util.Currency("$10.00") }, //$NON-NLS-1$ //$NON-NLS-2$
    { new com.jbidwatcher.util.Currency("$2499.99"), new com.jbidwatcher.util.Currency("$25.00") }, //$NON-NLS-1$ //$NON-NLS-2$
    { new com.jbidwatcher.util.Currency("$4999.99"), new com.jbidwatcher.util.Currency("$50.00") }, //$NON-NLS-1$ //$NON-NLS-2$
    { com.jbidwatcher.util.Currency.NoValue(), new com.jbidwatcher.util.Currency("$100.00") } }; //$NON-NLS-1$

  private static com.jbidwatcher.util.Currency[][] au_incrementTable = {
    { new com.jbidwatcher.util.Currency(   "AUD0.99"), new com.jbidwatcher.util.Currency( "AUD0.05") }, //$NON-NLS-1$ //$NON-NLS-2$
    { new com.jbidwatcher.util.Currency(   "AUD4.99"), new com.jbidwatcher.util.Currency( "AUD0.25") }, //$NON-NLS-1$ //$NON-NLS-2$
    { new com.jbidwatcher.util.Currency(  "AUD24.99"), new com.jbidwatcher.util.Currency( "AUD0.50") }, //$NON-NLS-1$ //$NON-NLS-2$
    { new com.jbidwatcher.util.Currency(  "AUD99.99"), new com.jbidwatcher.util.Currency( "AUD1.00") }, //$NON-NLS-1$ //$NON-NLS-2$
    { new com.jbidwatcher.util.Currency( "AUD249.99"), new com.jbidwatcher.util.Currency( "AUD2.50") }, //$NON-NLS-1$ //$NON-NLS-2$
    { new com.jbidwatcher.util.Currency( "AUD499.99"), new com.jbidwatcher.util.Currency( "AUD5.00") }, //$NON-NLS-1$ //$NON-NLS-2$
    { new com.jbidwatcher.util.Currency( "AUD999.99"), new com.jbidwatcher.util.Currency("AUD10.00") }, //$NON-NLS-1$ //$NON-NLS-2$
    { new com.jbidwatcher.util.Currency("AUD2499.99"), new com.jbidwatcher.util.Currency("AUD25.00") }, //$NON-NLS-1$ //$NON-NLS-2$
    { new com.jbidwatcher.util.Currency("AUD4999.99"), new com.jbidwatcher.util.Currency("AUD50.00") }, //$NON-NLS-1$ //$NON-NLS-2$
    { com.jbidwatcher.util.Currency.NoValue(), new com.jbidwatcher.util.Currency("AUD100.00") } }; //$NON-NLS-1$

  private static com.jbidwatcher.util.Currency[][] ca_incrementTable = {
    { new com.jbidwatcher.util.Currency( "CAD0.99"), new com.jbidwatcher.util.Currency( "CAD0.05") }, //$NON-NLS-1$ //$NON-NLS-2$
    { new com.jbidwatcher.util.Currency( "CAD4.99"), new com.jbidwatcher.util.Currency( "CAD0.25") }, //$NON-NLS-1$ //$NON-NLS-2$
    { new com.jbidwatcher.util.Currency("CAD24.99"), new com.jbidwatcher.util.Currency( "CAD0.50") }, //$NON-NLS-1$ //$NON-NLS-2$
    { new com.jbidwatcher.util.Currency("CAD99.99"), new com.jbidwatcher.util.Currency( "CAD1.00") }, //$NON-NLS-1$ //$NON-NLS-2$
    { com.jbidwatcher.util.Currency.NoValue(), new com.jbidwatcher.util.Currency("CAD2.50") } }; //$NON-NLS-1$

  private static com.jbidwatcher.util.Currency[][] uk_incrementTable = {
    { new com.jbidwatcher.util.Currency(   "GBP1.00"), new com.jbidwatcher.util.Currency( "GBP0.05") }, //$NON-NLS-1$ //$NON-NLS-2$
    { new com.jbidwatcher.util.Currency(   "GBP5.00"), new com.jbidwatcher.util.Currency( "GBP0.20") }, //$NON-NLS-1$ //$NON-NLS-2$
    { new com.jbidwatcher.util.Currency(  "GBP15.00"), new com.jbidwatcher.util.Currency( "GBP0.50") }, //$NON-NLS-1$ //$NON-NLS-2$
    { new com.jbidwatcher.util.Currency(  "GBP60.00"), new com.jbidwatcher.util.Currency( "GBP1.00") }, //$NON-NLS-1$ //$NON-NLS-2$
    { new com.jbidwatcher.util.Currency( "GBP150.00"), new com.jbidwatcher.util.Currency( "GBP2.00") }, //$NON-NLS-1$ //$NON-NLS-2$
    { new com.jbidwatcher.util.Currency( "GBP300.00"), new com.jbidwatcher.util.Currency( "GBP5.00") }, //$NON-NLS-1$ //$NON-NLS-2$
    { new com.jbidwatcher.util.Currency( "GBP600.00"), new com.jbidwatcher.util.Currency("GBP10.00") }, //$NON-NLS-1$ //$NON-NLS-2$
    { new com.jbidwatcher.util.Currency("GBP1500.00"), new com.jbidwatcher.util.Currency("GBP25.00") }, //$NON-NLS-1$ //$NON-NLS-2$
    { new com.jbidwatcher.util.Currency("GBP3000.00"), new com.jbidwatcher.util.Currency("GBP50.00") }, //$NON-NLS-1$ //$NON-NLS-2$
    { com.jbidwatcher.util.Currency.NoValue(), new com.jbidwatcher.util.Currency("GBP100.00") } }; //$NON-NLS-1$

  private static com.jbidwatcher.util.Currency[][] fr_incrementTable = {
    { new com.jbidwatcher.util.Currency(    "FRF4.99"), new com.jbidwatcher.util.Currency( "FRF0.25") }, //$NON-NLS-1$ //$NON-NLS-2$
    { new com.jbidwatcher.util.Currency(   "FRF24.99"), new com.jbidwatcher.util.Currency( "FRF0.50") }, //$NON-NLS-1$ //$NON-NLS-2$
    { new com.jbidwatcher.util.Currency(   "FRF99.99"), new com.jbidwatcher.util.Currency( "FRF1.00") }, //$NON-NLS-1$ //$NON-NLS-2$
    { new com.jbidwatcher.util.Currency(  "FRF249.99"), new com.jbidwatcher.util.Currency( "FRF2.50") }, //$NON-NLS-1$ //$NON-NLS-2$
    { new com.jbidwatcher.util.Currency(  "FRF499.99"), new com.jbidwatcher.util.Currency( "FRF5.00") }, //$NON-NLS-1$ //$NON-NLS-2$
    { new com.jbidwatcher.util.Currency(  "FRF999.99"), new com.jbidwatcher.util.Currency("FRF10.00") }, //$NON-NLS-1$ //$NON-NLS-2$
    { new com.jbidwatcher.util.Currency( "FRF2499.99"), new com.jbidwatcher.util.Currency("FRF25.00") }, //$NON-NLS-1$ //$NON-NLS-2$
    { new com.jbidwatcher.util.Currency( "FRF9999.99"), new com.jbidwatcher.util.Currency("FRF100.00") }, //$NON-NLS-1$ //$NON-NLS-2$
    { new com.jbidwatcher.util.Currency("FRF49999.99"), new com.jbidwatcher.util.Currency("FRF250.00") }, //$NON-NLS-1$ //$NON-NLS-2$
    { com.jbidwatcher.util.Currency.NoValue(), new com.jbidwatcher.util.Currency("FRF500.00") } }; //$NON-NLS-1$

  private static com.jbidwatcher.util.Currency[][] eu_incrementTable = {
    { new com.jbidwatcher.util.Currency(   "EUR49.99"), new com.jbidwatcher.util.Currency( "EUR0.50") }, //$NON-NLS-1$ //$NON-NLS-2$
    { new com.jbidwatcher.util.Currency(  "EUR499.99"), new com.jbidwatcher.util.Currency( "EUR1.00") }, //$NON-NLS-1$ //$NON-NLS-2$
    { new com.jbidwatcher.util.Currency(  "EUR999.99"), new com.jbidwatcher.util.Currency( "EUR5.00") }, //$NON-NLS-1$ //$NON-NLS-2$
    { new com.jbidwatcher.util.Currency( "EUR4999.99"), new com.jbidwatcher.util.Currency("EUR10.00") }, //$NON-NLS-1$ //$NON-NLS-2$
    { com.jbidwatcher.util.Currency.NoValue(), new com.jbidwatcher.util.Currency("EUR50.00") } }; //$NON-NLS-1$

  private static com.jbidwatcher.util.Currency[][] tw_incrementTable = {
    { new com.jbidwatcher.util.Currency(   "NTD500"), new com.jbidwatcher.util.Currency( "NTD15") }, //$NON-NLS-1$ //$NON-NLS-2$
    { new com.jbidwatcher.util.Currency(  "NTD2500"), new com.jbidwatcher.util.Currency( "NTD30") }, //$NON-NLS-1$ //$NON-NLS-2$
    { new com.jbidwatcher.util.Currency(  "NTD5000"), new com.jbidwatcher.util.Currency( "NTD50") }, //$NON-NLS-1$ //$NON-NLS-2$
    { new com.jbidwatcher.util.Currency( "NTD25000"), new com.jbidwatcher.util.Currency("NTD100") }, //$NON-NLS-1$ //$NON-NLS-2$
    { com.jbidwatcher.util.Currency.NoValue(), new com.jbidwatcher.util.Currency("NTD200") } }; //$NON-NLS-1$

  private static com.jbidwatcher.util.Currency[][] ch_incrementTable = {
    { new com.jbidwatcher.util.Currency(   "CHF49.99"), new com.jbidwatcher.util.Currency( "CHF0.50") }, //$NON-NLS-1$ //$NON-NLS-2$
    { new com.jbidwatcher.util.Currency(  "CHF499.99"), new com.jbidwatcher.util.Currency( "CHF1.00") }, //$NON-NLS-1$ //$NON-NLS-2$
    { new com.jbidwatcher.util.Currency(  "CHF999.99"), new com.jbidwatcher.util.Currency( "CHF5.00") }, //$NON-NLS-1$ //$NON-NLS-2$
    { new com.jbidwatcher.util.Currency( "CHF4999.99"), new com.jbidwatcher.util.Currency("CHF10.00") }, //$NON-NLS-1$ //$NON-NLS-2$
    { com.jbidwatcher.util.Currency.NoValue(), new com.jbidwatcher.util.Currency("CHF50.00") } }; //$NON-NLS-1$

  private static com.jbidwatcher.util.Currency zeroDollars = new com.jbidwatcher.util.Currency("$0.00"); //$NON-NLS-1$
  private static com.jbidwatcher.util.Currency zeroPounds = new com.jbidwatcher.util.Currency("GBP 0.00"); //$NON-NLS-1$
  private static com.jbidwatcher.util.Currency zeroFrancs = new com.jbidwatcher.util.Currency("FR 0.00"); //$NON-NLS-1$
  private static com.jbidwatcher.util.Currency zeroSwissFrancs = new com.jbidwatcher.util.Currency("CHF0.00"); //$NON-NLS-1$
  private static com.jbidwatcher.util.Currency zeroEuros = new com.jbidwatcher.util.Currency("EUR 0.00"); //$NON-NLS-1$
  private static com.jbidwatcher.util.Currency zeroAustralian = new com.jbidwatcher.util.Currency("AUD0.00"); //$NON-NLS-1$
  private static com.jbidwatcher.util.Currency zeroTaiwanese = new com.jbidwatcher.util.Currency("NTD0.00"); //$NON-NLS-1$
  private static com.jbidwatcher.util.Currency zeroCanadian = new com.jbidwatcher.util.Currency("CAD0.00"); //$NON-NLS-1$

  public com.jbidwatcher.util.Currency getMinimumBidIncrement(com.jbidwatcher.util.Currency currentBid, int bidCount) {
    com.jbidwatcher.util.Currency correctedValue = currentBid;
    com.jbidwatcher.util.Currency zeroIncrement = zeroDollars;
    com.jbidwatcher.util.Currency[][] rightTable;

    switch(currentBid.getCurrencyType()) {
      //  Default to USD, so we don't freak if we're passed a bad
      //  value.  We'll get the wrong answer, but we won't thrash.
      default:
        correctedValue = zeroDollars;
        rightTable = incrementTable;
        break;
      case com.jbidwatcher.util.Currency.US_DOLLAR:
        rightTable = incrementTable;
        break;
      case com.jbidwatcher.util.Currency.UK_POUND:
        rightTable = uk_incrementTable;
        zeroIncrement = zeroPounds;
        break;
      case com.jbidwatcher.util.Currency.FR_FRANC:
        rightTable = fr_incrementTable;
        zeroIncrement = zeroFrancs;
        break;
      case com.jbidwatcher.util.Currency.CH_FRANC:
        rightTable = ch_incrementTable;
        zeroIncrement = zeroSwissFrancs;
        break;
      case com.jbidwatcher.util.Currency.EURO:
        rightTable = eu_incrementTable;
        zeroIncrement = zeroEuros;
        break;
      case com.jbidwatcher.util.Currency.TW_DOLLAR:
        rightTable = tw_incrementTable;
        zeroIncrement = zeroTaiwanese;
        break;
      case com.jbidwatcher.util.Currency.CAN_DOLLAR:
        rightTable = ca_incrementTable;
        zeroIncrement = zeroCanadian;
        break;
      case com.jbidwatcher.util.Currency.AU_DOLLAR:
        rightTable = au_incrementTable;
        zeroIncrement = zeroAustralian;
        break;
    }

    if(bidCount == 0) return zeroIncrement;

    for (Currency[] aRightTable : rightTable) {
      Currency endValue = aRightTable[0];
      Currency incrementValue = aRightTable[1];

      //  Sentinel.  If we reach the end, return the max.
      if (endValue == null || endValue.isNull()) return incrementValue;

      try {
        //  If it's less than, or equal, to the end value than we use
        //  that increment amount.
        if (correctedValue.less(endValue)) return incrementValue;
        if (!endValue.less(correctedValue)) return incrementValue;
      } catch (Currency.CurrencyTypeException e) {
        /* Should never happen, since we've checked the currency already.  */
        ErrorManagement.handleException("Currency comparison threw a bad currency exception, which should be impossible.", e); //$NON-NLS-1$
      }
    }
    return null;
  }

  public void updateConfiguration() {
    sellerSearch = SearchManager.getInstance().buildSearch(System.currentTimeMillis(), "Seller", "My Selling Items", getUserId(), getName(), null, 0);
  }

  private class eBayTimeQueueManager extends TimeQueueManager {
    public long getCurrentTime() {
      return super.getCurrentTime() + getOfficialServerTimeDelta();
    }
  }


  /**
   * @brief Return the UI tab used to configure eBay-specific information.
   *
   *
   * @return - A new tab to be added to the configuration display.
   */
  public JConfigTab getConfigurationTab() {
    //  Always return a new one, to fix a problem on first startup.
    return new JConfigEbayTab();
  }

  /**
   * @brief Build a menu that can be added to the JBidwatcher standard
   * menu, to do eBay-specific things.
   *
   */
  public void establishMenu() {
    ebayServerMenu esm = new ebayServerMenu(eBayDisplayName, 'b');
    esm.initialize();
  }

  /**
   * @brief Determine if an identifier looks like an eBay ID.
   *
   * For now, this just determines that it's pure numbers.  It should
   * be 8-11 digits long, also, but we're not that specific right now.
   *
   * @param auctionId - The auction id to test for 'eBay compatibility'.
   *
   * @return - true if the item looks like it's an eBay id, false otherwise.
   */
  public boolean checkIfIdentifierIsHandled(String auctionId) {
    return auctionId != null && isNumberOnly(auctionId);
  }

  /**
   * @brief Very simplistic check to see if the current user is the
   * high bidder on a Dutch item.
   *
   * This only works, really, on closed items, I believe.  It shows
   * you as a 'winner' always, otherwise.
   *
   * @param inAE - The auction entry to check.
   *
   * @return - true if the user is one of the high bidders on a dutch item, false otherwise.
   */
  public boolean isHighDutch(AuctionEntry inAE) {
    String dutchWinners = eBayProtocol + eBayDutchRequest + eBayWS3File + eBayViewDutchWinners + inAE.getIdentifier();
    CookieJar cj = getNecessaryCookie(false);
    String userCookie = null;
    if (cj != null) userCookie = cj.toString();

    JHTML htmlDocument = new JHTML(dutchWinners, userCookie, this);
    String matchedName = null;
    if(htmlDocument.isLoaded()) {
      matchedName = htmlDocument.getNextContentAfterContent(getUserId());
    }

    return matchedName != null;
  }

  public void updateHighBid(AuctionEntry ae) {
    String bidHistory = eBayProtocol + eBayBidHost + eBayV3File + Externalized.getString("ebayServer.viewBidsCGI") + ae.getIdentifier();
    CookieJar cj = getNecessaryCookie(false);
    String userCookie = null;
    if (cj != null) userCookie = cj.toString();
    JHTML htmlDocument = new JHTML(bidHistory, userCookie, this);

    if(htmlDocument.isLoaded()) {
      String newCurrency = htmlDocument.getNextContentAfterContent(getUserId());

      //  If we couldn't find anything, ditch, because we're probably wrong that the user is in the high bid table.
      if(newCurrency == null) return;

      //  If we're dealing with feedback, skip it.
      if(isNumberOnly(newCurrency)) newCurrency = htmlDocument.getNextContent(); //  Skip feedback number
      int bidCount = 1;

      //  Check the next two columns for acceptable values.
      for(int i=0; i<2; i++) {
        com.jbidwatcher.util.Currency highBid = com.jbidwatcher.util.Currency.getCurrency(newCurrency);
        if(!highBid.isNull()) {
          if(ae.isDutch()) {
            String quant = htmlDocument.getNextContent();
            try {
              bidCount = Integer.parseInt(quant);
            } catch(NumberFormatException ignored) {
              //  We don't care what happened, that it's not a number means the bid count is 1.
              bidCount = 1;
            }
          }
          try {
            if(!ae.isBidOn() || ae.getBid().less(highBid)) {
              ae.setBid(highBid);
              ae.setBidQuantity(bidCount);
            }
          } catch(com.jbidwatcher.util.Currency.CurrencyTypeException cte) {
            //  Bad things happen here.  Ignore it for now.
          }
        }
        newCurrency = htmlDocument.getNextContent();
      }
    }
  }

  /**
   * @brief Given a server name, determine if we would normally handle requests for items on that server.
   *
   * @param serverName - The server name to check.
   *
   * @return - true if it is a server this class should handle, false otherwise.
   */
  public boolean checkIfSiteNameHandled(String serverName) {
    return(serverName.equalsIgnoreCase(eBayServerName));
  }

  /**
   * @brief Load a URL in, find all hrefs on the page that point to an
   * auction item, and load them into the program.
   *
   * @param searcher - The Searcher object that contains the URL to load and search for items in.
   * @param label - What 'group' to label items retrieved this way as.
   */
  private void loadAllFromURLString(Object searcher, String label) {
    String urlStr = ((Searcher)searcher).getSearch();
    MQFactory.getConcrete("Swing").enqueue("Loading from URL " + urlStr); //$NON-NLS-1$ //$NON-NLS-2$

    //noinspection MismatchedQueryAndUpdateOfCollection
    EbayAuctionURLPager pager = new EbayAuctionURLPager(urlStr, this, this);
    int results = 0;

    ListIterator li = pager.listIterator();

    while(li.hasNext()) {
      MQFactory.getConcrete("Swing").enqueue("Loading page " + li.nextIndex() + "/" + pager.size() + " from URL " + urlStr); //$NON-NLS-1$ //$NON-NLS-2$

    	JHTML htmlDocument = (JHTML)li.next();
    	if(htmlDocument != null) {
    		results += addAllItemsOnPage(htmlDocument, label, !((Searcher)searcher).shouldSkipDeleted());
    	}
    }

    if(results == 0) {
      MQFactory.getConcrete("Swing").enqueue("Failed to load from URL " + urlStr); //$NON-NLS-1$ //$NON-NLS-2$
    } else {
      MQFactory.getConcrete("Swing").enqueue("Done loading from URL " + urlStr); //$NON-NLS-1$ //$NON-NLS-2$
    }
  }

  /**
   * @brief Given a search string, send it to eBay's search, and gather up the results.
   *
   * @param searcher - The Searcher object containing the string to search for.
   * @param label - What 'group' to label items retrieved this way as.
   * @param title_only - Should the search focus on the titles only, or titles and descriptions?
   */
  private void loadSearchString(Object searcher, String label, boolean title_only) {
    String search = ((Searcher)searcher).getSearch();
    //  This should be encode(search, "UTF-8"); but that's a 1.4+ feature!
    //  Ignore the deprecation warning for this one.
    String encodedSearch;
    try {
      encodedSearch = URLEncoder.encode(search, "UTF-8");
    } catch(UnsupportedEncodingException ignored) {
      encodedSearch = null;
      ErrorManagement.logMessage("Failed to search because of encoding transformation failure.");
    }
    int allResults = 0;

    if(encodedSearch != null) {
      MQFactory.getConcrete("Swing").enqueue("Searching for: " + search); //$NON-NLS-1$ //$NON-NLS-2$
      String sacur = "";

      String currency = ((Searcher)searcher).getCurrency();
      if(currency != null) sacur = "&sacur=" + currency;

      String fullSearch;

      if (title_only) {
        fullSearch = eBaySearchURL1 + encodedSearch + sacur + eBaySearchURLNoDesc;
      } else {
        fullSearch = eBaySearchURL1 + encodedSearch + sacur + eBaySearchURL2;
      }
      int skipCount = 0;
      boolean done;

      do {
        done = true;

        CookieJar cj = getNecessaryCookie(false);
        String userCookie = null;
        if (cj != null) userCookie = cj.toString();
        JHTML htmlDocument = new JHTML(fullSearch, userCookie, this);
        if(htmlDocument.isLoaded()) {
          int pageResults = addAllItemsOnPage(htmlDocument, label, !((Searcher)searcher).shouldSkipDeleted());
          if(pageResults != 0) {
            if(pageResults >= ITEMS_PER_PAGE) {
              skipCount += ITEMS_PER_PAGE;
              fullSearch = new StringBuffer(eBaySearchURL1).append(encodedSearch).append(sacur).append(title_only?eBaySearchURLNoDesc:eBaySearchURL2).append("&skip=").append(skipCount).toString(); //$NON-NLS-1$
              done = false;
            }

            allResults += pageResults;
          }
        }
      } while(!done);
    }

    if(allResults == 0) {
      MQFactory.getConcrete("Swing").enqueue("No results found for search: " + search); //$NON-NLS-1$ //$NON-NLS-2$
    } else {
      MQFactory.getConcrete("Swing").enqueue("Done searching for: " + search); //$NON-NLS-1$ //$NON-NLS-2$
    }
  }

  /**
   * @brief Process an action, based on messages passed through our internal queues.
   *
   * This function is required, as an implementor of MessageQueue.Listener.
   *
   */
  public void messageAction(Object deQ) {
    AuctionQObject ac = (AuctionQObject)deQ; //$NON-NLS-1$
    String failString = null;
    boolean defaultUser = getUserId().equals("default"); //$NON-NLS-1$

    /**
     * Just load all listings on a specific URL.
     */
    switch(ac.getCommand()) {
      case AuctionQObject.LOAD_STRINGS:
        loadStrings();
        return;
      case AuctionQObject.LOAD_URL:
        loadAllFromURLString(ac.getData(), ac.getLabel());
        return;
      case AuctionQObject.LOAD_SEARCH:
        /**
         * Check for searches, and execute one if that's what is requested.
         */
        loadSearchString(ac.getData(), ac.getLabel(), false);
        return;
      case AuctionQObject.LOAD_TITLE:
        /**
         * Check for searches, and execute one if that's what is requested.
         */
        loadSearchString(ac.getData(), ac.getLabel(), true);
        return;
      case AuctionQObject.LOAD_SELLER:
        /**
         * Load all items being sold by a given seller.
         */
        doGetSelling(ac.getData(), ac.getLabel());
        return;
      case AuctionQObject.LOAD_MYITEMS:
        if(defaultUser) {
          failString = Externalized.getString("ebayServer.cantLoadWithoutUsername1") + getName() + Externalized.getString("ebayServer.cantLoadWithoutUsername2"); //$NON-NLS-1$ //$NON-NLS-2$
        } else {
          doMyEbaySynchronize(ac.getLabel());
          return;
        }
        break;
      case AuctionQObject.CANCEL_SNIPE:
        AuctionEntry snipeCancel = (AuctionEntry)ac.getData();
        String id = snipeCancel.getIdentifier();
        AuctionQObject cancellable = snipeMap.get(id);

        _etqm.erase(cancellable);
        snipeMap.remove(id);
        return;
      case AuctionQObject.SET_SNIPE:
        AuctionEntry snipeOn = (AuctionEntry)ac.getData();
        AuctionQObject currentlyExists = snipeMap.get(snipeOn.getIdentifier());
        //  If we already have a snipe set for it, first cancel the old one, and then set up the new.
        if(currentlyExists != null) {
          _etqm.erase(currentlyExists);
          snipeMap.remove(snipeOn.getIdentifier());
        }

        long two_minutes = Constants.ONE_MINUTE*2;
        AuctionQObject payload = new AuctionQObject(AuctionQObject.SNIPE, new Snipe(snipeOn), null);

        _etqm.add(payload, "snipes", (snipeOn.getEndDate().getTime()-snipeOn.getSnipeTime())-two_minutes);
        _etqm.add(payload, "snipes", (snipeOn.getEndDate().getTime()-snipeOn.getSnipeTime()));
        snipeMap.put(snipeOn.getIdentifier(), payload);
        return;
      case AuctionQObject.BID:
        AuctionAction ab = (AuctionAction)ac.getData();
        String bidResultString = ab.activate();
        String configBidMsg;

        if(ab.isSuccessful()) {
          configBidMsg = "prompt.hide_bidalert";
        } else {
          configBidMsg = "prompt.hide_bidfailalert";
        }

        MQFactory.getConcrete("Swing").enqueue("IGNORE " + configBidMsg + ' ' + bidResultString);

        AuctionsManager.getInstance().changed();
        return;
      default:
        //  It's okay if we don't recognize it.
    }

    if(ac.getData() != null) {
      /**
       * This calls back to here, by adding a message onto the queue,
       * but it will update the 'last run' time for the 'My eBay' search.
       */
      if(ac.getData().equals("Get My eBay Items")) { //$NON-NLS-1$
        if(_my_ebay != null) {
          _my_ebay.execute();
          return;
        }
        /**
         * From here on, everything requires a 'real' user id.  failString
         * gets set if it's not, as a custom message for the user based on
         * the action they are trying to do.
         */
        if(defaultUser) {
          failString = Externalized.getString("ebayServer.cantLoadWithoutUsername1") + getName() + Externalized.getString("ebayServer.cantLoadWithoutUsername2"); //$NON-NLS-1$ //$NON-NLS-2$
        } else {
          doMyEbaySynchronize(null);
          return;
        }
      }

      /**
       * Get items this user is selling.
       */
      if(ac.getData().equals("Get Selling Items")) { //$NON-NLS-1$
        if(defaultUser) {
          failString = Externalized.getString("ebayServer.cantLoadSellerWithoutUser1") + getName() + Externalized.getString("ebayServer.cantLoadWithoutUsername2"); //$NON-NLS-1$ //$NON-NLS-2$
        } else {
          if(sellerSearch == null) updateConfiguration();
          if(sellerSearch != null) sellerSearch.execute();
          return;
        }
      }

      /**
       * Update the login cookie, that contains session and adult information, for example.
       */
      if(ac.getData().equals("Update login cookie")) { //$NON-NLS-1$
        if(defaultUser) {
          failString = Externalized.getString("ebayServer.cantUpdateCookieWithoutUser1") + getName() + Externalized.getString("ebayServer.cantLoadWithoutUsername2"); //$NON-NLS-1$ //$NON-NLS-2$
        } else {
          _signinCookie = null;
          getNecessaryCookie(true);
          return;
        }
      }

      if(ac.getData().equals("[Dump eBay activity queue]")) {
        _etqm.dumpQueue();
        return;
      }
    }

    /**
     * If we've made a failure string, and we're using the default
     * user, then display the error, otherwise indicate that we got an
     * unexpected command.
     */
    if(failString != null && failString.length() != 0 && defaultUser) { //$NON-NLS-1$
      JOptionPane.showMessageDialog(null, failString, "No auction account error", JOptionPane.PLAIN_MESSAGE); //$NON-NLS-1$
    } else {
      if (ac.getData() instanceof String) {
        String acData = (String) ac.getData();
        ErrorManagement.logMessage("Dequeue'd unexpected command or fell through: " + ac.getCommand() + ':' + acData); //$NON-NLS-1$ //$NON-NLS-2$
      } else {
        //noinspection ObjectToString
        ErrorManagement.logMessage("Can't recognize ebay-queued data: " + ac.getData());
      }
    }
  }

  /**
   * @brief Constructor for the eBay server object.
   *
   * It's not a terribly good idea to have multiple of these, right
   * now, but it is probably not broken.  -- mrs: 18-September-2003 15:08
   */
  public ebayServer() {
    siteId = "ebay"; //$NON-NLS-1$

    /**
     * Build a simple hashtable of results that bidding might get.
     * Not the greatest solution, but it's working okay.  A better one
     * would be great.
     */
    if(_resultHash == null) {
      _resultHash = new HashMap<String, Integer>();
      _resultHash.put("you are not permitted to bid on their listings.", BID_ERROR_BANNED); //$NON-NLS-1$
      _resultHash.put("the item is no longer available because the auction has ended.", BID_ERROR_ENDED); //$NON-NLS-1$
      _resultHash.put("cannot proceed", BID_ERROR_CANNOT); //$NON-NLS-1$
      _resultHash.put("problem with bid amount", BID_ERROR_AMOUNT); //$NON-NLS-1$
      _resultHash.put("your bid must be at least ", BID_ERROR_TOO_LOW); //$NON-NLS-1$
      _resultHash.put("you have been outbid by another bidder", BID_ERROR_OUTBID); //$NON-NLS-1$
      _resultHash.put("your bid is confirmed!", BID_DUTCH_CONFIRMED); //$NON-NLS-1$
      _resultHash.put("you are bidding on this multiple item auction", BID_DUTCH_CONFIRMED); //$NON-NLS-1$
      _resultHash.put("you are the high bidder on all items you bid on", BID_DUTCH_CONFIRMED); //$NON-NLS-1$
      _resultHash.put("you are the current high bidder", BID_WINNING); //$NON-NLS-1$
      _resultHash.put("you purchased the item", BID_WINNING); //$NON-NLS-1$
      _resultHash.put("the reserve price has not been met", BID_ERROR_RESERVE_NOT_MET); //$NON-NLS-1$
      _resultHash.put("your new total must be higher than your current total", BID_ERROR_TOO_LOW_SELF); //$NON-NLS-1$
      _resultHash.put("this exceeds or is equal to your current bid", BID_ERROR_TOO_LOW_SELF); //$NON-NLS-1$
      _resultHash.put("you bought this item", BID_BOUGHT_ITEM); //$NON-NLS-1$
      _resultHash.put("you committed to buy", BID_BOUGHT_ITEM); //$NON-NLS-1$
      _resultHash.put("congratulations! you won!", BID_BOUGHT_ITEM); //$NON-NLS-1$
      _resultHash.put("account suspended", BID_ERROR_ACCOUNT_SUSPENDED); //$NON-NLS-1$
      _resultHash.put("to enter a higher maximum bid, please enter", BID_ERROR_TOO_LOW_SELF); //$NON-NLS-1$
      _resultHash.put("you are registered in a country to which the seller doesn.t ship.", BID_ERROR_WONT_SHIP); //$NON-NLS-1$
      _resultHash.put("this seller has set buyer requirements for this item and only sells to buyers who meet those requirements.", BID_ERROR_REQUIREMENTS_NOT_MET); //$NON-NLS-1$
      //      _resultHash.put("You are the current high bidder", new Integer(BID_SELFWIN));
    }

    //"If you want to submit another bid, your new total must be higher than your current total";
    StringBuffer superRegex = new StringBuffer("(");
    Iterator<String> it = _resultHash.keySet().iterator();
    while (it.hasNext()) {
      String key = it.next();
      superRegex.append(key);
      if(it.hasNext()) {
        superRegex.append('|');
      } else {
        superRegex.append(')');
      }
    }
    mBidResultRegex = "(?i)" + superRegex.toString();
    mFindBidResult = Pattern.compile(mBidResultRegex);
    _resultHash.put("sign in", BID_ERROR_CANT_SIGN_IN);

    _etqm = new eBayTimeQueueManager();
    eQueue = new TimerHandler(_etqm);
    eQueue.setName("eBay SuperQueue");
    //noinspection CallToThreadStartDuringObjectConstruction
    eQueue.start();

    MQFactory.getConcrete("snipes").registerListener(new SnipeListener());
    MQFactory.getConcrete("ebay").registerListener(this); //$NON-NLS-1$

    JConfig.registerListener(this);
  }

  private static final String srcMatch = "(?i)src=\"([^\"]*?)\"";
  private static Pattern srcPat = Pattern.compile(srcMatch);

  private static final String dateMatch = "(?i)(Ends|end.time).([A-Za-z]+(.[0-9]+)+.[A-Z]+)";
  private static Pattern datePat = Pattern.compile(dateMatch);

  /**
   * @brief Go to eBay and get their official time page, parse it, and
   * mark the difference between that time and our current time
   * internally, so we know how far off this machine's time is.
   *
   * @return - An object containing eBay's date, or null if we fail to
   * load or parse the 'official time' page properly.
   */
  protected Date getOfficialTime() {
    Auctions.startBlocking();
    String timeRequest = eBayProtocol + eBayHost + eBayFile + Externalized.getString("ebayServer.timeCmd"); //$NON-NLS-1$

//  Getting the necessary cookie here causes intense slowdown which fudges the time, badly.
//    htmlDocument = new JHTML(timeRequest, getNecessaryCookie(false).toString(), this);
    JHTML htmlDocument = new JHTML(timeRequest, null, this);
    Date result = null;

    String pageStep = htmlDocument.getNextContent();
    while(result == null && pageStep != null) {
      if(pageStep.equals(eBayPrequelTimeString) || pageStep.equals(eBayPrequelTimeString2)) {
        result = figureDate(htmlDocument.getNextContent(), eBayOfficialTimeFormat, false);
      }
      pageStep = htmlDocument.getNextContent();
    }

    Auctions.endBlocking();
    return result;
  }

  /**
   * @brief Given a standard URL, strip it apart, and find the items
   * identifier from the standard eBay 'ViewItem' URL.
   *
   * @param urlStyle - The string to parse the identifier out of.
   *
   * @return - The identifier for the auction referenced by the URL
   * string passed in, or null if no identifier could be found.
   */
  public String extractIdentifierFromURLString(String urlStyle) {
    Pattern url = Pattern.compile(eBayParseItemURL);
    Matcher urlMatch = url.matcher(urlStyle);
    if(urlMatch.find()) {
        String itemNum = urlMatch.group(2);
        if(isNumberOnly(itemNum)) return itemNum;
    }
    URL siteAddr = getURLFromString(urlStyle);

    if(siteAddr != null) {
      String lastPart = siteAddr.toString();
      if(lastPart.indexOf(eBayViewItemCmd) != -1) {
        int index = lastPart.indexOf(eBayViewItemCGI);
        if(index != -1) {
          String aucId = lastPart.substring(index+eBayViewItemCGI.length());

          if (aucId.indexOf("&") != -1) { //$NON-NLS-1$
            aucId = aucId.substring(0, aucId.indexOf("&")); //$NON-NLS-1$
          }

          if (aucId.indexOf("#") != -1) { //$NON-NLS-1$
            aucId = aucId.substring(0, aucId.indexOf("#")); //$NON-NLS-1$
          }

          return(aucId);
        }
      }
    }

    ErrorManagement.logDebug("extractIdentifierFromURLString failed."); //$NON-NLS-1$
    return null;
  }

  /**
   * @brief Given a site-dependant item ID, get the string-form URL for that item.
   *
   * @param itemID - The item ID to get the URL for.
   *
   * @return - The real URL pointing to the item referenced by the passed in ID.
   */
  public String getStringURLFromItem(String itemID) {
    return eBayProtocol + eBayViewHost + eBayFile + '?' + eBayViewItemCmd + eBayViewItemCGI + itemID;
  }

  /**
   * @brief Get a string form URL that the user can browse to.
   *
   * This involves going to the users preferred country site.
   *
   * @param itemID - The item to browse w/r/t.
   *
   * @return - A string containing the way to browse to the users preferred international site.
   */
  public String getBrowsableURLFromItem(String itemID) {
    int browse_site = Integer.parseInt(JConfig.queryConfiguration(siteId + ".browse.site", "0")); //$NON-NLS-1$ //$NON-NLS-2$

    return eBayProtocol + eBayBrowseHost + site_choices[browse_site] + eBayFile + '?' + eBayViewItemCmd + eBayViewItemCGI + itemID;
  }

  /**
   * @brief Given a site-dependant item ID, get the URL for that item.
   *
   * @param itemID - The eBay item ID to get a net.URL for.
   *
   * @return - a URL to use to pull that item.
   */
  protected URL getURLFromItem(String itemID) {
    return(getURLFromString(getStringURLFromItem(itemID)));
  }

  /**
   * @brief Factory for generating an auction that contains the rules specific to eBay.
   *
   * @return - An object that can be used as an AuctionInfo object.
   */
  public SpecificAuction getNewSpecificAuction() {
    return new ebayAuction();
  }

  public JHTML.Form getBidForm(CookieJar cj, AuctionEntry inEntry, com.jbidwatcher.util.Currency inCurr, int inQuant) throws BadBidException {
    String bidRequest = eBayProtocol + eBayBidHost + eBayV3File;
    String bidInfo;
    if(inEntry.isDutch()) {
      bidInfo = eBayBidItem + "&co_partnerid=" + eBayItemCGI + inEntry.getIdentifier() +
                "&fb=2" + Externalized.getString("ebayServer.quantCGI") + inQuant +
                Externalized.getString("ebayServer.bidCGI") + inCurr.getValue(); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
    } else {
      bidInfo = eBayBidItem + "&co_partnerid=" + eBayItemCGI + inEntry.getIdentifier() + "&fb=2" +
                Externalized.getString("ebayServer.bidCGI") + inCurr.getValue(); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
    }
    StringBuffer loadedPage = null;
    JHTML htmlDocument = null;

    try {
      String pageName = bidRequest + '?' + bidInfo;
      boolean checked_signon = false;
      boolean checked_reminder = false;
      boolean done = false;
      boolean post = false;
      while (!done) {
        done = true;

        if(JConfig.debugging) inEntry.setLastStatus("Loading bid request...");
        URLConnection huc = cj.getAllCookiesFromPage(pageName, null, post);
        post = false;
        //  We failed to load, entirely.  Punt.
        if (huc == null) return null;

        loadedPage = Http.receivePage(huc);
        //  We failed to load.  Punt.
        if (loadedPage == null) return null;

        htmlDocument = new JHTML(loadedPage);
        JHTML.Form bidForm = htmlDocument.getFormWithInput("key");
        if(bidForm != null) {
          if(JConfig.debugging) inEntry.setLastStatus("Done loading bid request, got form...");
          return bidForm;
        }

        if(!checked_signon) {
          checked_signon = true;
          String signOn = htmlDocument.getFirstContent();
          if (signOn != null) {
            ErrorManagement.logDebug("Checking sign in as bid key load failed!");
            if (signOn.equalsIgnoreCase("Sign In")) {
              //  This means we somehow failed to keep the login in place.  Bad news, in the middle of a snipe.
              ErrorManagement.logDebug("Being prompted again for sign in, retrying.");
              if(JConfig.debugging) inEntry.setLastStatus("Not done loading bid request, got re-login request...");
              _signinCookie = null;
              getNecessaryCookie(true);
              if(JConfig.debugging) inEntry.setLastStatus("Done re-logging in, retrying load bid request.");
              done = false;
            }
          }
        }

        if(!checked_reminder) {
          if(htmlDocument.grep("Buying.Reminder") != null) {
            JHTML.Form continueForm = htmlDocument.getFormWithInput("firedFilterId");
            if(continueForm != null) {
              inEntry.setLastStatus("Trying to 'continue' for the actual bid.");
              pageName = continueForm.getCGI();
              pageName = pageName.replaceFirst("%[A-F][A-F0-9]%A0", "%A0");
              post = false;
            }
            checked_reminder = true;
          }
        }
      }
    } catch (IOException e) {
      ErrorManagement.handleException("Failure to get the bid key!  BID FAILURE!", e); //$NON-NLS-1$
    }

    if(htmlDocument != null) {
      String signOn = htmlDocument.getFirstContent();
      if(signOn != null && signOn.equalsIgnoreCase("Sign In")) throw new BadBidException("sign in", BID_ERROR_CANT_SIGN_IN);
      String errMsg = htmlDocument.grep(mBidResultRegex);
      if(errMsg != null) {
        Matcher bidMatch = mFindBidResult.matcher(errMsg);
        bidMatch.find();
        String matched_error = bidMatch.group().toLowerCase();
        throw new BadBidException(matched_error, _resultHash.get(matched_error));
      }
    }

    if(JConfig.debugging) inEntry.setLastStatus("Failed to bid. 'Show Last Error' from context menu to see the failure page from the bid attempt.");
    inEntry.setErrorPage(loadedPage);

    //  We don't recognize this error.  Damn.  Log it and freak.
    ErrorManagement.logFile(bidInfo, loadedPage);
    return null;
  }

  /**
   * @brief Returns the amount of time it takes to retrieve a page
   * from the auction server.
   *
   * @return The amount of milliseconds it takes to get a simple page
   * from the auction server.
   */
  public long getSnipePadding() {
    if(JBConfig.doAffiliate(0)) {
      return 3;
    }
    return 1;
  }

  public StringBuffer getAuctionViaAffiliate(CookieJar cj, AuctionEntry ae, String id) throws CookieJar.CookieException {
    long end_time = 1;
    if(ae != null) {
      Date end = ae.getEndDate();
      if(end != null) end_time = end.getTime();
    }
    StringBuffer sb = null;
    if(JBConfig.doAffiliate(end_time)) {
      sb = AffiliateRetrieve.getAuctionViaAffiliate(cj, id);
    }

    if(sb == null || sb.indexOf("eBay item") == -1) {
      try {
        sb = getAuction(getURLFromItem(id));
      } catch (FileNotFoundException ignored) {
        sb = null;
      }
    }

    return sb;
  }

  public int buy(AuctionEntry ae, int quantity) {
    String buyRequest = "http://offer.ebay.com/ws/eBayISAPI.dll?MfcISAPICommand=BinConfirm&fb=1&co_partnerid=&item=" + ae.getIdentifier() + "&quantity=" + quantity;

    //  This updates the cookies with the affiliate information, if it's not a test auction.
    if(ae.getTitle().toLowerCase().indexOf("test") == -1) {
      if(JBConfig.doAffiliate(ae.getEndDate().getTime())) {
        try {
          getAuctionViaAffiliate(getNecessaryCookie(false), ae, ae.getIdentifier());
        } catch (CookieJar.CookieException ignore) {
          //  Ignore, it doesn't matter for this call.
        }
      }
    }

    StringBuffer sb;

    try {
      sb = getNecessaryCookie(false).getAllCookiesAndPage(buyRequest, null, false);
      JHTML doBuy = new JHTML(sb);
      JHTML.Form buyForm = doBuy.getFormWithInput("uiid");

      if (buyForm != null) {
        buyForm.delInput("BIN_button");
        CookieJar cj = getNecessaryCookie(false);
        StringBuffer loadedPage = cj.getAllCookiesAndPage(buyForm.getCGI(), buyRequest, false);
        if (loadedPage == null) return BID_ERROR_CONNECTION;
        return handlePostBidBuyPage(cj, loadedPage, buyForm, ae);
      }
    } catch (CookieJar.CookieException ignored) {
      return BID_ERROR_CONNECTION;
    } catch (UnsupportedEncodingException uee) {
      ErrorManagement.handleException("UTF-8 not supported locally, can't URLEncode buy form.", uee);
      return BID_ERROR_CONNECTION;
    }

    ae.setErrorPage(sb);
    return BID_ERROR_UNKNOWN;
  }

  /**
   * @brief Perform the entire bidding process on an item.
   *
   * @param inEntry - The item to bid on.
   * @param inBid - The amount to bid.
   * @param inQuantity - The number of items to bid on.
   *
   * @return - A bid response code, or BID_ERROR_UNKNOWN if we can't
   * figure out what happened.
   */
  public int bid(AuctionEntry inEntry, com.jbidwatcher.util.Currency inBid, int inQuantity) {
    Auctions.startBlocking();
    if(JConfig.queryConfiguration("sound.enable", "false").equals("true")) MQFactory.getConcrete("audio").enqueue("/audio/bid.mp3");

    try {
      //  If it's not closing within the next minute, then go ahead and try for the affiliate mode.
      if(inEntry.getEndDate().getTime() > (System.currentTimeMillis() + Constants.ONE_MINUTE)) {
        safeGetAffiliate(getNecessaryCookie(false), inEntry);
      }
    } catch (CookieJar.CookieException ignore) {
      //  We don't care that much about connection refused in this case.
    }
    JHTML.Form bidForm;

    try {
      bidForm = getBidForm(getNecessaryCookie(false), inEntry, inBid, inQuantity);
    } catch(BadBidException bbe) {
      Auctions.endBlocking();
      return bbe.getResult();
    }

    if (bidForm != null) {
      int rval = placeFinalBid(getNecessaryCookie(false), bidForm, inEntry, inBid, inQuantity);
      Auctions.endBlocking();
      return rval;
    }
    ErrorManagement.logMessage("Bad/nonexistent key read in bid, or connection failure!"); //$NON-NLS-1$

    Auctions.endBlocking();
    return BID_ERROR_UNKNOWN;
  }

  public int placeFinalBid(CookieJar cj, JHTML.Form bidForm, AuctionEntry inEntry, com.jbidwatcher.util.Currency inBid, int inQuantity) {
    String bidRequest = eBayProtocol + eBayBidHost + eBayV3File;
    String bidInfo = eBayBidItem + eBayItemCGI + inEntry.getIdentifier() +
        Externalized.getString("ebayServer.quantCGI") + inQuantity +
        Externalized.getString("ebayServer.bidCGI") + inBid.getValue(); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
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
      return BID_ERROR_CONNECTION;
    }

    if (loadedPage == null) {
      return BID_ERROR_CONNECTION;
    }
    return handlePostBidBuyPage(cj, loadedPage, bidForm, inEntry);
  }

  public void safeGetAffiliate(CookieJar cj, AuctionEntry inEntry) throws CookieJar.CookieException {
    //  This updates the cookies with the affiliate information, if it's not a test auction.
    if(inEntry.getTitle().toLowerCase().indexOf("test") == -1) {
      if(JBConfig.doAffiliate(inEntry.getEndDate().getTime())) {
        if(JConfig.debugging) inEntry.setLastStatus("Loading item...");
        getAuctionViaAffiliate(cj, inEntry, inEntry.getIdentifier());
        if(JConfig.debugging) inEntry.setLastStatus("Done loading item...");
      }
    }
  }

  private int handlePostBidBuyPage(CookieJar cj, StringBuffer loadedPage, JHTML.Form bidForm, AuctionEntry inEntry) {
    if(JConfig.debugging) inEntry.setLastStatus("Loading post-bid data.");
    JHTML htmlDocument = new JHTML(loadedPage);

    if(htmlDocument.grep("Buying.Reminder") != null) {
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
          if (huc == null) return BID_ERROR_CONNECTION;

          loadedPage = Http.receivePage(huc);
          //  We failed to load.  Punt.
          if (loadedPage == null) return BID_ERROR_CONNECTION;

          htmlDocument = new JHTML(loadedPage);
        } catch(Exception ignored) {
          return BID_ERROR_CONNECTION;
        }
      }
    }

    String errMsg = htmlDocument.grep(mBidResultRegex);
    if (errMsg != null) {
      Matcher bidMatch = mFindBidResult.matcher(errMsg);
      bidMatch.find();
      String matched_error = bidMatch.group().toLowerCase();
      Integer bidResult = _resultHash.get(matched_error);

      if(inEntry.getTitle().toLowerCase().indexOf("test") == -1) {
        if(JBConfig.doAffiliate(inEntry.getEndDate().getTime())) {
          List<String> images = htmlDocument.getAllImages();
          for (String tag : images) {
            Matcher tagMatch = srcPat.matcher(tag);
            if (tagMatch.find()) {
              int retry = 2;
              do {
                StringBuffer result = null;
                try {
                  result = getNecessaryCookie(false).getAllCookiesAndPage(tagMatch.group(1), "http://offer.ebay.com/ws/eBayISAPI.dll", false);
                } catch (CookieJar.CookieException ignored) {
                  //  Ignore connection refused errors.
                }
                if (result == null) {
                  retry--;
                } else {
                  retry = 0;
                }
              } while (retry != 0);
            }
          }
        }
      }

      if(JConfig.debugging) inEntry.setLastStatus("Done loading post-bid data.");

      if(bidResult != null) return bidResult;
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
    return BID_ERROR_UNKNOWN;
  }

  /**
   * @brief Returns the set of cookies necessary to be posted in order
   * to retrieve auctions.  getNecessaryCookie() can return null when
   * the process of logging in can't be done, for whatever reason.
   * (For instance, eBay's 2-3 hour downtime on Friday mornings @
   * 1-3am.)
   *
   * @param force - Force an update of the cookie, even if it's not
   * time yet.
   *
   * @return - A cookie jar of all the necessary cookies to do eBay connections.
   */
  public synchronized CookieJar getNecessaryCookie(boolean force) {
    if(_signinCookie == null || force) {
      _signinCookie = getSignInCookie(_signinCookie);
    }

    return(_signinCookie);
  }

  /**
   * @brief Debugging function to dump a string buffer out to a file.
   *
   * This is used for 'emergency' debugging efforts.
   *
   * @param fname - The filename to output to.
   * @param sb - The StringBuffer to dump out.
   */
  private static void dump2File(String fname, StringBuffer sb) {
    FileWriter fw = null;
    try {
      fw = new FileWriter(fname);

      fw.write(sb.toString());
    } catch(IOException ioe) {
      ErrorManagement.handleException("Threw exception in dump2File!", ioe); //$NON-NLS-1$
    } finally {
      if(fw != null) try { fw.close(); } catch(IOException ignored) { /* I don't care about exceptions on close. */ }
    }
  }

  private URLConnection checkFollowRedirector(URLConnection current, CookieJar cj, String lookFor) throws IOException {
    StringBuffer signed_in = Http.receivePage(current);
    if (JConfig.queryConfiguration("debug.filedump", "false").equals("true")) dump2File("sign_in-a1.html", signed_in);

    //  Parse the redirector, and find the URL that points to the adult
    //  confirmation page.
    JHTML redirector = new JHTML(signed_in);
    if(checkSecurityConfirmation(redirector)) return null;
    return checkHTMLFollowRedirect(redirector, lookFor, cj);
  }

  private static URLConnection checkHTMLFollowRedirect(JHTML redirectPage, String lookFor, CookieJar cj) {
    redirectPage.reset();
    List<String> allURLs = redirectPage.getAllURLsOnPage(false);
    for (String url : allURLs) {
      //  If this URL has the text we're looking for in its body someplace, that's the one we want.
      if (url.indexOf(lookFor) != -1) {
        //  Replace nasty quoted amps with single-amps.
        url = url.replaceAll("&amp;", "&");
        url = url.replaceAll("\n", "");
        if (lookFor.equals("BidBin")) {
          int step = url.indexOf("BidBinInfo=");
          if (step != -1) {
            step += "BidBinInfo=".length();

            try {
              String encodedURL = URLEncoder.encode(url.substring(step), "UTF-8");
              //noinspection StringContatenationInLoop
              url = url.substring(0, step) + encodedURL;
            } catch (UnsupportedEncodingException ignored) {
              ErrorManagement.logMessage("Failed to build a URL because of encoding transformation failure.");
            }
          }
        }
        //  Now get the actual page...
        return cj.getAllCookiesFromPage(url, null, false);
      }
    }

    return null;
  }

  //  Get THAT page, which is actually (usually) a 'redirector' page with a meta-refresh
  //  and a clickable link in case meta-refresh doesn't work.
  private boolean getAdultRedirector(URLConnection uc_signin, CookieJar cj) throws IOException {
    uc_signin = checkFollowRedirector(uc_signin, cj, "Adult");
    return uc_signin != null && getAdultConfirmation(uc_signin, cj);

  }

  private static boolean getAdultConfirmation(URLConnection uc_signin, CookieJar cj) throws IOException {
    StringBuffer confirm = Http.receivePage(uc_signin);
    if (JConfig.queryConfiguration("debug.filedump", "false").equals("true")) dump2File("sign_in-a2.html", confirm);
    JHTML confirmPage = new JHTML(confirm);

    List<JHTML.Form> confirm_forms = confirmPage.getForms();
    for(JHTML.Form finalForm : confirm_forms) {
      if (finalForm.hasInput("MfcISAPICommand")) {
        uc_signin = cj.getAllCookiesFromPage(finalForm.getCGI(), null, false);
        StringBuffer confirmed = Http.receivePage(uc_signin);
        if (JConfig.queryConfiguration("debug.filedump", "false").equals("true")) dump2File("sign_in-a2.html", confirmed);
        JHTML htdoc = new JHTML(confirmed);
        JHTML.Form curForm = htdoc.getFormWithInput("pass");
        if (curForm != null) {
          return false;
        }
      }
    }
    return true;
  }

  // @noinspection TailRecursion
  public CookieJar getSignInCookie(CookieJar oldCookie, String username, String password) {
    boolean isAdult = JConfig.queryConfiguration(siteId + ".adult", "false").equals("true");
    CookieJar cj = (oldCookie==null)?new CookieJar():oldCookie;
    String startURL = Externalized.getString("ebayServer.signInPage");
    if(isAdult) {
      startURL = Externalized.getString("ebayServer.adultPageLogin");
    }
    URLConnection uc_signin = cj.getAllCookiesFromPage(startURL, null, false);
    try {
      StringBuffer signin = Http.receivePage(uc_signin);
      if(JConfig.queryConfiguration("debug.filedump", "false").equals("true")) dump2File("sign_in-1.html", signin);
      JHTML htdoc = new JHTML(signin);

      JHTML.Form curForm = htdoc.getFormWithInput("pass");
      if(curForm != null) {
        //  If it has a password field, this is the input form.
        curForm.setText("userid", username);
        curForm.setText("pass", password);
        uc_signin = cj.getAllCookiesFromPage(curForm.getCGI(), null, false);
        if (isAdult) {
          if (getAdultRedirector(uc_signin, cj)) {
            MQFactory.getConcrete("Swing").enqueue("VALID LOGIN"); //$NON-NLS-1$ //$NON-NLS-2$
          } else {
            //  Disable adult mode and try again.
            ErrorManagement.logMessage("Disabling 'adult' mode and retrying.");
            JConfig.setConfiguration(siteId + ".adult", "false");
            return getSignInCookie(cj, username, password);
          }
        } else {
          StringBuffer confirm = Http.receivePage(uc_signin);
          if (JConfig.queryConfiguration("debug.filedump", "false").equals("true")) dump2File("sign_in-2.html", confirm);
          JHTML doc = new JHTML(confirm);
          if(checkSecurityConfirmation(doc)) {
            cj = null;
          } else if(doc.grep("Your sign in information is not valid.") != null) {
            mBadPassword = getPassword();
            mBadUsername = getUserId();
            ErrorManagement.logMessage("Username/password not valid.");
            MQFactory.getConcrete("Swing").enqueue("INVALID LOGIN Username/password not recognized by eBay."); //$NON-NLS-1$ //$NON-NLS-2$
          } else {
            MQFactory.getConcrete("Swing").enqueue("VALID LOGIN"); //$NON-NLS-1$ //$NON-NLS-2$
          }
        }
      }
    } catch (IOException e) {
      //  We don't know how far we might have gotten...  The cookies
      //  may be valid, even!  We can't assume it, though.
      MQFactory.getConcrete("Swing").enqueue("INVALID LOGIN " + e.getMessage()); //$NON-NLS-1$ //$NON-NLS-2$
      ErrorManagement.handleException("Couldn't sign in!", e);
    }

    return cj;
  }

  private void notifySecurityIssue() {
    MQFactory.getConcrete("Swing").enqueue("NOTIFY " + "eBay's security monitoring has been triggered, and temporarily requires\n" +
        "human intervention to log in.  JBidwatcher will not be able to log in\n" +
        "(including bids, snipes, and retrieving My eBay items) until this is fixed.");
  }

  private void notifyBadSignin() {
    MQFactory.getConcrete("Swing").enqueue("NOTIFY " + "Your sign in information appears to be incorrect, according to\n" +
                                                       "eBay.  Please fix it in the eBay tab in the Configuration Manager.");
  }

  private boolean checkSecurityConfirmation(JHTML doc) throws IOException {
    if(doc.grep("Security Confirmation") != null) {
      ErrorManagement.logMessage("eBay's security monitoring has been triggered, and temporarily requires human intervention to log in.");
      MQFactory.getConcrete("Swing").enqueue("INVALID LOGIN eBay's security monitoring has been triggered, and temporarily requires human intervention to log in.");
      notifySecurityIssue();
      mBadPassword = getPassword();
      mBadUsername = getUserId();
      throw new IOException("Failed eBay security check (captcha).");
    }

    if(doc.grep("Your sign in information is not valid.") != null) {
      ErrorManagement.logMessage("Your sign in information is not valid.");
      MQFactory.getConcrete("Swing").enqueue("INVALID LOGIN Your sign in information is not correct.  Fix it in the eBay tab in the Configuration Manager.");
      notifyBadSignin();
      mBadPassword = getPassword();
      mBadUsername = getUserId();
      return true;
    }

    return false;
  }

  /**
   * @brief eBay has a cookie that is needed to do virtually anything
   * interesting on their site; this function retrieves that cookie,
   * and holds on to it.
   *
   * If you are registered as an adult, it also logs in through that
   * page, getting all necessary cookies.
   *
   * @return - A collection of cookies that need to be passed around
   * (and updated) each time pages are requested, etc., on eBay.
   */
  public synchronized CookieJar getSignInCookie(CookieJar old_cj) {
    if(getPassword().equals(mBadPassword) && getUserId().equals(mBadUsername)) {
      return old_cj;
    }

    String msg = "Getting the sign in cookie.";

    if(JConfig.queryConfiguration("debug.verbose", "false").equals("true")) ErrorManagement.logDebug(msg);
    MQFactory.getConcrete("Swing").enqueue(msg);  //$NON-NLS-1$

    CookieJar cj = getSignInCookie(old_cj, getUserId(), getPassword());

    String done_msg = "Done getting the sign in cookie.";
    MQFactory.getConcrete("Swing").enqueue(done_msg);  //$NON-NLS-1$
    if(JConfig.queryConfiguration("debug.verbose", "false").equals("true")) ErrorManagement.logDebug(done_msg);

    return cj;
  }

  /**
   * @brief Add all the items on the page to the list of monitored auctions.
   *
   * @param htmlDocument - The document to get all the items from.
   * @param category - What 'group' to label items retrieved this way as.
   * @param interactive - Is this operation being done interactively, by the user?
   *
   * @return - A count of items added.
   */
  private static int addAllItemsOnPage(JHTML htmlDocument, String category, boolean interactive) {
    List<String> allItemsOnPage = htmlDocument.getAllURLsOnPage(true);
    int item_count = 0;

    if(allItemsOnPage == null) {
      ErrorManagement.logDebug("No items on page!"); //$NON-NLS-1$
    } else {
      for(ListIterator<String> it=allItemsOnPage.listIterator(); it.hasNext(); ) {
        String url = it.next();

        url = url.replaceAll("\n|\r", "");
        boolean gotNext;
        String nextURL;

        if(it.hasNext()) {
          nextURL = it.next();

          nextURL = nextURL.replaceAll("\n|\r", "");
          gotNext = true;
        } else {
          nextURL = ""; //$NON-NLS-1$
          gotNext = false;
        }

        //  If the URL is listed multiple times in order, then skip
        //  until the last instance of it.
        if (nextURL.equals(url)) {
          //  If they're equal, it pretty much has to have gotten the next entry, but for safety's sake, check.
          if (gotNext) it.previous();
        } else {
          //  Back out the move if we made one.
          if (gotNext) it.previous();
          url = url.trim();

          /**
           * Does this look like an auction server item URL?
           */
          AuctionServer aucServ = AuctionServerManager.getInstance().getServerForUrlString(url);
          String hasId = aucServ.extractIdentifierFromURLString(url);

          if (hasId != null) {
            MQFactory.getConcrete("drop").enqueue(new DropQObject(url.trim(), category, interactive)); //$NON-NLS-1$
            item_count++;
          }
        }
      }
    }
    return item_count;
  }

  /**
   *  @brief Clear the search queue.
   *
   *  This queue is basically only used for starting searches.
   */
  public void cancelSearches() {
    MQFactory.getConcrete("ebay").clear(); //$NON-NLS-1$
  }

  /**
   * @brief Add search types to the search manager.
   *
   * Allows an auction server class to add unusual or site-specific
   * searches to the search manager.
   *
   * @param searchManager - The search manager to add these searches to.
   */
  public void addSearches(SearchManagerInterface searchManager) {
    String doSync = JConfig.queryConfiguration(siteId + ".synchronize", "false"); //$NON-NLS-1$ //$NON-NLS-2$

    if(!doSync.equals("ignore")) {
      if(doSync.equalsIgnoreCase("true")) { //$NON-NLS-1$
        _my_ebay = searchManager.addSearch("My Items", "My eBay", "", "ebay", 1, 1); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
      } else {
        _my_ebay = searchManager.addSearch("My Items", "My eBay", "", "ebay", -1, 1); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
      }
      JConfig.setConfiguration(siteId + ".synchronize", "ignore");
    }
  }

  /**
   * @brief Get the list of bidders on an item.
   *
   * This is primarily useful for networks-of-interest searching.
   *
   * @param ae - The item you are interested in.
   *
   * @return - A list containing strings with the names of each
   * user who was interested in the item enough to bid.
   */
  public List<String> getBidderNames(AuctionEntry ae) {
    CookieJar cj = getNecessaryCookie(false);
    String userCookie = null;
    if (cj != null) userCookie = cj.toString();
    JHTML htmlDocument = new JHTML(eBayProtocol + Externalized.getString("ebayServer.bidderNamesHost") + eBayFile + Externalized.getString("ebayServer.viewBidsCGI") + ae.getIdentifier(), userCookie, this);

//    if(htmlDocument == null) {
//      ErrorManagement.logMessage("Error getting bidder names for auction " + ae.getIdentifier()); //$NON-NLS-1$
//      return null;
//    }
//
    String curName = htmlDocument.getNextContentAfterContent(Externalized.getString("ebayServer.bidListPrequel"));

    if(curName == null) {
      ErrorManagement.logMessage("Problem with loaded page when getting bidder names for auction " + ae.getIdentifier()); //$NON-NLS-1$
      return null;
    }

    List<String> outNames = new ArrayList<String>();

    do {
      if(!outNames.contains(curName)) {
        outNames.add(curName);
      }
      curName = htmlDocument.getNextContent();
      while(curName != null && ! (curName.endsWith("PDT") || curName.endsWith("PST"))) { //$NON-NLS-1$ //$NON-NLS-2$
        curName = htmlDocument.getNextContent();
      }
      if(curName != null) curName = htmlDocument.getNextContent();
      if(curName != null) {
        if(curName.indexOf(Externalized.getString("ebayServer.earlierCheck")) != -1) curName = null; //$NON-NLS-1$
      }
    } while(curName != null);

    return outNames;
  }

  /**
   * @brief Do a Seller Search to see all the items a given user is selling.
   *
   * This obsoletes our previous use of 'My eBay' to get the selling
   * information.
   *
   * @param userId - The user to load their selling items for.
   * @param label - What 'group' to label items retrieved this way as.
   */
  private void getSellingItems(String userId, String label) {
    CookieJar cj = getNecessaryCookie(false);
    String userCookie = null;
    if (cj != null) userCookie = cj.toString();

    if(userId == null || userId.equals("default")) { //$NON-NLS-1$
      ErrorManagement.logMessage("Cannot load selling pages without at least a userid."); //$NON-NLS-1$
      return;
    }

    String myEBayURL = eBayProtocol + Externalized.getString("ebayServer.sellingListHost") + eBayV3File + //$NON-NLS-1$
                       Externalized.getString("ebayServer.listedCGI") + //$NON-NLS-1$
                       Externalized.getString("ebayServer.sortOrderCGI") +
                       Externalized.getString("ebayServer.userIdCGI") + userId; //$NON-NLS-1$

    JHTML htmlDocument = new JHTML(myEBayURL, userCookie, this);

    if(htmlDocument.isLoaded()) {
      int count = addAllItemsOnPage(htmlDocument, label, userId.equals(getUserId()));
      MQFactory.getConcrete("Swing").enqueue("Loaded " + count + " items for seller " + userId);
    } else {
      ErrorManagement.logMessage("getSellingItems failed!"); //$NON-NLS-1$
    }
  }

  /**
   * @brief Load all items we can find in the 'My eBay' bidding page.
   *
   * Unfortunately, that can include items in the 'You might like...' area.
   *
   * @param label - What 'group' to label items retrieved this way as.
   */
  private void getMyEbayItems(String label) {
    String localUser = getUserId();
    CookieJar cj = getNecessaryCookie(false);
    String userCookie = null;
    if (cj != null) userCookie = cj.toString();

    if(localUser == null || localUser.equals("default")) { //$NON-NLS-1$ //$NON-NLS-2$
      ErrorManagement.logMessage("Cannot load My eBay pages without a userid and password."); //$NON-NLS-1$
      return;
    }

    int page = 0;
    boolean done_watching = false;
    while(!done_watching) {
      //  First load items that the user is watching (!)
      //    String watchingURL = Externalized.getString("ebayServer.watchingURL");
      String watchingURL = Externalized.getString("ebayServer.bigWatchingURL1") + getUserId() +
              Externalized.getString("ebayServer.bigWatchingURL2") + page +
              Externalized.getString("ebayServer.bigWatchingURL3") + (page+1);
      ErrorManagement.logDebug("Loading page " + page + " of My eBay for user " + getUserId()); //$NON-NLS-1$
      ErrorManagement.logDebug("URL: " + watchingURL);

      JHTML htmlDocument = new JHTML(watchingURL, userCookie, this);
      addAllItemsOnPage(htmlDocument, label, true);
      String ofX = htmlDocument.getNextContentAfterRegex("Page " + (page+1));
      if(ofX == null || !ofX.startsWith("of ")) done_watching = true;
      else try { done_watching = (page+1)==Integer.parseInt(ofX.substring(3)); } catch(NumberFormatException ignored) { done_watching = true; }
      if(!done_watching) page++;
    }

    boolean done_bidding = false;
    while(!done_bidding) {
      //  Now load items the user is bidding on...
      String biddingURL = Externalized.getString("ebayServer.biddingURL");
      ErrorManagement.logDebug("Loading page: " + biddingURL); //$NON-NLS-1$

      //noinspection ReuseOfLocalVariable
      JHTML htmlDocument = new JHTML(biddingURL, userCookie, this);
      addAllItemsOnPage(htmlDocument, label, true);
      done_bidding = true;
    }
  }

  /**
   * @brief Delete characters from a range within a stringbuffer, safely.
   *
   * @param sb - The stringbuffer to delete from.
   * @param desc_start - The start point to delete from.
   * @param desc_end - The endpoint to delete to.
   *
   * @return - true if a deletion occurred, false if the parameters
   * were invalid in any way.
   */
  private static boolean deleteRange(StringBuffer sb, int desc_start, int desc_end) {
    if(desc_start < desc_end &&
       desc_start != -1 &&
       desc_end != -1) {
      sb.delete(desc_start, desc_end);
      return true;
    }
    return false;
  }

  /**
   * @brief Delete a block of text, indicated by a start and end
   * string pair, with alternates.
   *
   * @param sb - The StringBuffer to delete from, In/Out.
   * @param startStr - The start string to delete from.
   * @param altStartStr - An alternate start string, in case the startStr isn't found.
   * @param endStr - The end string to delete to.
   * @param altEndStr - An alternate end string in case the endStr is found before the start string.
   *
   * @return - true if a delete occurred, false otherwise.
   */
  private static boolean deleteFirstToLast(StringBuffer sb, String startStr, String altStartStr, String endStr, String altEndStr) {
    String fullBuff = sb.toString();
    int desc_start = fullBuff.indexOf(startStr);

    if(desc_start == -1) {
      desc_start = fullBuff.indexOf(altStartStr);
    }

    int desc_end = fullBuff.lastIndexOf(endStr);

    if(desc_start > desc_end) desc_end = fullBuff.lastIndexOf(altEndStr);

    return deleteRange(sb, desc_start, desc_end);
  }

  /**
   * @brief Simple utility to delete from a stringbuffer starting
   * from a string, until the next following string.
   *
   * @param sb - The buffer to delete from.
   * @param startStr - The string to delete starting at.
   * @param endStr - The string to delete up until.
   *
   * @return - true if the delete happened, false otherwise.
   */
  private static boolean deleteRegexPair(StringBuffer sb, String startStr, String endStr) {
    Matcher start = Pattern.compile(startStr, Pattern.CASE_INSENSITIVE).matcher(sb);
    Matcher end = Pattern.compile(endStr, Pattern.CASE_INSENSITIVE).matcher(sb);

    if(start.find() &&
       end.find(start.start()+1)) {
      int desc_start = start.start();
      int desc_end = end.end();

      return deleteRange(sb, desc_start, desc_end);
    }
    return false;
  }

  /**
   * @brief Remove all scripts (javascript or other) in the string
   * buffer passed in.
   *
   * @param sb - The StringBuffer to eliminate script entries from.
   */
  private static void killScripts(StringBuffer sb) {
    boolean didDelete;
    do {
      didDelete = deleteRegexPair(sb, Externalized.getString("ebayServer.stripScript"), Externalized.getString("ebayServer.stripScriptEnd")); //$NON-NLS-1$ //$NON-NLS-2$
    } while(didDelete);
  }

  /**
   * @brief Delete all scripts, and comments on an HTML page.
   *
   * @param sb - The StringBuffer to clean of scripts and comments.
   */
  private static void internalCleanup(StringBuffer sb) {
    killScripts(sb);

    //  Eliminate all comment sections.
    boolean didDelete;
    do {
      didDelete = deleteRegexPair(sb, Externalized.getString("ebayServer.stripComment"), Externalized.getString("ebayServer.stripCommentEnd")); //$NON-NLS-1$ //$NON-NLS-2$
    } while(didDelete);
  }

  /**
   * @brief Chain to the internal cleanup code.
   *
   * @param sb - The StringBuffer to clean of scripts and comments.
   */
  public void cleanup(StringBuffer sb) {
    internalCleanup(sb);
  }

  protected class ebayAuction extends SpecificAuction {
    String _bidCountScript = null;
    String _startComment = null;
    private static final int TITLE_LENGTH = 60;
    private static final int HIGH_BIT_SET = 0x80;
    //private final Pattern p = Pattern.compile("src=\"(http://[a-z0-9]+?\\.ebayimg\\.com.*?(jpg|gif|png))\"", Pattern.DOTALL | Pattern.MULTILINE | Pattern.CASE_INSENSITIVE);
    private final Pattern p = Pattern.compile(Externalized.getString("ebayServer.thumbSearch"), Pattern.DOTALL | Pattern.MULTILINE | Pattern.CASE_INSENSITIVE);
    private final Pattern p2 = Pattern.compile(Externalized.getString("ebayServer.thumbSearch2"), Pattern.DOTALL | Pattern.MULTILINE | Pattern.CASE_INSENSITIVE);
    private String potentialThumbnail = null;

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
      deleteFirstToLast(sb, eBayDescStart, eBayMotorsDescStart, eBayDescEnd, eBayClosedDescEnd);

      deleteFirstToLast(sb, Externalized.getString("ebayServer.descStart"), eBayMotorsDescStart, Externalized.getString("ebayServer.descEnd"), eBayClosedDescEnd); //$NON-NLS-1$ //$NON-NLS-2$

      String skimOver = sb.toString();

      Matcher startCommentSearch = Pattern.compile(Externalized.getString("ebayServer.startedRegex")).matcher(skimOver); //$NON-NLS-1$
      if(startCommentSearch.find()) _startComment = startCommentSearch.group(1);
      else _startComment = "";

      Matcher bidCountSearch = Pattern.compile(Externalized.getString("ebayServer.bidCountRegex")).matcher(skimOver); //$NON-NLS-1$
      if(bidCountSearch.find()) _bidCountScript = bidCountSearch.group(1);
      else _bidCountScript = "";

      //  Use eBayServer's cleanup method to finish up with.
      internalCleanup(sb);
    }

    boolean checkTitle(String auctionTitle) {
      if(auctionTitle.startsWith(Externalized.getString("ebayServer.liveAuctionsTitle"))) { //$NON-NLS-1$
        ErrorManagement.logMessage("JBidWatcher cannot handle live auctions!"); //$NON-NLS-1$
        return false;
      }

      if(auctionTitle.startsWith(Externalized.getString("ebayServer.greatCollectionsTitle"))) { //$NON-NLS-1$
        ErrorManagement.logMessage("JBidWatcher cannot handle Great Collections items yet."); //$NON-NLS-1$
        return false;
      }

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
          if (approxAmount.indexOf(Externalized.getString("ebayServer.USD")) == -1) { //$NON-NLS-1$
            newCur = com.jbidwatcher.util.Currency.getCurrency("$0.00"); //$NON-NLS-1$
          } else {
            approxAmount = approxAmount.substring(approxAmount.indexOf(Externalized.getString("ebayServer.USD"))); //$NON-NLS-1$
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
      if(in_title.indexOf(eBayTemporarilyUnavailable) != -1) {
        MQFactory.getConcrete("Swing").enqueue("LINK DOWN eBay (or the link to eBay) appears to be down."); //$NON-NLS-1$ //$NON-NLS-2$
        MQFactory.getConcrete("Swing").enqueue("eBay (or the link to eBay) appears to be down for the moment."); //$NON-NLS-1$ //$NON-NLS-2$
      } else if(in_title.indexOf(Externalized.getString("ebayServer.invalidItem")) != -1) { //$NON-NLS-1$
        ErrorManagement.logDebug("Found bad/deleted item."); //$NON-NLS-1$
      } else {
        ErrorManagement.logDebug("Failed to load auction title from header: \"" + in_title + '\"'); //$NON-NLS-1$ //$NON-NLS-2$
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
                jh.getToken().equalsIgnoreCase("/title"))); //$NON-NLS-1$

      return outTitle.toString();
    }

    private Pattern amountPat = Pattern.compile("(([0-9]+\\.[0-9]+|(?i)free))");

    private void load_shipping_insurance(com.jbidwatcher.util.Currency sampleAmount) {
      String shipString = _htmlDocument.getNextContentAfterRegex(eBayShippingRegex);
      //  Sometimes the next content might not be the shipping amount, it might be the next-next.
      Matcher amount = null;
      if(shipString != null) amount = amountPat.matcher(shipString);
      if(shipString != null && !amount.find()) {
        shipString = _htmlDocument.getNextContent();
        amount = amountPat.matcher(shipString);
        if(shipString != null) amount.find();
      }
      //  This will result in either 'null' or the amount.
      if(shipString != null) shipString = amount.group();

      //  Step back two contents, to check if it's 'Payment
      //  Instructions', in which case, the shipping and handling
      //  came from their instructions box, not the
      //  standard-formatted data.
      String shipStringCheck = _htmlDocument.getPrevContent(2);

      String insureString = _htmlDocument.getNextContentAfterRegex(eBayInsurance);
      String insuranceOptionalCheck = _htmlDocument.getNextContent();

      //  Default to thinking it's optional if the word 'required' isn't found.
      //  You don't want to make people think it's required if it's not.
      _insurance_optional = insuranceOptionalCheck == null || (insuranceOptionalCheck.toLowerCase().indexOf(Externalized.getString("ebayServer.requiredInsurance")) == -1);

      if(insureString != null) {
        if(insureString.equals("-") || insureString.equals("--")) { //$NON-NLS-1$ //$NON-NLS-2$
          insureString = null;
        } else {
          insureString = insureString.trim();
        }
      }

      if(shipStringCheck != null && !shipStringCheck.equals(eBayPayInstructions)) {
        if(shipString != null) {
          if(shipString.equals("-")) { //$NON-NLS-1$
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
          _shipping = com.jbidwatcher.util.Currency.getCurrency(sampleAmount.fullCurrencyName(), shipString);
        }
      } else {
        _shipping = com.jbidwatcher.util.Currency.NoValue();
      }
      _insurance = com.jbidwatcher.util.Currency.getCurrency(insureString);
    }

    private void load_buy_now() {
      _buy_now = com.jbidwatcher.util.Currency.NoValue();
      _buy_now_us = com.jbidwatcher.util.Currency.NoValue();
      String buyNowString = _htmlDocument.getNextContentAfterContent(eBayBuyItNow);
      if(buyNowString != null) {
        buyNowString = buyNowString.trim();
        while(buyNowString.length() == 0 || buyNowString.equals(Externalized.getString("ebayServer.buyNowFor"))) { //$NON-NLS-1$ //$NON-NLS-2$
          buyNowString = _htmlDocument.getNextContent().trim();
        }
      }

      if(buyNowString != null && !buyNowString.equals(eBayItemEnded)) {
        _buy_now = com.jbidwatcher.util.Currency.getCurrency(buyNowString);
        _buy_now_us = getUSCurrency(_buy_now, _htmlDocument);
      }
      if(buyNowString == null || buyNowString.equals(eBayItemEnded) || _buy_now == null || _buy_now.isNull()) {
        String altBuyNowString1 = _htmlDocument.getNextContentAfterRegexIgnoring(eBayPrice, "[Ii]tem.[Nn]umber");
        if(altBuyNowString1 != null) {
          altBuyNowString1 = altBuyNowString1.trim();
        }
        if(altBuyNowString1 != null && altBuyNowString1.length() != 0) { //$NON-NLS-1$
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
        return matcher.group(match);
      }

      return null;
    }

    private void loadSecondaryInformation(JHTML doc) {
      try {
        String score = getResult(doc, Externalized.getString("ebayServer.feedbackRegex"), 1);
        if(score != null && isNumberOnly(score)) {
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

    public boolean parseAuction(AuctionEntry ae) {
      //  Verify the title (in case it's an invalid page, the site is
      //  down for maintenance, etc).
      String prelimTitle = _htmlDocument.getFirstContent();
      if( prelimTitle == null) {
        prelimTitle = eBayTemporarilyUnavailable;
      }
      if(prelimTitle.equals(eBayAdultLoginPageTitle) || prelimTitle.indexOf("Terms of Use: ") != -1) {
        boolean isAdult = JConfig.queryConfiguration(siteId + ".adult", "false").equals("true"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        if(isAdult) {
          getNecessaryCookie(true);
        } else {
          ErrorManagement.logDebug("Failed to load adult item, user not registered as Adult.  Check eBay configuration."); //$NON-NLS-1$
        }
        finish();
        return false;
      }

      //  Is this a valid eBay item page?
      if(!checkTitle(prelimTitle)) {
        handle_bad_title(prelimTitle);
        finish();
        return false;
      }

      //  If we got a valid title, mark the link as up, because it worked...
      MQFactory.getConcrete("Swing").enqueue("LINK UP"); //$NON-NLS-1$ //$NON-NLS-2$

      boolean ebayMotors = false;
      if(prelimTitle.indexOf(Externalized.getString("ebayServer.ebayMotorsTitle")) != -1) ebayMotors = true; //$NON-NLS-1$
      //  This is mostly a hope, not a guarantee, as eBay might start
      //  cross-advertising eBay Motors in their normal pages, or
      //  something.
      if(doesLabelExist(Externalized.getString("ebayServer.ebayMotorsTitle"))) ebayMotors = true; //$NON-NLS-1$

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
          _end = figureDate(endDate, eBayDateFormat);
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
          titleIndex = prelimTitle.indexOf(") -"); //$NON-NLS-1$
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

      if(_title.length() == 0) _title = "(bad title)"; //$NON-NLS-1$ //$NON-NLS-2$
      _title = JHTML.deAmpersand(_title);

      // eBay Motors titles are really a combination of the make/model,
      // and the user's own text.  Under BIBO, the user's own text is
      // below the 'description' fold.  For now, we don't get the user
      // text.
      if(ebayMotors) {
        extractMotorsTitle();
      }

      //  Get the integer values (Quantity, Bidcount)
      _quantity = getNumberFromLabel(_htmlDocument, eBayQuantity, Externalized.getString("ebayServer.postTitleIgnore"));

      _fixed_price = false;
      _numBids = getBidCount(_htmlDocument, _quantity);

      try {
        load_buy_now();
      } catch(Exception e) {
        ErrorManagement.handleException("Buy It Now Loading error", e);
      }

      if(_end == null) {
        String endDate = getEndDate(prelimTitle);
        _end = figureDate(endDate, eBayDateFormat);
      }

      _start = figureDate(_htmlDocument.getNextContentAfterRegexIgnoring(eBayStartTime, Externalized.getString("ebayServer.postTitleIgnore")), eBayDateFormat);
      if(_start == null) {
        _start = figureDate(_startComment, eBayDateFormat);
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
          String cvtCur = _htmlDocument.getNextContentAfterRegex(eBayCurrentBid);
          _curBid = com.jbidwatcher.util.Currency.getCurrency(cvtCur);
          _us_cur = getUSCurrency(_curBid, _htmlDocument);

          _curBid = (com.jbidwatcher.util.Currency)ensureSafeValue(_curBid, ae!=null?ae.getCurBid()  : com.jbidwatcher.util.Currency.NoValue(), com.jbidwatcher.util.Currency.NoValue());
          _us_cur = (com.jbidwatcher.util.Currency)ensureSafeValue(_us_cur, ae!=null?ae.getUSCurBid(): com.jbidwatcher.util.Currency.NoValue(), com.jbidwatcher.util.Currency.NoValue());
        }
      } else {
        //  The set of tags that indicate the current/starting/lowest/winning
        //  bid are 'Current bid', 'Starting bid', 'Lowest bid',
        //  'Winning bid' so far.
        String cvtCur = _htmlDocument.getNextContentAfterRegex(eBayCurrentBid);
        _curBid = com.jbidwatcher.util.Currency.getCurrency(cvtCur);
        _us_cur = getUSCurrency(_curBid, _htmlDocument);

        if(_curBid == null || _curBid.isNull()) {
          if(_quantity > 1) {
            _curBid = com.jbidwatcher.util.Currency.getCurrency(_htmlDocument.getNextContentAfterContent(eBayLowestBid));
            _us_cur = getUSCurrency(_curBid, _htmlDocument);
          }
        }

        _minBid = com.jbidwatcher.util.Currency.getCurrency(_htmlDocument.getNextContentAfterContent(eBayFirstBid));
        //  Handle odd case...
        if(_end == null) {
          _end = figureDate(_htmlDocument.getNextContentAfterContent(Externalized.getString("ebayServer.endsPrequel")), eBayDateFormat); //$NON-NLS-1$
        }
        com.jbidwatcher.util.Currency maxBid = com.jbidwatcher.util.Currency.getCurrency(_htmlDocument.getNextContentAfterContent(Externalized.getString("ebayServer.yourMaxBid")));

        _end    =     (Date)ensureSafeValue(_end,    ae!=null?ae.getEndDate() :null, null);
        _minBid = (com.jbidwatcher.util.Currency)ensureSafeValue(_minBid, ae!=null?ae.getMinBid()  : com.jbidwatcher.util.Currency.NoValue(), com.jbidwatcher.util.Currency.NoValue());
        _curBid = (com.jbidwatcher.util.Currency)ensureSafeValue(_curBid, ae!=null?ae.getCurBid()  : com.jbidwatcher.util.Currency.NoValue(), com.jbidwatcher.util.Currency.NoValue());
        _us_cur = (com.jbidwatcher.util.Currency)ensureSafeValue(_us_cur, ae!=null?ae.getUSCurBid(): com.jbidwatcher.util.Currency.NoValue(), com.jbidwatcher.util.Currency.NoValue());

        if(_numBids == 0 && (_minBid == null || _minBid.isNull())) _minBid = _curBid;

        if(_minBid == null || _minBid.isNull()) {
          String original = _htmlDocument.grep(Externalized.getString("ebayServer.originalBid"));
          if(original != null) {
            Pattern bidPat = Pattern.compile(Externalized.getString("ebayServer.originalBid"));
            Matcher bidMatch = bidPat.matcher(original);
            if(bidMatch.find()) {
              _minBid = com.jbidwatcher.util.Currency.getCurrency(bidMatch.group(1));
            }
          }
        }

        // This is dangerously intimate with the AuctionEntry class,
        // and it won't work the first time, since the first time ae
        // is null.
        if(ae != null && !maxBid.isNull()) {
          try {
            if(!ae.isBidOn() || ae.getBid().less(maxBid)) ae.setBid(maxBid);
          } catch(com.jbidwatcher.util.Currency.CurrencyTypeException cte) {
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

      _seller = _htmlDocument.getNextContentAfterRegex(eBaySeller);
      if(_seller == null) {
        _seller = _htmlDocument.getNextContentAfterContent(Externalized.getString("ebayServer.sellerInfoPrequel"), false, true); //$NON-NLS-1$
      }
      if(_seller == null) {
        if(_htmlDocument.grep(Externalized.getString("ebayServer.sellerAway")) != null) {
          if(ae != null) {
            ae.setLastStatus("Seller away - item unavailable.");
          }
          finish();
          return false;
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
        _highBidder = _htmlDocument.getNextContentAfterRegex(eBayBuyer);
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
          if (_highBidderEmail == null || _highBidderEmail.equals("(")) { //$NON-NLS-1$
            _highBidderEmail = "(unknown)"; //$NON-NLS-1$
          }
        } else {
          _highBidder = ""; //$NON-NLS-1$
        }
      } else {
        if (_quantity > 1) {
          _highBidder = "(dutch)"; //$NON-NLS-1$
          _isDutch = true;
        } else {
          _highBidder = ""; //$NON-NLS-1$
          if (_numBids != 0) {
            _highBidder = _htmlDocument.getNextContentAfterRegex(eBayHighBidder);
            if (_highBidder != null) {
              _highBidder = _highBidder.trim();

              _highBidderEmail = _htmlDocument.getNextContentAfterContent(_highBidder, true, false);
              if (_highBidderEmail.charAt(0) == '(' && _highBidderEmail.charAt(_highBidderEmail.length()-1) == ')' && _highBidderEmail.indexOf('@') != -1) { //$NON-NLS-1$
                _highBidderEmail = _highBidderEmail.substring(1, _highBidderEmail.length() - 1);
              }
            } else {
              _highBidder = "(unknown)"; //$NON-NLS-1$
            }
          }
        }
      }

      if(doesLabelExist(Externalized.getString("ebayServer.reserveNotMet1")) || //$NON-NLS-1$
         doesLabelExist(Externalized.getString("ebayServer.reserveNotMet2"))) { //$NON-NLS-1$
        _isReserve = true;
        _reserveMet = false;
      } else {
        if(doesLabelExist(Externalized.getString("ebayServer.reserveMet1")) || //$NON-NLS-1$
           doesLabelExist(Externalized.getString("ebayServer.reserveMet2"))) { //$NON-NLS-1$
          _isReserve = true;
          _reserveMet = true;
        }
      }
      if(_highBidder.indexOf(Externalized.getString("ebayServer.keptPrivate")) != -1) { //$NON-NLS-1$
        _isPrivate = true;
        _highBidder = "(private)"; //$NON-NLS-1$
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
      return true;
    }

    private int getBidCount(JHTML doc, int quantity) {
      String rawBidCount = doc.getNextContentAfterRegex(eBayBidCount);
      int bidCount = 0;
      if(rawBidCount != null) {
        if(rawBidCount.equals(Externalized.getString("ebayServer.purchasesBidCount")) ||
           rawBidCount.endsWith(Externalized.getString("ebayServer.offerRecognition")) ||
           rawBidCount.equals(Externalized.getString("ebayServer.offerRecognition"))) { //$NON-NLS-1$
          _fixed_price = true;
          bidCount = -1;
        } else {
          if(rawBidCount.equals(Externalized.getString("ebayServer.bidderListCount"))) { //$NON-NLS-1$
            bidCount = Integer.parseInt(_bidCountScript);
          } else {
            bidCount = getDigits(rawBidCount);
          }
        }
      }

      //  If we can't match any digits in the bidcount, or there is no match for the eBayBidCount regex, then
      //  this is a store or FP item.  Still true under BIBO?
      if (rawBidCount == null || _numBids == -1) {
        _highBidder = Externalized.getString("ebayServer.fixedPrice"); //$NON-NLS-1$
        _fixed_price = true;

        if (doesLabelExist(Externalized.getString("ebayServer.hasBeenPurchased")) || //$NON-NLS-1$
            doesLabelPrefixExist(Externalized.getString("ebayServer.endedEarly"))) { //$NON-NLS-1$
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
      String motorsTitle = _htmlDocument.getContentBeforeContent(eBayItemNumber); //$NON-NLS-1$
      if(motorsTitle != null) {
        motorsTitle = motorsTitle.trim();
      }
      if(motorsTitle != null && motorsTitle.length() != 0 && !_title.equals(motorsTitle)) { //$NON-NLS-1$
        if(motorsTitle.length() != 1 || motorsTitle.charAt(0) < HIGH_BIT_SET) {
          if(_title.length() == 0) {
            _title = decodeLatin(motorsTitle);
          } else {
            _title = decodeLatin(motorsTitle + " (" + _title + ')'); //$NON-NLS-1$ //$NON-NLS-2$
          }
        }
      }
    }
  }

  private void doMyEbaySynchronize(String label) {
    MQFactory.getConcrete("Swing").enqueue("Synchronizing with My eBay..."); //$NON-NLS-1$ //$NON-NLS-2$
    getMyEbayItems(label);
    MQFactory.getConcrete("Swing").enqueue("Done synchronizing with My eBay..."); //$NON-NLS-1$ //$NON-NLS-2$
  }

  private void doGetSelling(Object searcher, String label) {
    String userId = ((Searcher)searcher).getSearch();
    MQFactory.getConcrete("Swing").enqueue("Getting Selling Items for " + userId); //$NON-NLS-1$ //$NON-NLS-2$
    getSellingItems(userId, label);
    MQFactory.getConcrete("Swing").enqueue("Done Getting Selling Items for " + userId); //$NON-NLS-1$ //$NON-NLS-2$
  }

  protected static void doLoadAuctions() {
    OptionUI oui = new OptionUI();

    //  Use the right parent!  FIXME -- mrs: 17-February-2003 23:53
    String endResult = oui.promptString(null, "Enter the URL to load auctions from", "Loading Auctions");

    if(endResult == null) return;

    MQFactory.getConcrete("ebay").enqueue(new AuctionQObject(AuctionQObject.LOAD_URL, endResult, null)); //$NON-NLS-1$
  }

  class ebayServerMenu extends ServerMenu {
    public void initialize() {
      addMenuItem("Search eBay", 'F');
      addMenuItem("Get My eBay Items", 'M'); //$NON-NLS-1$
      addMenuItem("Get Selling Items", 'S'); //$NON-NLS-1$
      addMenuItem("Refresh eBay session", "Update login cookie", 'U'); //$NON-NLS-1$
      if(JConfig.debugging) addMenuItem("[Dump eBay activity queue]", 'Q');  //$NON-NLS-1$
    }

    public void actionPerformed(ActionEvent ae) {
      String actionString = ae.getActionCommand();

      //  Handle stuff which is redirected to the search manager.
      if(actionString.equals("Search eBay")) MQFactory.getConcrete("user").enqueue("SEARCH"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
      else MQFactory.getConcrete("ebay").enqueue(new AuctionQObject(AuctionQObject.MENU_CMD, actionString, null)); //$NON-NLS-1$
    }

    protected ebayServerMenu(String serverName, char ch) {
      super(serverName, ch);
    }
  }

  public class JConfigEbayTab extends JConfigTab {
    JCheckBox adultBox;
    JCheckBox synchBox = null;
    JTextField username;
    JTextField password;
    JComboBox siteSelect;

    public String getTabName() { return eBayDisplayName; }
    public void cancel() { }

    public boolean apply() {
      int selectedSite = siteSelect.getSelectedIndex();

      String old_adult = JConfig.queryConfiguration(siteId + ".adult"); //$NON-NLS-1$
      JConfig.setConfiguration(siteId + ".adult", adultBox.isSelected()?"true":"false"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
      String new_adult = JConfig.queryConfiguration(siteId + ".adult"); //$NON-NLS-1$
      if(JConfig.queryConfiguration("prompt.ebay_synchronize", "false").equals("true")) {
        JConfig.setConfiguration(siteId + ".synchronize", synchBox.isSelected()?"true":"false"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        if(_my_ebay == null) {
          _my_ebay = SearchManager.getInstance().getSearchByName("My eBay");
        }
        if(_my_ebay != null) {
          if(synchBox.isSelected()) {
            _my_ebay.enable();
          } else {
            _my_ebay.disable();
          }
        }
      }

      String old_user = JConfig.queryConfiguration(siteId + ".user"); //$NON-NLS-1$
      JConfig.setConfiguration(siteId + ".user", username.getText()); //$NON-NLS-1$
      String new_user = JConfig.queryConfiguration(siteId + ".user"); //$NON-NLS-1$
      String old_pass = JConfig.queryConfiguration(siteId + ".password"); //$NON-NLS-1$
      JConfig.setConfiguration(siteId + ".password", password.getText()); //$NON-NLS-1$
      String new_pass = JConfig.queryConfiguration(siteId + ".password"); //$NON-NLS-1$

      if(selectedSite != -1) {
        JConfig.setConfiguration(siteId + ".browse.site", Integer.toString(selectedSite)); //$NON-NLS-1$
      }

      if(old_pass == null || !new_pass.equals(old_pass) ||
         old_user == null || !new_user.equals(old_user) ||
         old_adult == null || !new_adult.equals(old_adult)) {
        MQFactory.getConcrete("ebay").enqueue(new AuctionQObject(AuctionQObject.MENU_CMD, "Update login cookie", null)); //$NON-NLS-1$ //$NON-NLS-2$
      }
      return true;
    }

    public void updateValues() {
      String isAdult = JConfig.queryConfiguration(siteId + ".adult", "false"); //$NON-NLS-1$ //$NON-NLS-2$
      adultBox.setSelected(isAdult.equals("true")); //$NON-NLS-1$
      if(JConfig.queryConfiguration("prompt.ebay_synchronize", "false").equals("true")) {
        String doSynchronize = JConfig.queryConfiguration(siteId + ".synchronize", "false"); //$NON-NLS-1$ //$NON-NLS-2$

        if(doSynchronize.equals("ignore")) {
          if(_my_ebay != null) synchBox.setSelected(_my_ebay.isEnabled());
        } else {
          synchBox.setSelected(doSynchronize.equals("true")); //$NON-NLS-1$
        }
      }

      username.setText(JConfig.queryConfiguration(siteId + ".user", "default")); //$NON-NLS-1$ //$NON-NLS-2$
      password.setText(JConfig.queryConfiguration(siteId + ".password", "default")); //$NON-NLS-1$ //$NON-NLS-2$
    }

    private JPanel buildUsernamePanel() {
      JPanel tp = new JPanel();
 
      tp.setBorder(BorderFactory.createTitledBorder("eBay User ID")); //$NON-NLS-1$
      tp.setLayout(new BorderLayout());
      username = new JTextField();
      username.addMouseListener(JPasteListener.getInstance());

      username.setText(JConfig.queryConfiguration(siteId + ".user", "default")); //$NON-NLS-1$ //$NON-NLS-2$
      username.setEditable(true);
      username.getAccessibleContext().setAccessibleName("User name to log into eBay"); //$NON-NLS-1$
      password = new JPasswordField(JConfig.queryConfiguration(siteId + ".password")); //$NON-NLS-1$
      password.addMouseListener(JPasteListener.getInstance());
      password.setEditable(true);

      //  Get the password from the configuration entry!  FIX
      password.getAccessibleContext().setAccessibleName("eBay Password"); //$NON-NLS-1$
      password.getAccessibleContext().setAccessibleDescription("This is the user password to log into eBay."); //$NON-NLS-1$

      Box userBox = Box.createVerticalBox();
      userBox.add(makeLine(new JLabel("Username: "), username)); //$NON-NLS-1$
      userBox.add(makeLine(new JLabel("Password:  "), password)); //$NON-NLS-1$
      tp.add(userBox);

      return(tp);
    }

    private JPanel buildCheckboxPanel() {
      String isAdult = JConfig.queryConfiguration(siteId + ".adult", "false"); //$NON-NLS-1$ //$NON-NLS-2$
      String doSynchronize = JConfig.queryConfiguration(siteId + ".synchronize", "false"); //$NON-NLS-1$ //$NON-NLS-2$
      JPanel tp = new JPanel();

      tp.setBorder(BorderFactory.createTitledBorder("General eBay Options")); //$NON-NLS-1$

      tp.setLayout(new BoxLayout(tp, BoxLayout.Y_AXIS));
      
      adultBox = new JCheckBox("Registered adult"); //$NON-NLS-1$
      adultBox.setSelected(isAdult.equals("true")); //$NON-NLS-1$
      tp.add(adultBox);

      if(JConfig.queryConfiguration("prompt.ebay_synchronize", "false").equals("true")) {
        synchBox = new JCheckBox("Synchronize w/ My eBay"); //$NON-NLS-1$
        if(_my_ebay == null) {
          _my_ebay = SearchManager.getInstance().getSearchByName("My eBay");
        }
        if(doSynchronize.equals("ignore")) {
          if(_my_ebay != null) synchBox.setSelected(_my_ebay.isEnabled());
        } else {
          synchBox.setSelected(doSynchronize.equals("true")); //$NON-NLS-1$
        }
        tp.add(synchBox);
      } else {
        tp.add(new JLabel("     To have JBidwatcher regularly retrieve auctions listed on your My eBay"));
        tp.add(new JLabel("     page, go to the Search Manager and enable the search also named 'My eBay'."));
      }

      return(tp);
    }

    private JPanel buildBrowseTargetPanel() {
      JPanel tp = new JPanel();

      tp.setBorder(BorderFactory.createTitledBorder("Browse target")); //$NON-NLS-1$
      tp.setLayout(new BorderLayout());

      String curSite = JConfig.queryConfiguration(siteId + ".browse.site", "0");
      int realCurrentSite;
      try {
        realCurrentSite = Integer.parseInt(curSite);
      } catch(Exception ignore) {
        realCurrentSite = 0;
      }
      siteSelect = new JComboBox(site_choices);
      siteSelect.setSelectedIndex(realCurrentSite);
      tp.add(makeLine(new JLabel("Browse to site: "), siteSelect), BorderLayout.NORTH); //$NON-NLS-1$

      return tp;
    }

    public JConfigEbayTab() {
      setLayout(new BorderLayout());
      JPanel jp = new JPanel();
      jp.setLayout(new BorderLayout());
      jp.add(panelPack(buildCheckboxPanel()), BorderLayout.NORTH);
      jp.add(panelPack(buildUsernamePanel()), BorderLayout.CENTER);
      add(jp, BorderLayout.NORTH);
      add(panelPack(buildBrowseTargetPanel()), BorderLayout.CENTER);
    }
  }

  private static final int THREE_SECONDS = 3*Constants.ONE_SECOND;

  private class SnipeListener implements MessageQueue.Listener {
    public void messageAction(Object deQ) {
      AuctionQObject ac = (AuctionQObject) deQ; //$NON-NLS-1$
      if (ac.getCommand() == AuctionQObject.SNIPE) {
        Snipe snipe = (Snipe) ac.getData();
        int snipeResult = snipe.fire();
        switch(snipeResult) {
          case Snipe.RESNIPE:
            /**
             *  The formula for 'when' the next resnipe is, is a little complex.
             * It's all in the code, though.  If we're 3 seconds or less away,
             * give up.  Otherwise wait another 20% of the remaining time
             * (minimum of 3 seconds), and retry.
             */
            long snipeIn = snipe.getItem().getEndDate().getTime() - _etqm.getCurrentTime();
            if(snipeIn > THREE_SECONDS) {
              long retry_wait = (snipeIn / 10) * 2;
              if(retry_wait < THREE_SECONDS) retry_wait = THREE_SECONDS;

              _etqm.erase(deQ);
              _etqm.add(deQ, "snipes", _etqm.getCurrentTime()+retry_wait);
            }
            break;
          case Snipe.FAIL:
            _etqm.erase(deQ);
            //  A failed snipe is a serious, hard error, and should fall through to being removed from the snipe map.
          case Snipe.DONE:
            snipeMap.remove(snipe.getItem().getIdentifier());
            break;
          case Snipe.SUCCESSFUL:
          default:
            break;
        }
      }
    }
  }
}
