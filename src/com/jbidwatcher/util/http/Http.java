package com.jbidwatcher.util.http;
/*
 * Copyright (c) 2000-2007, CyberFOX Software, Inc. All Rights Reserved.
 *
 * Developed by mrs (Morgan Schweers)
 */

import com.jbidwatcher.util.config.JConfig;
import com.jbidwatcher.util.config.Base64;
import com.jbidwatcher.util.config.ErrorManagement;
import com.jbidwatcher.util.ByteBuffer;
import com.jbidwatcher.util.Constants;
import com.jbidwatcher.util.Parameters;

import java.net.*;
import java.io.*;
import java.util.Map;

public class Http {
  private static void setConnectionProxyInfo(URLConnection huc) {
    if(JConfig.queryConfiguration("proxyfirewall", "none").equals("proxy")) {
      String proxyHost = JConfig.queryConfiguration("proxy.host", null);

      if(proxyHost != null) {
        String user = JConfig.queryConfiguration("proxy.user", null);
        String pass = JConfig.queryConfiguration("proxy.pass", null);

        System.setProperty("http.proxyUser", user);
        System.setProperty("http.proxyPassword", pass);
        if (user != null && pass != null) {
          String str = user + ':' + pass;
          String encoded = "Basic " + Base64.encodeString(str);
          huc.setRequestProperty("Proxy-Authorization", encoded);
        }
      }
    }
  }

  public static URLConnection postFormPage(String urlToPost, String cgiData, String cookie, String referer, boolean follow_redirects) {
    URLConnection huc;
    PrintStream obw;
    URL authURL;

    try {
      authURL = new URL(urlToPost);

      huc = authURL.openConnection();
      setConnectionProxyInfo(huc);
      huc.setDoOutput(true);

      if(huc instanceof HttpURLConnection) {
        HttpURLConnection conn = (HttpURLConnection)huc;
        conn.setRequestMethod("POST");
        if(!follow_redirects) conn.setInstanceFollowRedirects(false);
      }
      if(JConfig.queryConfiguration("debug.uber", "false").equals("true") && JConfig.debugging) {
        dumpFormHeaders(cgiData, cookie);
      }

      huc.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
      huc.setRequestProperty("Content-Length", Integer.toString(cgiData.length()));
      huc.setRequestProperty("User-Agent", Constants.FAKE_BROWSER);
      if(referer != null) huc.setRequestProperty("Referer", referer);
      if(cookie != null) {
        huc.setRequestProperty("Cookie", cookie);
      }
      obw = new PrintStream(huc.getOutputStream());
      obw.println(cgiData);
      obw.close();
    } catch(ConnectException ce) {
      ErrorManagement.logMessage("postFormPage: " + ce);
      huc = null;
    } catch(Exception e) {
      ErrorManagement.handleException("postFormPage: " + e, e);
      huc = null;
    }
    return(huc);
  }

  private static void dumpFormHeaders(String cgiData, String cookie) {
    if(cgiData != null) {
      System.err.println("Content-Type: application/x-www-form-urlencoded");
      System.err.println("Content-Length: " + Integer.toString(cgiData.length()));
      System.err.println("User-Agent: " + Constants.FAKE_BROWSER);
      System.err.println("Cookie: " + cookie);
    } else {
      System.err.println("CGI Data is null!");
    }
  }

  public static URLConnection makeRequest(URL source, String cookie) throws java.io.IOException {
    URLConnection uc;

    uc = source.openConnection();
    if(JConfig.queryConfiguration("proxyfirewall", "none").equals("proxy")) {
      String proxyHost = JConfig.queryConfiguration("proxy.host", null);
      if(proxyHost != null) {
        String user = JConfig.queryConfiguration("proxy.user", null);
        String pass = JConfig.queryConfiguration("proxy.pass", null);

        if (user != null && pass != null) {
          if(!user.equals("")) {
            String str = user + ':' + pass;
            String encoded = "Basic " + Base64.encodeString(str);
            uc.setRequestProperty("Proxy-Authorization", encoded);
          }
        }
      }
    }
    if(cookie != null) {
      uc.setRequestProperty("Cookie", cookie);
    }

    //  We fake our user-agent, since some auction servers only let
    //  you bid/read if we are a 'supported' browser.
    uc.setRequestProperty("User-Agent", Constants.FAKE_BROWSER);

    return uc;
  }

  public static ByteBuffer getURL(URL dataURL) {
    return getURL(dataURL, null);
  }

