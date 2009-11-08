package com.jbidwatcher.util.http;

import com.jbidwatcher.util.ByteBuffer;
import com.jbidwatcher.util.Parameters;
import com.jbidwatcher.util.config.JConfig;

import java.net.URLConnection;
import java.net.URL;
import java.net.HttpURLConnection;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.HashMap;

/**
 * A class to mock the network requests of the Http class.
 *
 * User: mrs
 * Date: Nov 7, 2009
 * Time: 5:57:48 PM
 */
public class HttpMock implements HttpInterface {
  public class UnimplementedException extends RuntimeException { }

  private static final String GET="GET";
  private static final String PUT="PUT";
  private static final String POST="POST";
  //  { 'GET', { 'url', 'mockfilename' } }
  private static final Map<String,Map<String,String>> urlRegistry = new HashMap<String,Map<String,String>>();
  private Http mReal = new Http();

  public HttpMock() {
    urlRegistry.put(GET, new HashMap<String,String>());
    urlRegistry.put(PUT, new HashMap<String,String>());
    urlRegistry.put(POST, new HashMap<String,String>());
  }

  private static String lookup(String method, String url) {
    String rval;
    synchronized(urlRegistry) {
      rval = urlRegistry.get(method).get(url);
      if (rval == null) {
        JConfig.log().logMessage("No response registered for method " + method + " to url: " + url);
      }
    }
    return rval;
  }

  public static void register(String method, String url, String filename) {
    synchronized(urlRegistry) {
      urlRegistry.get(method).put(url, filename);
    }
  }

  public void setAuthInfo(String user, String pass) {
    mReal.setAuthInfo(user, pass);
  }

  public URLConnection postFormPage(String url, String cgiData, String cookie, String referer, boolean followRedirects) {
    String newURL = lookup(POST, url);
    if(newURL == null) return mReal.postFormPage(url, cgiData, cookie, referer, followRedirects);
    throw new UnimplementedException();
  }

  public URLConnection makeRequest(URL source, String cookie) throws IOException {
    String newURL = lookup(GET, source.toString());
    if(newURL == null) return mReal.makeRequest(source, cookie);
    throw new UnimplementedException();
  }

  public URLConnection getPage(String url) {
    String newURL = lookup(GET, url);
    if(newURL == null) return mReal.getPage(url);
    throw new UnimplementedException();
  }

  public URLConnection getPage(String url, String cookie, String referer, boolean redirect) {
    String newURL = lookup(GET, url);
    if(newURL == null) return mReal.getPage(url, cookie, referer, redirect);
    throw new UnimplementedException();
  }

  public InputStream getStream(HttpURLConnection huc) {
    return mReal.getStream(huc);
  }

  public ByteBuffer getURL(URL url) {
    String newURL = lookup(GET, url.toString());
    if(newURL == null) return mReal.getURL(url);
    throw new UnimplementedException();
  }

  public StringBuffer get(String url) {
    String newURL = lookup(GET, url);
    if(newURL == null) return mReal.get(url);
    throw new UnimplementedException();
  }

  public StringBuffer receivePage(URLConnection uc) throws IOException {
    return mReal.receivePage(uc);
  }

  public String putTo(String url, String sb) {
    String newURL = lookup(PUT, url);
    if(newURL == null) mReal.putTo(url, sb);
    throw new UnimplementedException();
  }

  public String postTo(String url, Parameters params) {
    String newURL = lookup(POST, url);
    if(newURL == null) mReal.postTo(url, params);
    throw new UnimplementedException();
  }
}
