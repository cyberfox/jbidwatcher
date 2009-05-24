package com.jbidwatcher.util.http;
/*
 * Copyright (c) 2000-2007, CyberFOX Software, Inc. All Rights Reserved.
 *
 * Developed by mrs (Morgan Schweers)
 */

import com.jbidwatcher.util.config.JConfig;
import com.jbidwatcher.util.config.Base64;
import com.jbidwatcher.util.ByteBuffer;
import com.jbidwatcher.util.Constants;
import com.jbidwatcher.util.Parameters;
import com.jbidwatcher.util.StringTools;

import java.net.*;
import java.io.*;
import java.util.Map;

public class Http {
  private String mUsername = null;
  private String mPassword = null;

  private static Http sInstance = new Http();
  public static Http net() { return sInstance; }

  public void setAuthInfo(String user, String pass) {
    mUsername = user;
    mPassword = pass;
  }

  private void setConnectionInfo(URLConnection huc) {
    if(mUsername != null && mPassword != null) {
      huc.setRequestProperty("Authorization", "Basic " + Base64.encodeString(mUsername + ':' + mPassword));
    }
    setConnectionProxyInfo(huc);
  }

  private void setConnectionProxyInfo(URLConnection huc) {
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

  public URLConnection postFormPage(String urlToPost, String cgiData, String cookie, String referer, boolean follow_redirects) {
    URLConnection huc;

    try {
      if(JConfig.queryConfiguration("debug.urls", "false").equals("true")) {
        JConfig.log().logDebug("postFormPage: " + urlToPost);
      }
      URL authURL = new URL(urlToPost);

      huc = authURL.openConnection();
      setConnectionInfo(huc);
      huc.setDoOutput(true);

      if(huc instanceof HttpURLConnection) {
        HttpURLConnection conn = (HttpURLConnection)huc;
        conn.setRequestMethod("POST");
        if(!follow_redirects) conn.setInstanceFollowRedirects(false);
      }
      if(JConfig.queryConfiguration("debug.uber", "false").equals("true") && JConfig.debugging) {
        dumpFormHeaders(System.err, cgiData, cookie);
      }

      huc.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
      huc.setRequestProperty("Content-Length", Integer.toString(cgiData.length()-1));
      huc.setRequestProperty("User-Agent", Constants.FAKE_BROWSER);
      if(referer != null) huc.setRequestProperty("Referer", referer);
      if(cookie != null) {
        huc.setRequestProperty("Cookie", cookie);
      }
      PrintStream obw = new PrintStream(huc.getOutputStream());
      obw.println(cgiData);
      obw.close();
    } catch(ConnectException ce) {
      JConfig.log().logMessage("postFormPage: " + ce);
      huc = null;
    } catch(Exception e) {
      JConfig.log().handleException("postFormPage: " + e, e);
      huc = null;
    }
    return(huc);
  }

  private static void dumpFormHeaders(PrintStream out, String cgiData, String cookie) {
    if(cgiData != null) {
      out.println("Content-Type: application/x-www-form-urlencoded");
      out.println("Content-Length: " + Integer.toString(cgiData.length()));
      out.println("User-Agent: " + Constants.FAKE_BROWSER);
      out.println("Cookie: " + cookie);
    } else {
      out.println("CGI Data is null!");
    }
  }

  public URLConnection makeRequest(URL source, String cookie) throws java.io.IOException {
    if(JConfig.queryConfiguration("debug.urls", "false").equals("true")) {
      JConfig.log().logDebug("makeRequest: " + source.toString());
    }
    URLConnection uc = source.openConnection();
    setConnectionInfo(uc);
    if(cookie != null) {
      uc.setRequestProperty("Cookie", cookie);
    }

    //  We fake our user-agent, since some auction servers only let
    //  you bid/read if we are a 'supported' browser.
    uc.setRequestProperty("User-Agent", Constants.FAKE_BROWSER);

    return uc;
  }

  public ByteBuffer getURL(URL dataURL) {
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
  private ByteBuffer getURL(URL dataURL, String inCookie) {
    ByteBuffer rval;

    try {
      rval = receiveData(makeRequest(dataURL, inCookie));
    } catch(FileNotFoundException fnfe) {
      //  It'd be great if we could pass along something that said, 'not here, never will be'.
      rval = null;
    } catch(IOException e) {
      //  Mostly ignore HTTP 504 error, it's just a temporary 'gateway down' error.
      if(e.getMessage().indexOf("HTTP response code: 504")==-1) {
        JConfig.log().handleException("Error loading data URL (" + dataURL.toString() + ')', e);
      } else {
        JConfig.log().logMessage("HTTP 504 error loading URL (" + dataURL.toString() + ')');
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
  private ByteBuffer receiveData(URLConnection uc) throws IOException {
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

  public StringBuffer get(String url) {
    try {
      return receivePage(getPage(url));
    } catch (IOException ioe) {
      JConfig.log().logDebug("Got an exception reading " + url + ": " + ioe.getMessage());
      return null;
    }
  }

  public StringBuffer receivePage(URLConnection uc) throws IOException {
    if(uc == null) return null;
    ByteBuffer buff = receiveData(uc);

    if(buff == null) return null;
    String charset = uc.getContentType();
    if(charset != null && charset.matches(".*charset=([^;]*).*")) {
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
  public URLConnection getPage(String urlToGet) {
    return(getPage(urlToGet, null, null, true));
  }

  public URLConnection getPage(String urlToGet, String cookie, String referer, boolean redirect) {
    HttpURLConnection huc;

    try {
      if(JConfig.queryConfiguration("debug.urls", "false").equals("true")) {
        JConfig.log().logDebug("getPage: " + urlToGet);
      }
      URL authURL = new URL(urlToGet);
      URLConnection uc = authURL.openConnection();
      if(!(uc instanceof HttpURLConnection)) {
        return uc;
      }
      huc = (HttpURLConnection)uc;
      huc.setInstanceFollowRedirects(redirect);
      setConnectionInfo(huc);

      huc.setRequestProperty("User-Agent", Constants.FAKE_BROWSER);
      if(referer != null) huc.setRequestProperty("Referer", referer);
      if(cookie != null) huc.setRequestProperty("Cookie", cookie);
    } catch(Exception e) {
      JConfig.log().handleException("getPage: " + e, e);
      huc = null;
    }
    return(huc);
  }

  public String putTo(String url, String sb) {
    HttpURLConnection huc = null;
    String result = null;
    try {
      huc = (HttpURLConnection) new URL(url).openConnection();
      setConnectionInfo(huc);
      huc.setRequestProperty("Content-Type", "application/octet-stream");
      huc.setRequestProperty("Content-Length", Integer.toString(sb.length() - 1));
      huc.setRequestProperty("User-Agent", Constants.FAKE_BROWSER);

      huc.setRequestMethod("PUT");
      huc.setDoOutput(true);
      huc.getOutputStream().write(sb.getBytes());
      result = StringTools.cat(huc.getInputStream());
    } catch (MalformedURLException murle) {
      JConfig.log().logMessage("Invalid URL!? (" + url + "): " + murle.getMessage());
    } catch (IOException ioe) {
      try { result = StringTools.cat(huc.getErrorStream()); } catch (Exception e) { }
    }
    return result;
  }

  public String postTo(String url, Parameters params) {
    StringBuffer postData = null;
    try {
      postData = createCGIData(params);
      URLConnection uc = postFormPage(url, postData.toString(), null, null, false);
      StringBuffer sb = receivePage(uc);
      return sb == null ? null : sb.toString();
    } catch (IOException e) {
      int length = 0;
      if (postData != null) length = postData.length();
      JConfig.log().logDebug("Couldn't send params (length: " + length + ") to " + url);
      JConfig.log().logDebug(e.getMessage());
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

  public InputStream getStream(HttpURLConnection huc) {
    if(huc == null) return null;
    InputStream rval;

    try {
      int status = huc.getResponseCode();
      if (status / 100 == 4) {
        rval = huc.getErrorStream();
      } else if (status / 100 == 3) {
        String location = huc.getHeaderField("Location");
        huc = (HttpURLConnection)getPage(location);
        rval = huc.getInputStream();
      } else {
        rval = huc.getInputStream();
      }
    } catch (IOException e) {
      JConfig.log().logDebug("Error getting the stream from " + huc.getURL() + ": " + e.getMessage());
      rval = null;
    }
    return rval;
  }
}
