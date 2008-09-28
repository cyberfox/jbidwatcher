package com.jbidwatcher.my;

import com.jbidwatcher.util.config.ErrorManagement;
import com.jbidwatcher.util.config.JConfig;
import com.jbidwatcher.util.config.ErrorHandler;
import com.jbidwatcher.util.Parameters;
import com.jbidwatcher.util.StringTools;
import com.jbidwatcher.util.queue.MQFactory;
import com.jbidwatcher.util.queue.MessageQueue;
import com.jbidwatcher.util.xml.XMLSerialize;
import com.jbidwatcher.util.xml.XMLElement;
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
  private static MyJBidwatcher sInstance = null;

  public String recognizeBidpage(String identifier, StringBuffer page) {
    Parameters p = new Parameters();
    if(identifier != null) p.put("item", identifier);
    p.put("user", JConfig.queryConfiguration("my.jbidwatcher.id"));
    p.put("body", page);
    String url = "http://my.jbidwatcher.com/advanced/recognize";
    return Http.postTo(url, p);
  }

  public String reportException(String sb) {
    Parameters p = new Parameters();
    p.put("user", JConfig.queryConfiguration("my.jbidwatcher.id"));
    p.put("body", sb);
    String url = "http://my.jbidwatcher.com/advanced/report";
    return Http.postTo(url, p);
  }

  public static MyJBidwatcher getInstance() {
    if(sInstance == null) sInstance = new MyJBidwatcher();
    return sInstance;
  }

  public void postAuction(XMLSerialize ae) {
    Parameters p = new Parameters();
    postAuction(ae, "auctions/import", p);
  }

  public void postAuction(XMLSerialize ae, String myPath, Parameters p) {
    p.put("user", JConfig.queryConfiguration("my.jbidwatcher.id"));
    p.put("auction_data", ae.toXML().toString());
    Http.postTo("http://my.jbidwatcher.com/" + myPath, p);
  }

  private MyJBidwatcher() {
    MQFactory.getConcrete("upload").registerListener(new MessageQueue.Listener() {
      public void messageAction(Object deQ) {
        if(JConfig.queryConfiguration("my.jbidwatcher.id") != null) {
          postAuction((XMLSerialize)deQ);
        }
      }
    });

    if(JConfig.queryConfiguration("my.jbidwatcher.id") != null) {
      MQFactory.getConcrete("report").registerListener(new MessageQueue.Listener() {
        public void messageAction(Object deQ) {
          AuctionEntry ae = (AuctionEntry) deQ;
          XMLElement root = new XMLElement("report");
          XMLElement body = new XMLElement("body");
          body.setContents(ae.getContent().toString());
          root.addChild(ae.toXML());
          root.addChild(body);
          Parameters p = new Parameters();
          p.put("user_comment", ae.getLastStatus());
          postAuction(root, "report/problem", p);
        }
      });
    }

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
