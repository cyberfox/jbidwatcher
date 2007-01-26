package com.jbidwatcher.util.http;
/*
 * Copyright (c) 2000-2007, CyberFOX Software, Inc. All Rights Reserved.
 *
 * Developed by mrs (Morgan Schweers)
 */

import com.jbidwatcher.config.JConfig;
import com.jbidwatcher.util.Base64;
import com.jbidwatcher.util.ByteBuffer;
import com.jbidwatcher.util.ErrorManagement;
import com.jbidwatcher.Constants;

import java.net.*;
import java.io.*;

public class Http {
  private final static boolean do_uber_debug = false;

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
        ((HttpURLConnection)huc).setRequestMethod("POST");
        if(!follow_redirects) ((HttpURLConnection)huc).setInstanceFollowRedirects(false);
      }
      if(do_uber_debug && JConfig.debugging) {
        if(cgiData != null) {
          System.err.println("Content-Type: application/x-www-form-urlencoded");
          System.err.println("Content-Length: " + Integer.toString(cgiData.length()));
          System.err.println("User-Agent: " + Constants.FAKE_BROWSER);
          System.err.println("Cookie: " + cookie);
        } else {
          System.err.println("CGI Data is null!");
        }
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
   */
  public static ByteBuffer receiveData(URLConnection uc) throws IOException {
    InputStream is = uc.getInputStream();
    int curMax = 16384;
    byte[] mainBuf = new byte[curMax];
    int offset = 0;
    int count;

    count = is.read(mainBuf, 0, curMax);

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
    return new ByteBuffer(mainBuf, offset);
  }

  public static StringBuffer receivePage(URLConnection uc) throws IOException {
    StringBuffer loadUp = new StringBuffer();

    BufferedReader br = null;
    String readData;
    int retry = 0;

    while(br == null && retry < 3) {
      try {
        //        ErrorManagement.logMessage(Thread.currentThread().getName() + ": RcvPage.o");
        br = new BufferedReader(new InputStreamReader(uc.getInputStream()));
        //        ErrorManagement.logMessage(Thread.currentThread().getName() + ": RcvPage.c");
      } catch(java.net.ConnectException jnce) {
        br = null;
        retry++;
        ErrorManagement.handleException("Failed to connect via URLConnection (retry: " + retry + ")", jnce);
      } catch(java.net.NoRouteToHostException cantGetThere) {
        br = null;
        retry++;
        ErrorManagement.handleException("Failed to find a route to receive the page (retry: " + retry + ")", cantGetThere);
      } catch(java.net.SocketException jnse) {
        br = null;
        retry++;
        ErrorManagement.handleException("Failed to load from URLConnection (retry: " + retry + ")", jnse);
      }
    }

    if(br == null) return null;

    do {
      readData = br.readLine();
      if(readData != null) {
        loadUp.append(readData);
        loadUp.append("\n");
        if(do_uber_debug && JConfig.debugging) {
          ErrorManagement.logFile("Read the following data", new StringBuffer(readData));
        }
      }
    } while(readData != null);
    br.close();

    return(loadUp);
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
}
