package com.jbidwatcher.unused;
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

import com.jbidwatcher.util.html.JHTML;
import com.jbidwatcher.util.http.CookieJar;

import java.util.List;
import java.io.UnsupportedEncodingException;

/**
 * Created by IntelliJ IDEA.
 * User: mrs
 * Date: Nov 4, 2004
 * Time: 3:03:07 PM
 * To change this template use File | Settings | File Templates.
 */
public class CBOERetrieve {
  public static void main(String args[]) {
    String referer = "http://www.cboe.com/delayedQuote/QuoteTableDownload.aspx";
//    JConfig.setDebugging(true);
    CookieJar cj = new CookieJar();
    StringBuffer cboe = null;
    try {
      cboe = cj.getAllCookiesAndPage("http://www.cboe.com/delayedQuote/QuoteTableDownload.aspx", referer);
    } catch (CookieJar.CookieException e) {
      e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
    }
    JHTML myFile = new JHTML(cboe);
    List allForms = myFile.getForms();
    JHTML.Form mainForm = (JHTML.Form) allForms.get(0);

    if(args.length == 0) {
      mainForm.setText("txtTicker", "ebay");
    } else {
      mainForm.setText("txtTicker", args[0]);
    }

    try {
      String postText = mainForm.getCGI();
      StringBuffer result = cj.getAllCookiesAndPage("http://www.cboe.com/delayedQuote/" + postText, referer);
      System.err.println(result);
    } catch(UnsupportedEncodingException uee) {
      System.err.println("Couldn't get the form CGI, as UTF-8 is unsupported.");
    } catch (CookieJar.CookieException e) {
      e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
    }
  }
}
