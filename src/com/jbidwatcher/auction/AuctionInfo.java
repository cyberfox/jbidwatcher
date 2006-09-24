package com.jbidwatcher.auction;
/*
 * Copyright (c) 2000-2005 CyberFOX Software, Inc. All Rights Reserved.
 *
 * This library is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Library General Public License as published
 * by the Free Software Foundation; either version 2 of the License, or (at
 * your option) any later version.
 *
 * This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Library General Public License for more
 * details.
 *
 * You should have received a copy of the GNU Library General Public License
 * along with this library; if not, write to the
 *  Free Software Foundation, Inc.
 *  59 Temple Place
 *  Suite 330
 *  Boston, MA 02111-1307
 *  USA
 */

/*
 * @brief Generic auction information, generally information that
 * needed to be retrieved.
 *
 * Program-specific information (next update time, what auction
 * server, etc.) is stored in AuctionEntry
 */

import com.jbidwatcher.config.JConfig;
import com.jbidwatcher.xml.XMLElement;
import com.jbidwatcher.xml.XMLSerializeSimple;
import com.jbidwatcher.util.*;

import java.io.*;
import java.util.Date;

public abstract class AuctionInfo extends XMLSerializeSimple {
  protected String _identifier=null;
  protected Currency _curBid=Currency.NoValue();
  protected Currency _minBid=Currency.NoValue();
  protected Currency _shipping=Currency.NoValue();
  protected Currency _insurance=Currency.NoValue();
  protected Currency _us_cur= Currency.NoValue();
  protected Currency _buy_now_us=Currency.NoValue();
  protected boolean _insurance_optional=true;
  protected boolean _fixed_price=false;
  protected boolean _no_thumbnail=false;
  protected Currency _buy_now=Currency.NoValue();
  protected Date _start, _end;
  protected String _seller, _highBidder, _title, _highBidderEmail;
  protected int _quantity, _numBids;
  protected boolean _isDutch, _isReserve, _isPrivate, _reserveMet;
  protected boolean _hasThumb = false;
  protected boolean _outbid = false;

  protected int _feedback = 0;
  protected String _postivePercentage = "";
  protected String _itemLocation = "";
  protected boolean _paypal = false;

  protected static final sun.misc.BASE64Decoder b64dec = new sun.misc.BASE64Decoder();
  protected static final sun.misc.BASE64Encoder b64enc = new sun.misc.BASE64Encoder();
  protected GZip _loadedPage = null;

  /**
   * @brief Empty constructor, for XML parsing.
   *
   */
  public AuctionInfo() {}

  /** 
   * @brief Construct a somewhat complete AuctionInfo object with all
   * the important values set.
   * 
   * @param auctionTitle - The title of the auction.
   * @param auctionSeller - The seller's username for the auction.
   * @param auctionHighBidder - The current high bidder, if any.
   * @param auctionCurBid - The current/lowest/starting bid.
   * @param auctionStart - The start time for the auction (if available).
   * @param auctionEnd - The end time for the auction.
   * @param auctionBidCount - The number of bids that have been placed so far.
   */
  public AuctionInfo(String auctionTitle, String auctionSeller, String auctionHighBidder,
                     Currency auctionCurBid, Date auctionStart, Date auctionEnd, int auctionBidCount) {
    _title = auctionTitle.trim();
    _highBidder = auctionHighBidder.trim();
    _seller = auctionSeller.trim();

    _start = auctionStart;
    _end = auctionEnd;

    _curBid = auctionCurBid;
    _numBids = auctionBidCount;
  }

  protected String[] infoTags = { "title", "seller", "highbidder", "bidcount", "start", "end",
                                "currently", "dutch", "reserve", "private", "content",
                                "shipping", "insurance", "buynow", "usprice", "fixed", "minimum",
                                "paypal", "location", "feedback", "percentage"};
  protected String[] getTags() { return infoTags; }

