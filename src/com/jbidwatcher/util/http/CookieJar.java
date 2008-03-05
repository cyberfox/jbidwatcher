package com.jbidwatcher.util.http;
/*
 * Copyright (c) 2000-2007, CyberFOX Software, Inc. All Rights Reserved.
 *
 * Developed by mrs (Morgan Schweers)
 */

import com.jbidwatcher.util.config.ErrorManagement;

import java.net.*;
import java.util.*;
import java.io.IOException;

public class CookieJar {
  private Map<String, Cookie> _cookies;
  private boolean m_ignore_redirect_cookies = true;
  private final static boolean do_uber_debug = false;

  public CookieJar() {
    _cookies = new TreeMap<String, Cookie>();
  }

  public void ignoreCookiesInRedirects() {
    m_ignore_redirect_cookies = true;
  }

  public void catchCookiesInRedirects() {
    m_ignore_redirect_cookies = false;
  }

  public Cookie getCookie(String keyName) {
    return _cookies.get(keyName);
  }

  /**
   * Get all the cookies and the page data itself, using post.
   *
   * @param pageName - The URL and CGI to retrieve (the CGI is seperated out and sent as the POST).
   *
   * @return - A StringBuffer containing the data at the provided URL, or returned from the POST operation.
   *
   * @throws com.jbidwatcher.util.http.CookieJar.CookieException - If the connection is refused.
   */
  public StringBuffer getAllCookiesAndPage(String pageName) throws CookieException {
    return getAllCookiesAndPage(pageName, null, true);
  }

  /**
   * Get all the cookies and the page data itself, using post.
   *
   * @param pageName - The URL and CGI to retrieve (the CGI is seperated out and sent as the POST).
   * @param referer - The URL that referred us to this page (can be null).
   * @return - A StringBuffer containing the data at the provided URL, or returned from the POST operation.
   *
   * @throws com.jbidwatcher.util.http.CookieJar.CookieException - If the connection is refused.
   */
  public StringBuffer getAllCookiesAndPage(String pageName, String referer) throws CookieException {
    return getAllCookiesAndPage(pageName, referer, true);
  }

  /**
   * Get all the cookies and the page data itself, using post or get.
   *
   * @param pageName - The URL and CGI to retrieve.
   * @param referer  - The URL that referred us to this page (can be null).
   * @param doPost   - Whether or not to use POST to send any CGI associated with the pageName.
   *
   * @return - A StringBuffer containing the data at the provided URL, or returned from the POST operation.
   *
   * @throws com.jbidwatcher.util.http.CookieJar.CookieException - If the connection is refused.
   */
  public StringBuffer getAllCookiesAndPage(String pageName, String referer, boolean doPost) throws CookieException {
    return getAllCookiesAndPage(pageName, referer, doPost, null);
  }

  public class CookieException extends Exception {
    public CookieException(String text, Throwable trigger) {
      super(text, trigger);
    }
  }

  public StringBuffer getAllCookiesAndPage(String pageName, String referer, boolean doPost, List<String> pages) throws CookieException {
    URLConnection uc = getAllCookiesFromPage(pageName, referer, doPost, pages);
    if(uc == null) return null;

    StringBuffer sb = null;

    try {
      sb = Http.receivePage(uc);
    } catch(ConnectException ce) {
      logException(pageName, ce);
      if(ce.toString().indexOf("Connection refused") != -1) {
        throw new CookieException("Connection refused", ce);
      }
    } catch(IOException e) {
      logException(pageName, e);
      return null;
    }

    return sb;
  }

  private void logException(String pageName, Exception e) {
    String errmsg;
    int qLoc = pageName.indexOf('?');

    errmsg = "Error loading page: ";
    if(qLoc == -1) {
      errmsg += pageName;
    } else {
      errmsg += pageName.substring(0,qLoc);
    }
    ErrorManagement.handleException(errmsg, e);
  }

