package com.jbidwatcher.my;

import com.jbidwatcher.util.config.ErrorManagement;
import com.jbidwatcher.util.config.JConfig;
import com.jbidwatcher.util.config.ErrorHandler;
import com.jbidwatcher.util.Parameters;
import com.jbidwatcher.util.http.Http;
import com.jbidwatcher.auction.AuctionEntry;

/**
 * Created by IntelliJ IDEA.
 * User: mrs
 * Date: Jun 16, 2008
 * Time: 11:45:10 PM
 *
 * A set of methods to communicate with the 'my.jbidwatcher.com' site.
 */
public class MyJBidwatcher {
  private static MyJBidwatcher sInstance;

  public String recognizeBidpage(String identifier, StringBuffer page) {
    String url = "http://my.jbidwatcher.com/advanced/recognize";

    Parameters p = new Parameters();
    if(identifier != null) p.put("item", identifier);
    p.put("user", JConfig.queryConfiguration("my.jbidwatcher.id"));
    p.put("body", page);
    return Http.postTo(url, p);
  }

  public String reportException(String sb) {
    String url = "http://my.jbidwatcher.com/advanced/report";
    Parameters p = new Parameters();
    p.put("user", JConfig.queryConfiguration("my.jbidwatcher.id"));
    p.put("body", sb);
    return Http.postTo(url, p);
  }

  public static MyJBidwatcher getInstance() {
    if(sInstance == null) sInstance = new MyJBidwatcher();
    return sInstance;
  }

  public void postAuction(AuctionEntry ae) {
    Parameters p = new Parameters();
    p.put("auction_data", ae.toXML().toString());
    Http.postTo("http://my.jbidwatcher.com/auctions/import", p);
  }

  //  The only thing that gets submitted to the queue is exceptions...?
  private MyJBidwatcher() {
    ErrorManagement.addHandler(new ErrorHandler() {
      public void addLog(String s) { /* ignored */}

      public void exception(String log, String message, String trace) {
        if(JConfig.queryConfiguration("my.jbidwatcher.id") != null &&
           JConfig.queryConfiguration("logging.remote", "false").equals("true")) {
          reportException(log + "\n" + message + "\n" + trace);
        }
      }
    });
  }
}