  protected void handleTag(int i, XMLElement curElement) {
    switch(i) {
      case 0:
        if(JConfig.queryConfiguration("savefile.format", "0100").compareTo("0101") >= 0) {
          _title = curElement.decodeString(curElement.getContents(), 0);
        } else {
          _title = curElement.getContents();
        }
        break;
      case 1:
        _seller = curElement.getContents();
        break;
      case 2:
        _highBidder = curElement.getContents();
        break;
      case 3:
        _numBids = Integer.parseInt(curElement.getContents());
        break;
      case 4:
        _start = new Date(Long.parseLong(curElement.getContents()));
        break;
      case 5:
        _end = new Date(Long.parseLong(curElement.getContents()));
        break;
      case 6:
        _curBid = Currency.getCurrency(curElement.getProperty("CURRENCY"), curElement.getProperty("PRICE"));
        break;
      case 7:
        _isDutch = true;
        _quantity = Integer.parseInt(curElement.getProperty("QUANTITY"));
        break;
      case 8:
        _isReserve = true;
        _reserveMet = "true".equals(curElement.getProperty("MET"));
        break;
      case 9:
        _isPrivate = true;
        break;
      case 10:
        String isCompressed = curElement.getProperty("COMPRESSED");

        try {
          if(isCompressed == null || !isCompressed.equals("true")) {
            setRealContent(b64dec.decodeBuffer(curElement.getContents()), true);
          } else {
            setRealContent(GZip.uncompress(b64dec.decodeBuffer(curElement.getContents()), false), true);
          }
        } catch(IOException e) {
          ErrorManagement.handleException("handleTag failed to load content.", e);
        }
        _loadedPage = null;
        break;
      case 11:  //  Shipping
        _shipping = Currency.getCurrency(curElement.getProperty("CURRENCY"), curElement.getProperty("PRICE"));
        break;
      case 12:  //  Insurance
        _insurance = Currency.getCurrency(curElement.getProperty("CURRENCY"), curElement.getProperty("PRICE"));
        _insurance_optional = curElement.getProperty("OPTIONAL") == null || (curElement.getProperty("OPTIONAL").equals("true"));
        break;
      case 13:  //  Buy Now
        _buy_now = Currency.getCurrency(curElement.getProperty("CURRENCY"), curElement.getProperty("PRICE"));
        break;
      case 14:  //  Buy Now
        _us_cur = Currency.getCurrency(curElement.getProperty("CURRENCY"), curElement.getProperty("PRICE"));
        break;
      case 15:  //  Fixed price
        _fixed_price = true;
        break;
      case 16: //  Minimum price/bid
        _minBid = Currency.getCurrency(curElement.getProperty("CURRENCY"), curElement.getProperty("PRICE"));
        break;
      case 17: //  PayPal accepted
        _paypal = true;
        break;
      case 18: //  Location of item
        _itemLocation = curElement.getContents();
        break;
      case 19: //  Feedback score
        _feedback = Integer.parseInt(curElement.getContents());
        break;
      case 20: //  Postive feedback percentage (w/o the % sign)
        _postivePercentage = curElement.getContents();
        break;
      default:
        break;
        // commented out for FORWARDS compatibility.
        //        throw new RuntimeException("Unexpected value when handling AuctionInfo tags!");
    }
  }

