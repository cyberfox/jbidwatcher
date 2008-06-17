package com.jbidwatcher.app;

import com.jbidwatcher.auction.AuctionEntry;
import com.jbidwatcher.util.http.Http;
import com.jbidwatcher.util.config.ErrorManagement;
import com.jbidwatcher.util.config.JConfig;
import com.jbidwatcher.util.queue.MessageQueue;
import com.jbidwatcher.util.queue.MQFactory;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by IntelliJ IDEA.
 * User: mrs
 * Date: Jun 16, 2008
 * Time: 11:45:10 PM
 *
 * A set of methods to communicate with the 'my.jbidwatcher.com' site.
 */
public class MyJBidwatcher implements MessageQueue.Listener {
  private class Parameters extends HashMap<Object, Object>{ }
  private static MyJBidwatcher sInstance;

  public String recognizeBidpage(AuctionEntry entry, StringBuffer page) {
    String url = "http://my.jbidwatcher.com/advanced/recognize";

    Parameters p = new Parameters();
    if(entry != null) p.put("item", entry.getIdentifier());
    p.put("user", JConfig.queryConfiguration("my.jbidwatcher.id"));
    p.put("body", page);
    return postTo(url, p);
  }

  private static String postTo(String url, Parameters params) {
    StringBuffer postData = null;
    try {
      postData = createCGIData(params);
      URLConnection uc = Http.postFormPage(url, postData.toString(), null, null, false);
      StringBuffer sb = Http.receivePage(uc);
      return sb == null ? null : sb.toString();
    } catch (IOException e) {
      int length = 0;
      if(postData != null) length = postData.length();
      ErrorManagement.logDebug("Couldn't send params (length: " + length + ") to " + url);
      return null;
    }
  }

  private static StringBuffer createCGIData(Parameters data) throws UnsupportedEncodingException {
    StringBuffer postData = new StringBuffer();
    boolean first = true;
    for(Map.Entry<Object, Object> param : data.entrySet()) {
      Object key = param.getKey();
      Object value = param.getValue();

      if(value != null) {
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

  public String reportException(String sb) {
    String url = "http://my.jbidwatcher.com/advanced/report";
    Parameters p = new Parameters();
    p.put("user", JConfig.queryConfiguration("my.jbidwatcher.id"));
    p.put("body", sb);
    return postTo(url, p);
  }

  public static MyJBidwatcher getInstance() {
    if(sInstance == null) sInstance = new MyJBidwatcher();
    return sInstance;
  }

  //  The only thing that gets submitted to the queue is exceptions...?
  private MyJBidwatcher() {
    MQFactory.getConcrete("my").registerListener(this);
  }

  public void messageAction(Object deQ) {
    ErrorManagement.logDebug(reportException(deQ.toString()));
  }
}
