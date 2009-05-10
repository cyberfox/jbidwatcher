package com.jbidwatcher.my;

import com.jbidwatcher.util.config.JConfig;
import com.jbidwatcher.util.config.ErrorHandler;
import com.jbidwatcher.util.Parameters;
import com.jbidwatcher.util.StringTools;
import com.jbidwatcher.util.html.JHTML;
import com.jbidwatcher.util.queue.MQFactory;
import com.jbidwatcher.util.queue.MessageQueue;
import com.jbidwatcher.util.xml.XMLSerialize;
import com.jbidwatcher.util.xml.XMLElement;
import com.jbidwatcher.util.http.Http;
import com.jbidwatcher.util.http.ClientHttpRequest;
import com.jbidwatcher.auction.AuctionEntry;
import com.jbidwatcher.auction.EntryCorral;

import java.io.File;
import java.io.InputStream;
import java.io.IOException;
import java.net.URL;

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
  private static final String LOG_UPLOAD_URL = "http://my.jbidwatcher.com/upload/log";

  public boolean sendLogFile() {
    File fp = JConfig.log().closeLog();
    if(fp != null) return uploadFile(fp, LOG_UPLOAD_URL);
    return false;
  }

  public boolean sendLogFile(String filename) {
    File f = new File(filename);
    String feedForm = LOG_UPLOAD_URL;
    return uploadFile(f, feedForm);
  }

  private boolean uploadFile(File f, String feedForm) {
    try {
      String sample = StringTools.cat(new URL(feedForm));
      JHTML jh = new JHTML(new StringBuffer(sample));
      JHTML.Form form = jh.getFormWithInput("AWSAccessKeyId");
      if (form != null) {
        form.delInput("upload");
        String url = form.getAction();
        ClientHttpRequest chr = new ClientHttpRequest(url);
        chr.setParameters(form.getCGIMap());
        chr.setParameter("file", f);
        InputStream resp = chr.post();
        String result = StringTools.cat(resp);
        System.out.println(result);
        resp.close();
      }
    } catch (IOException e) {
      e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
      return false;
    }
    return true;
  }

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
          postAuction(EntryCorral.getInstance().takeForRead(deQ.toString()));
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

    JConfig.log().addHandler(new ErrorHandler() {
      public void addLog(String s) { /* ignored */}

      public void exception(String log, String message, String trace) {
        if(message == null) message = "(no message)";
        if(JConfig.queryConfiguration("my.jbidwatcher.id") != null &&
           JConfig.queryConfiguration("logging.remote", "false").equals("true")) {
          reportException(log + "\n" + message + "\n" + trace);
        }
      }
    });
  }

  public boolean createAccount(String email, String password) {
    //  TODO - GET http://my.jbidwatcher.com/users/new
    //  POST http://my.jbidwatcher.com/users with user[email]={email}&user[password]={password}&user[password_confirmation]={password}
    //  If 200 OK, the body contains the my.jbidwatcher.id
    return false;
  }

  public boolean updateAccount(String email, String password) {
    //  TODO - Must be logged in first?
    String acct_key = JConfig.queryConfiguration("my.jbidwatcher.id");
    if(acct_key == null) return false;

    String old_password = JConfig.queryConfiguration("my.jbidwatcher.password");
    //  TODO - PUT (!) http://my.jbidwatcher.com/users/update with user[email]={email}&user[password]={password}&old_password={old_password}
    //  TODO - Write server side for update.

    return false;
  }

  public boolean login(String email, String password) {
    //  TODO - GET http://my.jbidwatcher.com/login
    //  Fill in email and password & submit
    //  Get back a session key/cookie?
    return false;
  }
}
