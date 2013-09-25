package com.jbidwatcher.my;

import com.cyberfox.util.config.Base64;
import com.jbidwatcher.util.config.JConfig;
import com.cyberfox.util.config.ErrorHandler;
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
import com.jbidwatcher.util.xml.XMLInterface;
import com.jbidwatcher.util.http.Http;
import com.jbidwatcher.util.http.ClientHttpRequest;
import com.jbidwatcher.util.http.HttpInterface;
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
  private HttpInterface mNet = null;
  private static String LOG_UPLOAD_URL =  "my.jbidwatcher.com/upload/log";
  private static String ITEM_UPLOAD_URL = "my.jbidwatcher.com/upload/listing";
  private static String SYNC_UPLOAD_URL = "my.jbidwatcher.com/upload/sync";
  private static String THUMBNAIL_UPLOAD_URL = "my.jbidwatcher.com/upload/thumbnail";
  private String mSyncQueueURL = null;
  private String mReportQueueURL = null;
  private String mGixenQueueURL = null;
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

  private HttpInterface http() {
    if(mNet == null) {
      mNet = new Http();
    }

    mNet.setAuthInfo(JConfig.queryConfiguration("my.jbidwatcher.id"), JConfig.queryConfiguration("my.jbidwatcher.key"));

    return mNet;
  }

  public String sendLogFile(String email, String desc) {
    JConfig.log().pause();
    File fp = JConfig.log().closeLog();
    String result = sendFile(fp, url(LOG_UPLOAD_URL), email, desc);
    JConfig.log().openLog(fp);
    JConfig.log().resume();
    return result;
  }

  private static String createFormSource(String formBase, String email, String desc) {
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
          ClientHttpRequest chr = new ClientHttpRequest(JConfig.getURL(url));
          chr.setParameters(form.getCGIMap());
          chr.setParameter("file", f);
          HttpURLConnection huc = chr.post();
          InputStream resp = http().getStream(huc);
          result = StringTools.cat(resp);
//          JConfig.log().logDebug(result);
          resp.close();
        }
      }
    } catch (IOException e) {
      JConfig.log().handleDebugException("Trying to upload a file to S3", e);
    }
    return result;
  }

  public String recognizeBidpage(String identifier, StringBuffer page) {
    if(canParse()) {
      Parameters p = new Parameters();
      if (identifier != null) p.put("item", identifier);
      p.put("body", page);
      String url = url("my.jbidwatcher.com/services/recognize");
      return http().postTo(url, p);
    } else {
      return null;
    }
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

    if (queue != null) http().putTo(queue, aucXML);
  }

  void checkUpdated(String pair) {
    String[] params = pair.split(",");
    String identifier = params[0];
    boolean changed = Boolean.parseBoolean(params[1]);
    My status = My.findByIdentifier(identifier);

    if (status == null || status.getDate("last_synced_at") == null || changed) {
      EntryCorral.getInstance().takeForWrite(identifier);
      EntryCorral.getInstance().erase(identifier);
      MQFactory.getConcrete("upload").enqueue(identifier);
    }
  }

  private MyJBidwatcher() {
    MQFactory.getConcrete("my_account").registerListener(new MessageQueue.Listener() {
      public void messageAction(Object deQ) {
        String cmd = (String) deQ;
        if(cmd.equals("ACCOUNT")) {
          getAccountInfo();

          MQFactory.getConcrete("my").registerListener(new MessageQueue.Listener() {
            public void messageAction(Object deQ) {
              String cmd = (String) deQ;
              if (JConfig.queryConfiguration("my.jbidwatcher.enabled", "false").equals("true")) {
                if (cmd.equals("ACCOUNT")) getAccountInfo();
                if (cmd.startsWith("UPDATE ")) checkUpdated(cmd.substring(7));
                if (cmd.startsWith("SYNC ")) uploadAuctionList(cmd.substring(5));
                if (cmd.startsWith("SNIPE ")) doGixen(cmd.substring(6), false);
                if (cmd.startsWith("CANCEL ")) doGixen(cmd.substring(7), true);
              }
            }
          });
        }
      }
    });

    //  Get the URLs to POST stuff to, and get a new one every 12 hours.
    SuperQueue.getInstance().preQueue("ACCOUNT", "my_account", System.currentTimeMillis(), Constants.ONE_DAY);

    MQFactory.getConcrete("upload").registerListener(new MessageQueue.Listener() {
      public void messageAction(Object deQ) {
        if(JConfig.queryConfiguration("my.jbidwatcher.id") != null && mSyncQueueURL != null && canSync()) {
          AuctionEntry ae = EntryCorral.getInstance().takeForRead((String) deQ);
          uploadSync(ae);
          uploadThumbnail(ae);
          uploadAuctionHTML(ae, "uploadhtml");
        }
      }
    });

    if(JConfig.queryConfiguration("my.jbidwatcher.id") != null) {
      MQFactory.getConcrete("report").registerListener(new MessageQueue.Listener() {
        public void messageAction(Object deQ) {
          AuctionEntry ae = EntryCorral.getInstance().takeForRead((String)deQ);
          uploadAuctionHTML(ae, "report");
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

  private void uploadSync(AuctionEntry ae) {
    postXML(mSyncQueueURL, ae);
    String identifier = ae.getIdentifier();
    My status = My.findByIdentifier(identifier);
    if (status == null) status = new My(identifier);
    status.setDate("last_synced_at", new Date());
    status.saveDB();
  }

  private void uploadAuctionList(String fname) {
    if(canSync()) {
      File fp = new File(fname);
      if (fp.exists()) {
        sendFile(fp, url(SYNC_UPLOAD_URL), JConfig.queryConfiguration("my.jbidwatcher.id"), "synchronize");
        MQFactory.getConcrete("Swing").enqueue("NOTIFY Finished synchronizing auctions to My JBidwatcher");
      } else {
        MQFactory.getConcrete("Swing").enqueue("NOTIFY No auctions file to synchronize to My JBidwatcher");
      }
    } else {
      MQFactory.getConcrete("Swing").enqueue("NOTIFY Synchronizing auctions to My JBidwatcher is disabled");
    }
  }

  private void uploadThumbnail(AuctionEntry ae) {
    if(canSync()) {
      String identifier = ae.getIdentifier();
      My status = My.findByIdentifier(identifier);
      if (status == null) status = new My(identifier);
      String thumbnailFile = ae.getThumbnail();
      if(!status.getBoolean("thumbnail_uploaded") && thumbnailFile != null) {
        thumbnailFile = thumbnailFile.substring(5); //  Strip "file:" off the thumbnail path
        File fp = new File(thumbnailFile);
        if (fp.exists()) {
          String result = sendFile(fp, url(THUMBNAIL_UPLOAD_URL), JConfig.queryConfiguration("my.jbidwatcher.id"), "thumbnail");
          if(result == null) {
            JConfig.log().logMessage("Failed to upload thumbnail for " + identifier);
          } else {
            status.setBoolean("thumbnail_uploaded", true);
            status.saveDB();
          }
        }
      }
    }
  }

  private void uploadAuctionHTML(AuctionEntry ae, String uploadType) {
    if(canUploadHTML()) {
      String s3Result = sendFile(JConfig.getContentFile(ae.getIdentifier()), url(ITEM_UPLOAD_URL), JConfig.queryConfiguration("my.jbidwatcher.id"), ae.getLastStatus());
      XMLElement root = new XMLElement(uploadType);
      XMLElement s3Key = new XMLElement("s3");
      s3Key.setContents(s3Result);
      root.addChild(ae.toXML());
      postXML(mReportQueueURL, root);
      My status = My.findByIdentifier(ae.getIdentifier());
      String identifier = ae.getIdentifier();
      if (status == null) status = new My(identifier);

      status.setDate("last_uploaded_html", new Date());
      status.saveDB();
    }
  }

  private static boolean canUploadHTML() { return allow("uploadhtml"); }
  private static boolean canSync() { return allow("sync"); }
  private static boolean canParse() { return allow("parser"); }
  private static boolean canGetSnipes() { return allow("snipes"); }
  private static boolean canSendSnipeToGixen() { return allow("gixen"); }

  private static boolean allow(String type) {
    return JConfig.queryConfiguration("my.jbidwatcher.allow." + type, "false").equals("true") &&
           JConfig.queryConfiguration("my.jbidwatcher." + type, "false").equals("true");
  }

  private void doGixen(String identifier, boolean cancel) {
    if(canSendSnipeToGixen() && mGixenQueueURL != null) {
      AuctionEntry ae = EntryCorral.getInstance().takeForRead(identifier);

      //  If we're being asked to set a snipe, and one isn't assigned to the entry, punt.
      if(!cancel && !ae.isSniped()) {
        JConfig.log().logMessage("Submitted auction " + identifier + " to snipe on Gixen, but doesn't have any snipe information set!");
        return;
      }

      //  If we've already submitted a snipe for this listing, don't send it again.
      My status = My.findByIdentifier(identifier);
      if (status != null && ae.getSnipeAmount().equals(status.getMonetary("snipe_amount"))) return;
      if (status == null) status = new My(identifier);

      XMLElement gixen = generateGixenXML(identifier, cancel, ae);
      if (gixen == null) return;
      postXML(mGixenQueueURL, gixen);
      if (cancel) {
        status.setDate("snipe_submitted_at", null);
        status.setMonetary("snipe_amount", null);
      } else {
        status.setDate("snipe_submitted_at", new Date());
        status.setMonetary("snipe_amount", ae.getSnipeAmount());
      }
      status.saveDB();
    }
  }

  private static XMLElement generateGixenXML(String identifier, boolean cancel, AuctionEntry ae) {
    XMLElement gixen = new XMLElement(cancel ? "cancelsnipe" : "snipe");
    gixen.setProperty("AUCTION", identifier);

    if (!cancel) {
      String bid = ae.getSnipeAmount().getValueString();
      gixen.setProperty("AMOUNT", bid);
    }

    XMLElement userInfo = new XMLElement("credentials");
    String user = JConfig.queryConfiguration(ae.getServer().getName() + ".user");
    String password = JConfig.queryConfiguration(ae.getServer().getName() + ".password");
    if(user == null || password == null) {
      JConfig.log().logMessage("Failed to submit snipe to Gixen; one or both of username and password are not set.");
      return null;
    }
    userInfo.setProperty("user", user);
    userInfo.setProperty("password", Base64.encodeString(password));
    userInfo.setEmpty();
    gixen.addChild(userInfo);
    return gixen;
  }

  public boolean getAccountInfo() {
    return getAccountInfo(JConfig.queryConfiguration("my.jbidwatcher.id"), JConfig.queryConfiguration("my.jbidwatcher.key"));
  }

  public boolean getAccountInfo(String username, String password) {
    if(username == null || password == null) return false;
    if (username.length() == 0 || password.length() == 0) return false;
    StringBuffer sb = getRawAccountXML(username, password);
    if(sb == null) return false;

    XMLElement xml = new XMLElement();
    xml.parseString(sb.toString());
    XMLInterface sync = xml.getChild("syncq");
    XMLInterface snipe = xml.getChild("snipeq");
    XMLInterface expires = xml.getChild("expiry");
    XMLInterface listingsRemaining = xml.getChild("listings");
    XMLInterface categoriesRemaining = xml.getChild("categories");
    XMLInterface reporting = xml.getChild("reportq");
    XMLInterface snipesListen = xml.getChild("snipes");
    XMLInterface ssl = xml.getChild("ssl");
    XMLInterface uploadHTML = xml.getChild("uploadhtml");
    XMLInterface serverParser = xml.getChild("parser");
    XMLInterface gixen = xml.getChild("gixen");

    checkExpiration(expires);

    JConfig.setConfiguration("my.jbidwatcher.allow.listings", listingsRemaining.getContents());
    JConfig.setConfiguration("my.jbidwatcher.allow.categories", categoriesRemaining.getContents());

    mSyncQueueURL = sync == null ? null : sync.getContents();
    JConfig.setConfiguration("my.jbidwatcher.allow.sync", Boolean.toString(mSyncQueueURL != null));
    mReportQueueURL = reporting == null ? null : reporting.getContents();
    mUseSSL = getBoolean(ssl);
    JConfig.setConfiguration("my.jbidwatcher.allow.ssl", Boolean.toString(mUseSSL));
    mReadSnipesFromServer = getBoolean(snipesListen);
    JConfig.setConfiguration("my.jbidwatcher.allow.snipes", Boolean.toString(mReadSnipesFromServer));
    mUploadHTML = getBoolean(uploadHTML);
    JConfig.setConfiguration("my.jbidwatcher.allow.uploadhtml", Boolean.toString(mUploadHTML));
    mUseServerParser = getBoolean(serverParser);
    JConfig.setConfiguration("my.jbidwatcher.allow.parser", Boolean.toString(mUseServerParser));
    mGixen = getBoolean(gixen);
    JConfig.setConfiguration("my.jbidwatcher.allow.gixen", Boolean.toString(mGixen));
    mGixenQueueURL = snipe == null ? null : snipe.getContents();

    return mSyncQueueURL != null && mReportQueueURL != null;
  }

  private void checkExpiration(XMLInterface expires) {
    if(expires != null) {
      String date = expires.getContents();
      mExpiry = StringTools.figureDate(date, "yyyy-MM-dd'T'HH:mm:ssZ");
      if(mExpiry == null || mExpiry.getDate() == null || mExpiry.getDate().before(new Date())) {
        JConfig.setConfiguration("my.jbidwatcher.enabled", "false");
      } else {
        JConfig.setConfiguration("my.jbidwatcher.enabled", "true");
      }
    }
  }

  private static StringBuffer getRawAccountXML(String username, String password) {
    String suffix = JConfig.queryConfiguration("ebay.browse.site");
    if(suffix != null && !suffix.equals("0")) {
      suffix = "?browse_to=" + suffix;
    } else suffix = "";
    HttpInterface http = new Http();
    http.setAuthInfo(username, password);
    return http.get("https://my.jbidwatcher.com/services/account" + suffix);
  }

  private static boolean getBoolean(XMLInterface x) {
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