  /**
   * Retrieve any cookies from the provided page via GET or POST, but only return
   * the URLConnection letting the caller do what they want to with it.
   *
   * @param pageName - The page to load.
   * @param referer  - The page that referred us to this page, can be null.
   * @param post     - Use 'post' or 'get' (true == use 'post').
   * @return - A URLConnection connected to the response from the server for the given request.
   */
  public URLConnection getAllCookiesFromPage(String pageName, String referer, boolean post) {
    return getAllCookiesFromPage(pageName, referer, post, null);
  }
  public URLConnection getAllCookiesFromPage(String pageName, String referer, boolean post, List<String> pages) {
    String sendRequest = pageName;

    if(pages != null) pages.add(pageName);
    String cgi = null;
    if(post) {
      int cgipos = pageName.indexOf("?");
      if (cgipos >= 0) {
        cgi = pageName.substring(cgipos + 1);
        sendRequest = pageName.substring(0, cgipos);
      } else {
        post = false;
      }
    }

    HttpURLConnection uc = initiateRequest(post, sendRequest, cgi, referer);

    if(uc != null) {
      String redirect = handleRedirect(uc, pageName);

      if(redirect != null) {
        if (do_uber_debug) {
          com.jbidwatcher.util.config.ErrorManagement.logDebug("Redirecting to: " + redirect);
        }
        return getAllCookiesFromPage(redirect, referer, post, pages);
      }
    }

    return uc;
  }

  private String handleRedirect(HttpURLConnection uc, String pageName) {
    int i = 1;
    String redirect = null;
    String nextKey;
    do {
      nextKey = uc.getHeaderFieldKey(i);
      if(nextKey != null) {
        if(do_uber_debug) {
          com.jbidwatcher.util.config.ErrorManagement.logDebug(nextKey+": " + uc.getHeaderField(i));
        }
        //  If we're redirected, shortcut to loading the new page.
        if(nextKey.startsWith("Location") ||
           nextKey.startsWith("location")) {
          redirect = uc.getHeaderField(i);
          redirect = stripQuotedAmpersands(redirect);
          if(!redirect.startsWith("http")) {
            redirect = fixRelativeRedirect(pageName, redirect);
          }
        }

        if(nextKey.startsWith("Set-Cookie") ||
           nextKey.startsWith("Set-cookie")) {
          Cookie newCookie = new Cookie(uc.getHeaderField(i));
          _cookies.put(newCookie.getKey(), newCookie);
        }
      }
      i++;
    } while(nextKey != null);
    return redirect;
  }

  private String fixRelativeRedirect(String pageName, String redirect) {
    String prefix;
    String slash = "";
    int serverEnd = pageName.indexOf(".com/");
    if(!redirect.startsWith("/")) {
      slash = "/";
    }
    if(serverEnd == -1) {
      prefix = pageName;
    } else {
      prefix = pageName.substring(0, serverEnd+4);
    }
    redirect = prefix + slash + redirect;
    return redirect;
  }

  private String stripQuotedAmpersands(String redirect) {
    int amploc = redirect.indexOf("&amp;");

    while(amploc != -1) {
      redirect = redirect.substring(0, amploc) + "&" +
                 redirect.substring(amploc+5);
      amploc = redirect.indexOf("&amp;");
    }
    return redirect;
  }

  private HttpURLConnection initiateRequest(boolean post, String sendRequest, String cgi, String referer) {
    HttpURLConnection uc;

    if(_cookies.size() > 0) {
      if(post) {
        uc = (HttpURLConnection) Http.postFormPage(sendRequest, cgi, this.toString(), referer, m_ignore_redirect_cookies);
      } else {
        uc = (HttpURLConnection)Http.getPage(sendRequest, this.toString(), referer, m_ignore_redirect_cookies);
      }
    } else {
      if(post) {
        uc = (HttpURLConnection)Http.postFormPage(sendRequest, cgi, null, referer, m_ignore_redirect_cookies);
      } else {
        uc = (HttpURLConnection)Http.getPage(sendRequest, null, referer, m_ignore_redirect_cookies);
      }
    }
    return uc;
  }

  public String toString() {
    boolean firstThrough = true;
    StringBuffer outBuf = null;
    Cookie stepper;

    for (Cookie cookie : _cookies.values()) {
      stepper = cookie;
      if (!stepper.getValue().equals("")) {
        if (!firstThrough) {
          outBuf.append("; ");
        } else {
          firstThrough = false;
          outBuf = new StringBuffer();
        }
        outBuf.append(stepper.getKey());
        outBuf.append("=");
        outBuf.append(stepper.getValue());
      }
    }

    if(outBuf != null) {
      return outBuf.toString();
    } else {
      return null;
    }
  }
}
