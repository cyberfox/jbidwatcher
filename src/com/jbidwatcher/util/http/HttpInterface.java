package com.jbidwatcher.util.http;

import com.jbidwatcher.util.ByteBuffer;
import com.jbidwatcher.util.Parameters;

import java.net.URLConnection;
import java.net.URL;
import java.net.HttpURLConnection;
import java.io.IOException;
import java.io.InputStream;

/**
 * A high level interface for doing HTTP requests.
 *
 * User: mrs
 * Date: Nov 7, 2009
 * Time: 6:01:02 PM
 */
public interface HttpInterface {
  void setAuthInfo(String user, String pass);

  URLConnection postFormPage(String url, String cgiData, String cookie, String referer, boolean followRedirects);

  URLConnection makeRequest(URL source, String cookie) throws java.io.IOException;

  ByteBuffer getURL(URL url);

  StringBuffer get(String url);

  StringBuffer receivePage(URLConnection uc) throws IOException;

  URLConnection getPage(String url);

  URLConnection getPage(String url, String cookie, String referer, boolean redirect);

  String putTo(String url, String sb);

  String postTo(String url, Parameters params);

  InputStream getStream(HttpURLConnection huc);
}
