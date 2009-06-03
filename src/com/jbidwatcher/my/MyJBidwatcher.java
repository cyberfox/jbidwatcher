package com.jbidwatcher.my;

import com.jbidwatcher.util.config.JConfig;
import com.jbidwatcher.util.config.ErrorHandler;
import com.jbidwatcher.util.Parameters;
import com.jbidwatcher.util.StringTools;
import com.jbidwatcher.util.Constants;
import com.jbidwatcher.util.ZoneDate;
import com.jbidwatcher.util.html.JHTML;
import com.jbidwatcher.util.queue.MQFactory;
import com.jbidwatcher.util.queue.MessageQueue;
import com.jbidwatcher.util.queue.SuperQueue;
import com.jbidwatcher.util.xml.XMLSerialize;
import com.jbidwatcher.util.xml.XMLElement;
import com.jbidwatcher.util.http.Http;
import com.jbidwatcher.util.http.ClientHttpRequest;
import com.jbidwatcher.auction.AuctionEntry;
import com.jbidwatcher.auction.EntryCorral;

import java.io.File;
import java.io.InputStream;
import java.io.IOException;
import java.net.URLEncoder;
import java.net.HttpURLConnection;
import java.util.Date;

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
  private Http mNet = null;
  private static String LOG_UPLOAD_URL =  "my.jbidwatcher.com/upload/log";
  private static String ITEM_UPLOAD_URL = "my.jbidwatcher.com/upload/listing";
  private String mSyncQueueURL = null;
  private String mReportQueueURL = null;
  private boolean mUseSSL = false;
  private boolean mUploadHTML = false;
  private boolean mUseServerParser = false;
  private boolean mGixen = false;
  private boolean mReadSnipesFromServer = false;
  private ZoneDate mExpiry;

  private String url(String url) {
    if(mUseSSL) return "https://" + url;
    return "http://" + url;
  }

  private Http http() {
    if(mNet == null) {
      mNet = new Http();
    }

    mNet.setAuthInfo(JConfig.queryConfiguration("my.jbidwatcher.id"), JConfig.queryConfiguration("my.jbidwatcher.key"));

    return mNet;
  }

  public String sendLogFile(String email, String desc) {
    File fp = JConfig.log().closeLog();
    return sendFile(fp, url(LOG_UPLOAD_URL), email, desc);
  }

  private String createFormSource(String formBase, String email, String desc) {
    try {
      String parameters = "";
      if(email != null && email.length() != 0) {
        parameters = "?email=" + URLEncoder.encode(email, "UTF-8");
      }
      if(desc != null && desc.length() != 0) {
        parameters += (parameters.length() == 0) ? '?' : '&';
        parameters += "description=" + URLEncoder.encode(desc, "UTF-8");
      }
      formBase += parameters;
    } catch(Exception e) {
      formBase += "email=teh%40fail.com&description=Failed+to+encode+description";
    }
    return formBase;
  }

  public String sendFile(File fp, String formBase, String email, String desc) {
    String formSource = createFormSource(formBase, email, desc);
    if (fp != null) return uploadFile(fp, formSource);
    return null;
  }

  private String uploadFile(File f, String feedForm) {
    String result = null;
    try {
      StringBuffer sample = http().get(feedForm);
      if(sample == null) {
        JConfig.log().logDebug("Failed to get S3 upload form from " + feedForm);
      } else {
        JHTML jh = new JHTML(sample);
        JHTML.Form form = jh.getFormWithInput("AWSAccessKeyId");
        if (form != null) {
          form.delInput("upload");
          String url = form.getAction();
          ClientHttpRequest chr = new ClientHttpRequest(url);
          chr.setParameters(form.getCGIMap());
          chr.setParameter("file", f);
          HttpURLConnection huc = chr.post();
          InputStream resp = http().getStream(huc);
          result = StringTools.cat(resp);
          JConfig.log().logDebug(result);
          resp.close();
        }
      }
    } catch (IOException e) {
      JConfig.log().handleDebugException("Trying to upload a file to S3", e);
    }
    return result;
  }

  public String recognizeBidpage(String identifier, StringBuffer page) {
    Parameters p = new Parameters();
    if(identifier != null) p.put("item", identifier);
    p.put("body", page);
    String url = url("my.jbidwatcher.com/services/recognize");
    return http().postTo(url, p);
  }

  public String reportException(String sb) {
    Parameters p = new Parameters();
    p.put("body", sb);
    String url = url("my.jbidwatcher.com/services/report_exception");
    return http().postTo(url, p);
  }

  public static MyJBidwatcher getInstance() {
    if(sInstance == null) sInstance = new MyJBidwatcher();
    return sInstance;
  }

  private void getSQSURL() {
    StringBuffer sb = http().get(url("my.jbidwatcher.com/services/syncq"));
    mSyncQueueURL = (sb == null) ? null : sb.toString();
    sb = http().get(url("my.jbidwatcher.com/services/reportq"));
    mReportQueueURL = (sb == null) ? null : sb.toString();
  }

  public void postXML(String queue, XMLSerialize ae) {
    XMLElement xmlWrapper = new XMLElement("message");
    XMLElement user = new XMLElement("user");
    XMLElement access_key = new XMLElement("key");
    user.setContents(JConfig.queryConfiguration("my.jbidwatcher.id"));
    access_key.setContents(JConfig.queryConfiguration("my.jbidwatcher.key"));
    xmlWrapper.addChild(user);
    xmlWrapper.addChild(access_key);
    xmlWrapper.addChild(ae.toXML());
    String aucXML = xmlWrapper.toString();

    if(queue != null) http().putTo(queue, aucXML);
  }

  private MyJBidwatcher() {
    MQFactory.getConcrete("my").registerListener(new MessageQueue.Listener() {
      public void messageAction(Object deQ) {
        String cmd = (String)deQ;
        if(JConfig.queryConfiguration("my.jbidwatcher.enabled", "false").equals("true")) {
          if (cmd.equals("ACCOUNT")) getAccountInfo();
        }
      }
    });

    //  Get the URLs to POST stuff to, and get a new one every 12 hours.
    SuperQueue.getInstance().preQueue("ACCOUNT", "my", System.currentTimeMillis(), Constants.ONE_DAY);

    MQFactory.getConcrete("upload").registerListener(new MessageQueue.Listener() {
      public void messageAction(Object deQ) {
        if(JConfig.queryConfiguration("my.jbidwatcher.id") != null) {
          postXML(mSyncQueueURL, EntryCorral.getInstance().takeForRead(deQ.toString()));
        }
      }
    });

    if(JConfig.queryConfiguration("my.jbidwatcher.id") != null) {
      MQFactory.getConcrete("report").registerListener(new MessageQueue.Listener() {
        public void messageAction(Object deQ) {
          AuctionEntry ae = EntryCorral.getInstance().takeForRead((String)deQ);
          String s3Result = sendFile(ae.getContentFile(), url(ITEM_UPLOAD_URL), JConfig.queryConfiguration("my.jbidwatcher.id"), ae.getLastStatus());
          XMLElement root = new XMLElement("report");
          XMLElement s3Key = new XMLElement("s3");
          s3Key.setContents(s3Result);
          root.addChild(ae.toXML());
          postXML(mReportQueueURL, root);
        }
      });
    }

    JConfig.log().addHandler(new ErrorHandler() {
      public void close() { /* ignored */ }
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

  public boolean getAccountInfo() {
    StringBuffer sb = http().get("https://my.jbidwatcher.com/services/account");
    if(sb == null) return false;
    XMLElement xml = new XMLElement();
    xml.parseString(sb.toString());
    XMLElement sync = xml.getChild("syncq");
    XMLElement expires = xml.getChild("expiry");
    XMLElement listingsRemaining = xml.getChild("listings");
    XMLElement categoriesRemaining = xml.getChild("categories");
    XMLElement reporting = xml.getChild("reportq");
    XMLElement snipesListen = xml.getChild("snipes");
    XMLElement ssl = xml.getChild("ssl");
    XMLElement uploadHTML = xml.getChild("uploadhtml");
    XMLElement serverParser = xml.getChild("parser");
    XMLElement gixen = xml.getChild("gixen");

    if(expires != null) {
      String date = expires.getContents();
      mExpiry = StringTools.figureDate(date, "yyyy-MM-dd'T'HH:mm:ssZ");
      if(mExpiry.getDate().before(new Date())) {
        JConfig.setConfiguration("my.jbidwatcher.enabled", "false");
      } else {
        JConfig.setConfiguration("my.jbidwatcher.enabled", "true");
      }
    }

    mSyncQueueURL = sync == null ? null : sync.getContents();
    mReportQueueURL = reporting == null ? null : reporting.getContents();
    mUseSSL = getBoolean(ssl);
    mReadSnipesFromServer = getBoolean(snipesListen);
    mUploadHTML = getBoolean(uploadHTML);
    mUseServerParser = getBoolean(serverParser);
    mGixen = getBoolean(gixen);

    return mSyncQueueURL != null && mReportQueueURL != null;
  }

  private boolean getBoolean(XMLElement x) {
    boolean rval = false;
    if(x != null) {
      String contents = x.getContents();
      if(contents != null) {
        rval = contents.equals("true");
      }
    }

    return rval;
  }

  public boolean createAccount(String email, String password) {
    //  TODO - GET http://my.jbidwatcher.com/users/new
    //  POST http://my.jbidwatcher.com/users with user[email]={email}&user[password]={password}&user[password_confirmation]={password}
    //  If 200 OK, the body contains the my.jbidwatcher.id
    return false;
  }
}
