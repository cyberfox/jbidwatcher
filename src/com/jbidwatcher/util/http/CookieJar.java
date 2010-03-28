package com.jbidwatcher.util.http;
/*
 * Copyright (c) 2000-2007, CyberFOX Software, Inc. All Rights Reserved.
 *
 * Developed by mrs (Morgan Schweers)
 */

import com.jbidwatcher.util.config.JConfig;

import java.net.*;
import java.util.*;
import java.io.IOException;

public class CookieJar {
  private Map<String, Cookie> mCookies;
  private boolean mIgnoreRedirectCookies = false;
  private final static boolean sUberDebug = false;

  public CookieJar() {
    mCookies = new TreeMap<String, Cookie>();
  }

  public Cookie getCookie(String keyName) {
    return mCookies.get(keyName);
  }

  public String dump() {
    StringBuffer rval = new StringBuffer();
    for(String cookieName : mCookies.keySet()) {
      Cookie value = mCookies.get(cookieName);
      rval.append(cookieName).append(": ").append(value.getValue()).append('\n');
    }
    return rval.toString();
  }

  public static class CookieException extends Exception {
    public CookieException(String text, Throwable trigger) {
      super(text, trigger);
    }
  }

  public static class CookieRedirectException extends RuntimeException {
    public CookieRedirectException(String text, Throwable trigger) {
      super(text, trigger);
    }
  }

  /**
   * Get all the cookies and the page data itself, using post or get.
   *
   * @param page     - The URL to retrieve.
   * @param body     - The body of the POST to send, if we want to do a POST operation.
   * @param referer  - The URL that referred us to this page (can be null).
   * @return - A StringBuffer containing the data at the provided URL, or returned from the POST operation.
   *
   * @throws com.jbidwatcher.util.http.CookieJar.CookieException - If the connection is refused.
   */
  public StringBuffer getPage(String page, String body, String referer) throws CookieException {
    URLConnection uc = connect(page, body, referer, body != null, null);
    if(uc == null) return null;

    StringBuffer sb = null;

    try {
      sb = Http.net().receivePage(uc);
    } catch(ConnectException ce) {
      logException(page, ce);
      if(ce.toString().indexOf("Connection refused") != -1) {
        throw new CookieException("Connection refused", ce);
      }
    } catch(IOException e) {
      logException(page, e);
      return null;
    }

    return sb;
  }

  private void logException(String pageName, Exception e) {
    int qLoc = pageName.indexOf('?');

    String errmsg = "Error loading page: ";
    if(qLoc == -1) {
      errmsg += pageName;
    } else {
      errmsg += pageName.substring(0,qLoc);
    }
    JConfig.log().handleException(errmsg, e);
  }

  /**
   * Retrieve any cookies from the provided page via GET or POST, but only return
   * the URLConnection letting the caller do what they want to with it.
   *
   * @param page - The page to load.
   * @return - A URLConnection connected to the response from the server for the given request.
   */
  public URLConnection connect(String page) {
    return connect(page, null, null, false, null);
  }

  private Map<String, Integer> mRedirections;

  public URLConnection connect(String page, String body, String referer, boolean post, List<String> pages) {
    URLConnection rval;
    mRedirections = new HashMap<String, Integer>();
    rval = internal_connect(page, body, referer, post, pages);
    mRedirections = null;
    return rval;
  }

  private URLConnection internal_connect(String page, String body, String referer, boolean post, List<String> pages) {
    if(handleInfiniteRedirection(page)) {
      //  If we're posting, and we hit an infloop, maybe we don't want to re-submit the data...
      if(post) {
        post = false;
        body = null;
      } else {
        throw new CookieRedirectException("Looped redirect to " + page, null);
      }
    }

    if(pages != null) pages.add(page);

    HttpURLConnection uc = initiateRequest(post, page, body, referer);

    if(uc != null) {
      String redirect = handleRedirect(uc, page);

      if(redirect != null) {
        if (JConfig.debugging()) {
          //  Don't log passwords in redirection messages.
          if(!page.contains("pass")) JConfig.log().logMessage("Redirecting from: " + page);
          if(!page.contains("pass")) JConfig.log().logMessage("Redirecting to: " + redirect);
          try {
            if(JConfig.queryConfiguration("debug.urls", "false").equals("true")) {
              JConfig.log().logMessage("Content: " + Http.net().receivePage(uc));
            }
          } catch (IOException ignored) {
            //  If there's no content or it's an unrecognized type, ignore it.
          }
        }

        return internal_connect(redirect, body, referer, post, pages);
      }
    }

    return uc;
  }

  private boolean handleInfiniteRedirection(String page) {
    Integer pageCount = mRedirections.get(page);
    if(pageCount == null) pageCount = 0;
    if(pageCount >= 2) return true;
    mRedirections.put(page, pageCount+1);
    return false;
  }

  private String handleRedirect(HttpURLConnection uc, String pageName) {
    int i = 1;
    String redirect = null;
    String nextKey;
    do {
      nextKey = uc.getHeaderFieldKey(i);
      if(nextKey != null) {
        if(sUberDebug) {
          JConfig.log().logDebug(nextKey+": " + uc.getHeaderField(i));
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
          mCookies.put(newCookie.getKey(), newCookie);
        }
      }
      i++;
    } while(nextKey != null);
    return redirect;
  }

  private String fixRelativeRedirect(String pageName, String redirect) {
    String slash = "";
    int serverEnd = pageName.indexOf(".com/");
    if(!redirect.startsWith("/")) {
      slash = "/";
    }
    String prefix;
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
    URLConnection uc;
    String cookies = mCookies.isEmpty() ? null : this.toString();

    if(post) {
      uc = Http.net().postFormPage(sendRequest, cgi, cookies, referer, mIgnoreRedirectCookies);
    } else {
      uc = Http.net().getPage(sendRequest, cookies, referer, mIgnoreRedirectCookies);
    }

    return (HttpURLConnection)uc;
  }

  public String toString() {
    boolean firstThrough = true;
    StringBuffer outBuf = null;

    for (Cookie cookie : mCookies.values()) {
      if (cookie.getValue().length() != 0) {
        if (!firstThrough) {
          outBuf.append("; ");
        } else {
          firstThrough = false;
          outBuf = new StringBuffer();
        }
        outBuf.append(cookie.getKey());
        outBuf.append("=");
        outBuf.append(cookie.getValue());
      }
    }

    if(outBuf != null) {
      return outBuf.toString();
    } else {
      return null;
    }
  }
}
