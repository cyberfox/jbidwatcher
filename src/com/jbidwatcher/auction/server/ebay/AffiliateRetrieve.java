package com.jbidwatcher.auction.server.ebay;
/*
 *
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
 * To change this template use File | Settings | File Templates.
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
    List allSteps = new ArrayList();
    String affiliate_link = Externalized.getString("ebayServer.affiliateLink") + auction_id;
    String affiliate_ref  = Externalized.getString("ebayServer.affiliateRef");
    //JConfig.setDebugging(true);
    cj.catchCookiesInRedirects();
    StringBuffer interstitial = cj.getAllCookiesAndPage(affiliate_link, affiliate_ref, false, allSteps);
    //System.out.println(interstitial);
    if(interstitial == null) return null;
    String lastStep = (String)allSteps.get(allSteps.size()-1);

    JHTML myFile = new JHTML(interstitial);
    List allLinks = myFile.getAllURLsOnPage(false);
    String redirectLink = (String)allLinks.get(0);

    //System.err.println("Last step was: " + lastStep);
    return cj.getAllCookiesAndPage(redirectLink, lastStep, false);
    //System.err.println("Final Cookies: " + cj.toString());
  }
}