  /** 
   * @brief Retrieve data from HTTP in raw byte form.
   * 
   * @param dataURL - The URL of the raw data to retrieve.
   * @param inCookie - Any cookie needed to be passed along.
   * 
   * @return - A result with raw data and the length.
   */
  public static ByteBuffer getURL(URL dataURL, String inCookie) {
    ByteBuffer rval;

    try {
      rval = receiveData(makeRequest(dataURL, inCookie));
    } catch(FileNotFoundException fnfe) {
      //  It'd be great if we could pass along something that said, 'not here, never will be'.
      rval = null;
    } catch(IOException e) {
      //  Mostly ignore HTTP 504 error, it's just a temporary 'gateway down' error.
      if(e.getMessage().indexOf("HTTP response code: 504")==-1) {
        ErrorManagement.handleException("Error loading data URL (" + dataURL.toString() + ')', e);
      } else {
        ErrorManagement.logMessage("HTTP 504 error loading URL (" + dataURL.toString() + ')');
      }
      rval = null;
    }
    return rval;
  }

  /**
   * @brief Retrieve raw data from an already existing URL connection.
   *
   * @param uc - The URLConnection to pull the data from.
   *
   * @return - A structure containing the raw data and the length.
   * @throws java.io.IOException if an error occurs while reading the data.
   */
  public static ByteBuffer receiveData(URLConnection uc) throws IOException {
    InputStream is = uc.getInputStream();
    int curMax = 32768;
    byte[] mainBuf = new byte[curMax];

    int count = is.read(mainBuf, 0, curMax);
    int offset = 0;

    while(count != -1) {
      if(offset+count == curMax) {
        curMax *= 3;
        byte[] tmp = new byte[curMax];
        System.arraycopy(mainBuf, 0, tmp, 0, offset+count);
        mainBuf = tmp;
      }
      offset += count;
      count = is.read(mainBuf, offset, curMax-offset);
    }
    is.close();
    return new ByteBuffer(mainBuf, offset);
  }

  public static StringBuffer receivePage(URLConnection uc) throws IOException {
    ByteBuffer buff;

    buff = receiveData(uc);

    if(buff == null) return null;
    String charset = uc.getContentType();
    if(charset.matches(".*charset=([^;]*).*")) {
      charset = charset.replaceFirst(".*charset=([^;]*).*", "$1");
      return new StringBuffer(new String(buff.getData(), 0, buff.getLength(), charset));
    }
    return new StringBuffer(new String(buff.getData(), 0, buff.getLength()));
  }

  /**
   * Simplest request, load a URL, no cookie, no referer, follow redirects blindly.
   *
   * @param urlToGet - The URL to load.
   * @return - A URLConnection usable to retrieve the page requested.
   */
  public static URLConnection getPage(String urlToGet) {
    return(getPage(urlToGet, null, null, true));
  }

  public static URLConnection getPage(String urlToGet, String cookie, String referer, boolean redirect) {
    HttpURLConnection huc;

    try {
      URL authURL = new URL(urlToGet);
      URLConnection uc = authURL.openConnection();
      if(!(uc instanceof HttpURLConnection)) {
        return uc;
      }
      huc = (HttpURLConnection)uc;
      huc.setInstanceFollowRedirects(redirect);
      setConnectionProxyInfo(huc);

      huc.setRequestProperty("User-Agent", Constants.FAKE_BROWSER);
      if(referer != null) huc.setRequestProperty("Referer", referer);
      if(cookie != null) huc.setRequestProperty("Cookie", cookie);
    } catch(Exception e) {
      ErrorManagement.handleException("getPage: " + e, e);
      huc = null;
    }
    return(huc);
  }

  public static String postTo(String url, Parameters params) {
    StringBuffer postData = null;
    try {
      postData = createCGIData(params);
      URLConnection uc = postFormPage(url, postData.toString(), null, null, false);
      StringBuffer sb = receivePage(uc);
      return sb == null ? null : sb.toString();
    } catch (IOException e) {
      int length = 0;
      if (postData != null) length = postData.length();
      ErrorManagement.logDebug("Couldn't send params (length: " + length + ") to " + url);
      return null;
    }
  }

  private static StringBuffer createCGIData(Parameters data) throws UnsupportedEncodingException {
    StringBuffer postData = new StringBuffer();
    boolean first = true;
    for (Map.Entry<Object, Object> param : data.entrySet()) {
      Object key = param.getKey();
      Object value = param.getValue();

      if (value != null) {
        if (!first)
          postData.append('&');
        else
          first = false;

        postData.append(key.toString());
        postData.append('=');
        postData.append(URLEncoder.encode(value.toString(), "UTF-8"));
      }
    }
    return postData;
  }
}
