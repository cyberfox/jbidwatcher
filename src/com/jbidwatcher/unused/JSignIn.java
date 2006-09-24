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

import com.jbidwatcher.config.JConfig;
import com.jbidwatcher.util.html.JHTML;
import com.jbidwatcher.util.http.Http;
import com.jbidwatcher.util.http.CookieJar;

import java.util.List;
import java.util.Iterator;
import java.net.URLConnection;
import java.io.IOException;

/**
 * Created by IntelliJ IDEA.
 * User: mrs
 * Date: Sep 27, 2004
 * Time: 5:54:44 PM
 * To change this template use File | Settings | File Templates.
 */
public class JSignIn {
  public static void main(String args[]) {
    if(args.length != 2) {
      System.err.println("Usage: \n\tJSignIn {username} {password}");
      System.exit(0);
    }

    JConfig.setDebugging(true);

    CookieJar cj = signIn(args[0], args[1]);
    System.err.println("CJ == " + cj);
  }

  public static CookieJar signIn(String username, String password) {
    boolean isAdult = true;
    CookieJar cj = new CookieJar();
    String startURL = "https://signin.ebay.com/ws2/eBayISAPI.dll?SignIn";
    if (isAdult) {
      startURL = "https://signin.ebay.com/ws2/eBayISAPI.dll?AdultLoginShow";
    }
    //  Get the basic cookie set to start the login process.
    URLConnection uc_signin = cj.getAllCookiesFromPage(startURL, null, false);
    try {
      //  Load and parse the page, finding the necessary forms.
      StringBuffer signin = Http.receivePage(uc_signin);
      JHTML htdoc = new JHTML(signin);
      List forms = htdoc.getForms();
      Iterator it = forms.iterator();

      //  Step through the forms, looking for one with a password input field.
      while (it.hasNext()) {
        JHTML.Form curForm = (JHTML.Form) it.next();

        if (curForm.hasInput("pass")) {
          //  If it has a password field, this is the input form.
          curForm.setText("userid", username);
          curForm.setText("pass", password);
          //  Now, sign in, and get the resultant cookies...
          uc_signin = cj.getAllCookiesFromPage(curForm.getCGI(), null, false);
          if (isAdult) {
            getAdultRedirector(uc_signin, cj);
          } else {
            StringBuffer confirm = Http.receivePage(uc_signin);
            //  If it's null, return whatever we got up to here.
            if(confirm == null) return cj;
          }
        }
      }
    } catch (IOException e) {
      e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
      return null;
    }

    return cj;
  }

  private static void getAdultRedirector(URLConnection uc_signin, CookieJar cj) throws IOException {
    //  Get THAT page, which is actually (usually) a 'redirector' page with a meta-refresh
    //  and a clickable link in case meta-refresh doesn't work.
    StringBuffer signed_in = Http.receivePage(uc_signin);

    //  Parse the redirector, and find the URL that points to the adult
    //  confirmation page.
    JHTML redirector = new JHTML(signed_in);
    List allURLs = redirector.getAllURLsOnPage(false);
    Iterator urlIt = allURLs.iterator();
    while (urlIt.hasNext()) {
      String url = (String) urlIt.next();
      //  If this URL has 'Adult' in its text someplace, that's the one we want.
      if (url.indexOf("Adult") != -1) {
        //  Replace nasty quoted amps with single-amps.
        url = url.replaceAll("&amp;", "&");
        //  Now get the actual 'You promise you're an adult?' page...
        uc_signin = cj.getAllCookiesFromPage(url, null, false);

        getAdultConfirmation(uc_signin, cj);
      }
    }
  }

  private static void getAdultConfirmation(URLConnection uc_signin, CookieJar cj) throws IOException {
    StringBuffer confirm = Http.receivePage(uc_signin);
    JHTML confirmPage = new JHTML(confirm);
    List confirm_forms = confirmPage.getForms();
    Iterator confirmIt = confirm_forms.iterator();
    while (confirmIt.hasNext()) {
      JHTML.Form finalForm = (JHTML.Form) confirmIt.next();
      if (finalForm.hasInput("userid")) {
        uc_signin = cj.getAllCookiesFromPage(finalForm.getCGI(), null, false);
        Http.receivePage(uc_signin);
      }
    }
  }
}
