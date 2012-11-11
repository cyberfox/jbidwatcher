package com.jbidwatcher.auction;

/*
 * Copyright (c) 2000-2007, CyberFOX Software, Inc. All Rights Reserved.
 *
 * Developed by mrs (Morgan Schweers)
 */

import com.jbidwatcher.util.html.*;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

public abstract class SpecificAuction extends AuctionInfo implements CleanupHandler
{
  protected JHTML mDocument;
  protected Document mDocument2;

  private String mURL;
  public void setURL(String URL) { mURL = URL; }
  public String getURL() { return mURL; }

  public enum ParseErrors {
    SUCCESS,
    NOT_ADULT,
    BAD_TITLE,
    SELLER_AWAY,
    CAPTCHA,
    DELETED,
    WRONG_SITE
  }

  public abstract ParseErrors parseAuction(AuctionEntry ae);

  protected void finish() {
    mDocument = null;
    mDocument2 = null;
  }

  public boolean preParseAuction() {
    StringBuffer sb = getContent();
    if(sb == null) return(false);

    mDocument2 = Jsoup.parse(sb.toString());
    cleanup(sb);

    mDocument = new JHTML(sb);

    return true;
  }

  protected boolean doesLabelExist(String label) {
    return (mDocument.lookup(label, false) != null);
  }

  protected boolean doesLabelPrefixExist(String label) {
    return (mDocument.find(label, false) != null);
  }
}