  public XMLElement toXML() {
    XMLElement xmlResult = new XMLElement("info");
    XMLElement xtitle, xseller, xbidcount, xstart, xend, xcontent;
    XMLElement xcurrently, xhighbidder, xdutch, xreserve, xprivate;
    XMLElement xshipping, xinsurance, xbuynow, xusprice, xfixed, xminbid;
    XMLElement xlocation, xpospercent, xfeedback, xpaypal;

    xtitle = new XMLElement("title");
    xtitle.setContents(XMLElement.encodeString(_title));
    xmlResult.addChild(xtitle);

    xseller = new XMLElement("seller");
    xseller.setContents(_seller);
    xmlResult.addChild(xseller);

    xstart = new XMLElement("start");
    xstart.setContents(Long.toString(_start.getTime()));
    xmlResult.addChild(xstart);

    xend = new XMLElement("end");
    xend.setContents(Long.toString(_end.getTime()));
    xmlResult.addChild(xend);

    xbidcount = new XMLElement("bidcount");
    xbidcount.setContents(Integer.toString(_numBids));
    xmlResult.addChild(xbidcount);

    if(_loadedPage != null) {
      String bigEncode;

      xcontent = new XMLElement("content");
      xcontent.setProperty("compressed", "true");
      bigEncode = b64enc.encode(_loadedPage.getCompressedData());
      xcontent.setContents(bigEncode);
      xmlResult.addChild(xcontent);
    }

    xcurrently = new XMLElement("currently");
    xcurrently.setEmpty();
    xcurrently.setProperty("currency", _curBid.fullCurrencyName());
    xcurrently.setProperty("price", Double.toString(_curBid.getValue()));
    xmlResult.addChild(xcurrently);

    if(_shipping != null) {
      xshipping = new XMLElement("shipping");
      xshipping.setEmpty();
      xshipping.setProperty("currency", _shipping.fullCurrencyName());
      xshipping.setProperty("price", Double.toString(_shipping.getValue()));
      xmlResult.addChild(xshipping);
    }

    if(_insurance != null) {
      xinsurance = new XMLElement("insurance");
      xinsurance.setEmpty();
      xinsurance.setProperty("currency", _insurance.fullCurrencyName());
      xinsurance.setProperty("price", Double.toString(_insurance.getValue()));
      xinsurance.setProperty("optional", _insurance_optional?"true":"false");
      xmlResult.addChild(xinsurance);
    }

    if(_buy_now != null) {
      xbuynow = new XMLElement("buynow");
      xbuynow.setEmpty();
      xbuynow.setProperty("currency", _buy_now.fullCurrencyName());
      xbuynow.setProperty("price", Double.toString(_buy_now.getValue()));
      xmlResult.addChild(xbuynow);
    }

    if(_curBid.getCurrencyType() != Currency.US_DOLLAR &&
       _us_cur != null && !_us_cur.isNull()) {
      xusprice = new XMLElement("usprice");
      xusprice.setEmpty();
      xusprice.setProperty("currency", _us_cur.fullCurrencyName());
      xusprice.setProperty("price", Double.toString(_us_cur.getValue()));
      xmlResult.addChild(xusprice);
    }

    if(_minBid != null && !_minBid.isNull()) {
      xminbid = new XMLElement("minimum");
      xminbid.setEmpty();
      xminbid.setProperty("currency", _minBid.fullCurrencyName());
      xminbid.setProperty("price", Double.toString(_minBid.getValue()));
      xmlResult.addChild(xminbid);
    }

    if(_highBidder != null) {
      xhighbidder = new XMLElement("highbidder");
      xhighbidder.setContents(_highBidder);
      xmlResult.addChild(xhighbidder);
    }

    if(_isDutch) {
      xdutch = new XMLElement("dutch");
      xdutch.setEmpty();
      xdutch.setProperty("quantity", Integer.toString(_quantity));
      xmlResult.addChild(xdutch);
    }

    if(_isReserve) {
      xreserve = new XMLElement("reserve");
      xreserve.setEmpty();
      xreserve.setProperty("met", _reserveMet?"true":"false");
      xmlResult.addChild(xreserve);
    }

    if(_isPrivate) {
      xprivate = new XMLElement("private");
      xprivate.setEmpty();
      xmlResult.addChild(xprivate);
    }

    if(_fixed_price) {
      xfixed = new XMLElement("fixed");
      xfixed.setEmpty();
      xmlResult.addChild(xfixed);
    }

    if(_paypal) {
      xpaypal = new XMLElement("paypal");
      xpaypal.setEmpty();
      xmlResult.addChild(xpaypal);
    }

    if(_itemLocation != null && !_itemLocation.equals("")) {
      xlocation = new XMLElement("location");
      xlocation.setContents(_itemLocation);
      xmlResult.addChild(xlocation);
    }

    if(_feedback != 0) {
      xfeedback = new XMLElement("feedback");
      xfeedback.setContents(Integer.toString(_feedback));
      xmlResult.addChild(xfeedback);
    }

    if(_postivePercentage != null && !_postivePercentage.equals("")) {
      xpospercent = new XMLElement("percentage");
      xpospercent.setContents(_postivePercentage);
      xmlResult.addChild(xpospercent);
    }

    return xmlResult;
  }

