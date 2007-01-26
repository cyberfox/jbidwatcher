package com.jbidwatcher.auction.server.ebay;
/*
 * Copyright (c) 2000-2007, CyberFOX Software, Inc. All Rights Reserved.
 *
 * Developed by mrs (Morgan Schweers)
 */

import com.jbidwatcher.util.html.JHTML;
import com.jbidwatcher.util.http.CookieJar;
import com.jbidwatcher.util.ErrorManagement;
import com.jbidwatcher.util.Externalized;

import java.util.List;
import java.util.ArrayList;

/**
 * Created by IntelliJ IDEA.
 * User: mrs
 * Date: Nov 5, 2004
 * Time: 11:56:56 AM
 * 
 */
public class AffiliateRetrieve {
  public static void main(String[] args) {
    CookieJar cj = new CookieJar();
    try {
      if (args.length == 0) {
        //  Error out.
      } else {
        StringBuffer auction_text = getAuctionViaAffiliate(cj, args[0]);
        ErrorManagement.logFile("Received via affiliate load", auction_text);
      }
    } catch (CookieJar.CookieException ignore) {
      //  Ignore the result here, we don't care that much.
    }
  }

  private AffiliateRetrieve() { }
  public static StringBuffer getAuctionViaAffiliate(CookieJar cj, String auction_id) throws CookieJar.CookieException {
    List<String> allSteps = new ArrayList<String>();
    String affiliate_link = Externalized.getString("ebayServer.affiliateLink") + auction_id;
    String affiliate_ref  = Externalized.getString("ebayServer.affiliateRef");
    //JConfig.setDebugging(true);
    cj.catchCookiesInRedirects();
    StringBuffer interstitial = cj.getAllCookiesAndPage(affiliate_link, affiliate_ref, false, allSteps);
    //System.out.println(interstitial);
    if(interstitial == null) return null;
    String lastStep = allSteps.get(allSteps.size()-1);

    JHTML myFile = new JHTML(interstitial);
    List<String> allLinks = myFile.getAllURLsOnPage(false);
    String redirectLink = allLinks.get(0);

    //System.err.println("Last step was: " + lastStep);
    return cj.getAllCookiesAndPage(redirectLink, lastStep, false);
    //System.err.println("Final Cookies: " + cj.toString());
  }
}