  public void setThumbnail(ByteBuffer newThumb) {
    if (newThumb == null) {
      _no_thumbnail = true;
    } else {
      saveThumbnail(newThumb);
    }
  }

  private void saveThumbnail(ByteBuffer thumbnail) {
    getValidImagePath(thumbnail);
  }

  private String getValidImagePath(ByteBuffer buf) {
    String outPath = JConfig.queryConfiguration("auctions.savepath");
    String basePath = outPath + System.getProperty("file.separator") + _identifier;
    String thumbPath = basePath + "_t.jpg";
    String imgPath = thumbPath;
    if(buf != null) buf.save(basePath + ".jpg");
    File f = new File(thumbPath);

    if (!f.exists()) {
      File img = new File(basePath + ".jpg");
      if(!img.exists()) { return null; }
      String badConversionPath = basePath + "_b.jpg";
      File conversionAttempted = new File(badConversionPath);
      imgPath = basePath + ".jpg";

      if (!conversionAttempted.exists()) {
        String maxWidthString = JConfig.queryConfiguration("thumbnail.maxWidth", "256");
        String prefWidthString = JConfig.queryConfiguration("thumbnail.prefWidth", "128");
        String maxHeightString = JConfig.queryConfiguration("thumbnail.maxHeight", "256");
        String prefHeightString = JConfig.queryConfiguration("thumbnail.prefWidth", "128");
        int maxWidth = Integer.parseInt(maxWidthString);
        int prefWidth = Integer.parseInt(prefWidthString);
        int maxHeight = Integer.parseInt(maxHeightString);
        int prefHeight = Integer.parseInt(prefHeightString);
        if (IconFactory.resizeImage(imgPath, thumbPath, maxWidth, prefWidth, maxHeight, prefHeight)) {
          imgPath = thumbPath;
        } else {
          try {
            //  Create a mark file that notes that the thumbnail was
            //  attempted to be created, and failed.  It'll default to
            //  using the standard image file.
            conversionAttempted.createNewFile();
          } catch (IOException e) {
            ErrorManagement.handleException("Can't create 'bad' lock file.", e);
          }
        }
      }
    }
    return imgPath;
  }

  public void save() {
    String outPath = JConfig.queryConfiguration("auctions.savepath");
    if(outPath != null) {
      if(JConfig.queryConfiguration("store.auctionHTML", "true").equals("true")) {
        String filePath = outPath + System.getProperty("file.separator") + _identifier + ".html.gz";

        if(_loadedPage != null) {
          _loadedPage.save(filePath);
        }
      }
    }
    _loadedPage = null;
  }

  public boolean hasThumbnail() {
    String imgPath = getValidImagePath(null);
    if(imgPath == null) return false;
    File tester = new File(imgPath);

    return tester.exists();
  }

  public String getThumbnail() {
    //  Bad optimization -- BUGBUG -- mrs: 21-March-2004 18:28
    //  If it doesn't have a thumbnail, we check.
    if(!_hasThumb) {
      if(!hasThumbnail()) return null;
    }

    _hasThumb = true;

    String imgPath = getValidImagePath(null);

    return "file:" + imgPath;
  }

  private GZip loadFile(String fileName) {
    File fp = new File(fileName);
    GZip localZip = new GZip();

    if(fp.exists()) {
      //  Okay, I don't allow loading auction data that's over 512K.  Duh.
      if(fp.length() < 512 * 1024) {
        try {
          ErrorManagement.logDebug("Loading from backing page (file is " + fp.length() + " bytes)!");
          localZip.load(fp);
        } catch(IOException ioe) {
          ErrorManagement.handleException("Couldn't read " + fileName, ioe);
          return null;
        }

        return localZip;
      } else {
        ErrorManagement.logDebug("Can't load " + fileName + ", file is too large.");
      }
    }
    return null;
  }

  public void setRealContent(StringBuffer changedContent, boolean final_data) {
    if(changedContent != null) {
      byte[] localBytes = changedContent.toString().getBytes();
      setRealContent(localBytes, final_data);
    }
  }

  public void setRealContent(byte[] changedContent, boolean final_data) {
    String outPath = JConfig.queryConfiguration("auctions.savepath");

    if(changedContent != null) {
      _loadedPage = new GZip();
      _loadedPage.setData(changedContent);

      if(outPath != null) {
        if(final_data) {
          String filePath = outPath + System.getProperty("file.separator") + _identifier + ".html.gz";
          _loadedPage.save(filePath);
          _loadedPage = null;
        }
      }
    }
  }

  public GZip getRealContent() {
    String outPath = JConfig.queryConfiguration("auctions.savepath");
    if(outPath != null) {
      String filePath = outPath + System.getProperty("file.separator") + _identifier + ".html.gz";
      ErrorManagement.logDebug("filePath = " + filePath);
      return loadFile(filePath);
    }
    return _loadedPage;
  }

  public void setContent(StringBuffer inContent, boolean final_data) {
    setRealContent(inContent, final_data);
  }

  public StringBuffer getContent() {
    StringBuffer sb;

    if(_loadedPage != null) {
      StringBuffer outSB = _loadedPage.getUncompressedData(false);
      if(outSB == null) outSB = new StringBuffer("_loadedPage.getUncompressedData is null");
      sb = outSB;
    } else {
      ErrorManagement.logDebug("_loadedPage is null, returning the 'real' cached copy!");
      GZip gz = getRealContent();
      if(gz != null) {
        sb = gz.getUncompressedData();
        ErrorManagement.logDebug("Turned the uncompressed data into a StringBuffer!");
        if(sb == null) ErrorManagement.logDebug(" Failed to uncompress for id " + _identifier);
      } else {
        sb = new StringBuffer("Error getting real content.");
      }
    }
    return(sb);
  }

  public void setIdentifier(String id) { _identifier = id; }
  public String getIdentifier() { return _identifier; }

  public Currency getCurBid() { return(_curBid); }
  public Currency getUSCurBid() { if(_us_cur == null || _us_cur.isNull()) return Currency.NoValue(); else return(_us_cur); }
  public Currency getMinBid() { if(_minBid != null) return(_minBid); else return _curBid; }

  public Currency getShipping() { return _shipping; }
  public Currency getInsurance() { return _insurance; }
  public boolean getInsuranceOptional() { return _insurance_optional; }
  public Currency getBuyNow() { return _buy_now; }

  public int getQuantity() { return(_quantity); }
  public int getNumBidders() { return(_numBids); }

  public String getSeller() { return(_seller); }
  public String getHighBidder() { return(_highBidder); }
  public String getHighBidderEmail() { return(_highBidderEmail); }
  public String getTitle() { return(_title); }

  public Date getStartDate() { return(_start); }
  public Date getEndDate() { return(_end); }

  public boolean isDutch() { return(_isDutch); }
  public boolean isReserve() { return(_isReserve); }
  public boolean isPrivate() { return(_isPrivate); }

  public boolean isFixed() { return(_fixed_price); }

  public boolean isReserveMet() { return(_reserveMet); }

  public boolean isOutbid() { return(_outbid); }

  public boolean hasPaypal() { return _paypal; }
  public String getItemLocation() { return _itemLocation; }
  public String getPostiveFeedbackPercentage() { return _postivePercentage; }
  public int getFeedbackScore() { return _feedback; }

  public abstract ByteBuffer getSiteThumbnail();

  public abstract ByteBuffer getAlternateSiteThumbnail();
}
