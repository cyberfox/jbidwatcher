package com.jbidwatcher.app;
/*
 * Copyright (c) 2000-2007, CyberFOX Software, Inc. All Rights Reserved.
 *
 * Developed by mrs (Morgan Schweers)
 */

import com.jbidwatcher.auction.*;
import com.jbidwatcher.util.config.*;
import com.cyberfox.util.config.Base64;
import com.jbidwatcher.util.db.Device;
import com.jbidwatcher.util.html.JHTMLOutput;
import com.jbidwatcher.util.html.JHTMLDialog;
import com.jbidwatcher.util.http.Http;
import com.jbidwatcher.util.*;
import com.jbidwatcher.util.Currency;
import com.jbidwatcher.util.Constants;
import com.jbidwatcher.util.queue.MQFactory;
import com.jbidwatcher.ui.AuctionsManager;
import com.jbidwatcher.auction.server.AuctionServerManager;
import com.jbidwatcher.auction.server.AuctionServer;
import com.jbidwatcher.util.webserver.AbstractMiniServer;
import com.jbidwatcher.util.xml.XMLElement;
import org.json.simple.JSONValue;

import java.net.*;
import java.util.*;
import java.io.*;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.nio.charset.Charset;

public class JBidProxy extends AbstractMiniServer {
  private static final String activateSnipe = "activateSnipe?";
  private static final String findIDString = "id";
  private static final String findAmountString = "snipeamount";
  private static final String syndicate = "syndicate/";
  private static final String event = "event";
  private static final String messageFinisher = "<br>Return to <a href=\"" + Constants.PROGRAM_NAME + "\">auction list</a>.";
  private static StringBuffer sIcon = null;

  public JBidProxy(Socket talkSock) {
    super(talkSock);
    commonSetup();
    setName("JBidProxy");
  }

  private void commonSetup() {
    setServerName(Constants.PROGRAM_NAME + '/' + Constants.PROGRAM_VERS + " (Java)");
  }

  protected boolean needsAuthorization(String reqFile) {
    if(JConfig.queryConfiguration("allow.syndication", "true").equals("true")) {
      if(reqFile.charAt(0) == '/') {
        reqFile = reqFile.substring(1);
      }

      if(reqFile.startsWith(syndicate) || reqFile.endsWith(".jpg") || reqFile.startsWith("register")) return false;
    }
    return true;
  }

  protected boolean handleAuthorization(String inAuth) {
    String converted = Base64.decodeToString(inAuth);
    String[] params = converted.split(":");
    if(params.length != 2) return false;
    String user = params[0];
    String pass = params[1];

    //  If the device has verified itself, then we're okay.
    if(deviceVerify(user, pass)) return true;

    //  TODO -- Actually, validate against ASM, and it can *find* the correct
    //  TODO -- server/user combination and restrict the display/interaction to that server.
    AuctionServer aucServ = AuctionServerManager.getInstance().getServer();
    return aucServ.validate(user, pass);
  }

  protected StringBuffer buildHeaders(String whatDocument, byte[][] buf) throws FileNotFoundException {
    String relativeDocument = whatDocument;
    StringBuffer outBuf = new StringBuffer(256);

    if(relativeDocument.charAt(0) == '/') {
      relativeDocument = relativeDocument.substring(1);
    }

    if(relativeDocument.equals("favicon.ico")) {
      try {
        URL resource = JConfig.getResource("/icons/favicon.ico");
        if(sIcon == null) sIcon = new StringBuffer(Http.net().receivePage(resource.openConnection()));

        outBuf.append("Content-Type: image/x-icon\n");
        outBuf.append("Content-Length: ").append(sIcon.length()).append('\n');

        return outBuf;
      } catch (IOException ignored) {
        throw new FileNotFoundException("favicon.ico");
      }
    }

    if(relativeDocument.endsWith(".jpg")) {
      dumpImage(relativeDocument, outBuf, buf);
      return outBuf;
    }

    if(relativeDocument.equals("synchronize") || relativeDocument.startsWith(syndicate) || relativeDocument.endsWith(".xml")) {
      outBuf.append("Content-Type: text/xml\n");
    } else {
      outBuf.append("Content-Type: text/html; charset=").append(Charset.defaultCharset()).append('\n');
    }
    return outBuf;
  }

  private void dumpImage(String relativeDocument, StringBuffer outbuf, byte[][] buf) {
    String outPath = JConfig.queryConfiguration("auctions.savepath");
    if(outPath == null || outPath.length() == 0) return;

    String imgPath = outPath + System.getProperty("file.separator") + relativeDocument;
    File fp = new File(imgPath);
    if(fp.exists()) {
      outbuf.append("Content-Type: image/jpeg\n");
      outbuf.append("Content-Length: ").append(fp.length()).append('\n');
      StringTools.cat(fp, buf);
    }
  }

  protected StringBuffer checkError(StringBuffer result) {
    if(result != null) return result;

    return new JHTMLOutput("Invalid request", "Failed to correctly perform server-side actions." +
                           messageFinisher).getStringBuffer();
  }

  private static Object[][] sRoutes = {
      {"returnNull", "(.*)\\.jpg$"},
      {"syndicate", "syndicate/(.*)\\.xml"},
      {"show", "^(cached_)?([0-9]+)$"},
      {"favicon", "^favico.ico$"},
      {"snipePage", "^snipe\\?id=([0-9]+)$"},
      {"addAuction", "^addAuction\\?id=([0-9]+)[^0-9]*$"},
      {"cancelSnipe", "^cancelSnipe\\?id=([0-9]+)[^0-9]*$"},
      {"fireEvent", "^event?name=([^&]+)&param=(.*)$"},
      {"index", "(?i)^jbidwatcher$"},
      {"synchronize", "synchronize"},
      {"doSnipe", "^activateSnipe\\?id=([0-9]+)&snipeamount=(.*)(?:&[a-zA-Z]*)?$"},
      // APIs
      {"categories", "^categories.json$"},
      {"register", "^register\\?device=(.+)$"},
      {"auctionsInCategory", "^category/(.*).xml$"},
      {"showXML", "^([0-9]+)\\.xml$"}
  };

  public StringBuffer returnNull(String identifier) {
    return new StringBuffer();
  }

  protected Object[][] getRoutes() {
    return sRoutes;
  }

  public StringBuffer auctionsInCategory(String categoryName) {
    String serviceURL = JConfig.queryConfiguration("tmp.service.url");

    try {
      String category = URLDecoder.decode(categoryName, "UTF-8");
      Category tab = Category.findFirstByName(category);
      List<AuctionEntry> auctions = AuctionEntry.findAllBy("category_id", tab.get("id"));
      XMLElement xauctions = new XMLElement("auctions");
      for(AuctionEntry ae : auctions) {
        XMLElement child = ae.toXML();
        if(ae.getThumbnail() != null) {
          XMLElement thumbnail = new XMLElement("thumbnail");
          thumbnail.setContents(serviceURL + "/" + ae.getIdentifier() + ".jpg");
          child.addChild(thumbnail);
        }
        XMLElement url = new XMLElement("url");
        url.setContents(ae.getBrowseableURL());
        child.addChild(url);
        xauctions.addChild(child);
      }
      StringBuffer sb = new StringBuffer("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
      sb.append("<!DOCTYPE auctions [\n" +
          "  <!ENTITY pound \"#\">\n" +
          "]>\n");
      sb.append(xauctions.toStringBuffer());
      return sb;
    } catch(UnsupportedEncodingException crazy) {
      JConfig.log().logDebug("Couldn't decode the category name.");
    }
    return null;
  }

  public StringBuffer snipePage(String identifier) { return checkError(setupSnipePage(identifier)); }

  public StringBuffer favicon() { return sIcon; }

  public StringBuffer show(String cached, String identifier) {
    boolean isCached = cached != null && cached.length() != 0;

    AuctionEntry ae = AuctionEntry.findByIdentifier(identifier);

    if (ae == null) {
      return (new JHTMLOutput("Error!", "Error: No such auction in list!" + messageFinisher).getStringBuffer());
    }

    StringBuffer sbOut;

    if (isCached) {
      sbOut = new StringBuffer("<html><head><title>").append(ae.getTitle()).append("<link rel=\"shortcut icon\" href=\"/favicon.ico\"/></head><body><b>This is <a href=\"http://www.jbidwatcher.com\">JBidwatcher</a>'s cached copy.</b><br>");
      sbOut.append("This feature has been removed as it unnecessarily complicated the code.  Click here for the <a href=\"").append(ae.getBrowseableURL()).append("\">current page</a>.<hr>");
//      sbOut.append(ae.getContent());
    } else {
      sbOut = getAuctionHTMLFromServer(ae);
    }

    return sbOut;
  }

  public StringBuffer showXML(String identifier) {
    AuctionEntry ae = AuctionEntry.findByIdentifier(identifier);
    if(ae == null) return null;
    return new StringBuffer(ae.toXML().toString());
  }

  public StringBuffer categories() {
    Map<String, Integer> nameCountMap = new TreeMap<String, Integer>();
    List<Category> allCategories = Category.all();
    for(Category c : allCategories) {
      nameCountMap.put(c.getName(), AuctionEntry.countByCategory(c));
    }
    String json = JSONValue.toJSONString(nameCountMap);
    JConfig.log().logMessage("Returning: " + json);
    return (new StringBuffer(json));
  }

  public StringBuffer register(String device) {
    Device d = Device.findByDevice(device);
    if(d == null) {
      d = new Device(device);
    } else {
      d.refreshKey();
    }

    JConfig.log().logMessage("Registered device: " + device);

    MQFactory.getConcrete("Swing").enqueue("SECURITY " + d.getString("security_key"));
    return new StringBuffer("Enter the security digits");
  }

  public boolean deviceVerify(String device, String code) {
    Device remoteDevice = Device.findByDevice(device);
    return remoteDevice != null && code.equals(remoteDevice.getString("security_key"));
  }

  public StringBuffer index() {
    AuctionsManager.getInstance().saveAuctions();
    return checkError(AuctionTransformer.outputHTML(JConfig.queryConfiguration("savefile", "auctions.xml")));
  }

  public StringBuffer synchronize() {
    StringBuffer wholeData = new StringBuffer(25000);

    wholeData.append("<?xml version=\"1.0\"?>\n\n");
    wholeData.append(Constants.XML_SAVE_DOCTYPE);
    AuctionServerManager.getInstance().toXML().toStringBuffer(wholeData);

    return wholeData;
  }

  public StringBuffer fireEvent(String eventName, String eventParam) {
    if(eventName == null || eventParam == null) {
      return new JHTMLOutput("Invalid event", "No such event available." + messageFinisher).getStringBuffer();
    }
    eventParam = eventParam.replaceAll("\\+", " ").replaceAll("%20", " ");
    JConfig.log().logMessage("Firing event to queue '" + eventName + "' with parameter '" + eventParam + "'");
    MQFactory.getConcrete(eventName).enqueue(eventParam);
    return new JHTMLOutput("Event posted", "Event has been submitted." + messageFinisher).getStringBuffer();
  }

  public StringBuffer syndicate(String s) {
    return new StringBuffer(15000).
        append("<?xml version=\"1.0\" ?>\n").
        append("<rss version=\"0.91\">\n").
        append("  <channel>\n").
        append("    <title>JBidwatcher Auctions</title>\n").
        append("    <link>/syndicate/").append(s).append(".xml</link>\n").
        append("    <description>").append(labelToDescription.get(s)).append("</description>").
        append("    <language>en-us</language>").
        append(genItems(s)).
        append("  </channel>\n").
        append("</rss>\n");
  }

  private StringBuffer setupSnipePage(String auctionId) {
    AuctionEntry ae = AuctionEntry.findByIdentifier(auctionId);

    if (ae == null) return (null);
    Currency minBid;

    try {
      minBid = ae.getCurBid().add(ae.getServer().getMinimumBidIncrement(ae.getCurBid(), ae.getNumBidders()));
    } catch (Currency.CurrencyTypeException ignored) {
      minBid = ae.getCurBid();
    }

    JHTMLOutput jho = new JHTMLOutput("Prepare snipe",
        new JHTMLDialog("Snipe", "./activateSnipe", "GET",
            findIDString, auctionId, "snipe",
            "Enter snipe amount, with no currency symbols.", findAmountString, 20,
            minBid.getValueString()) +
            messageFinisher);

    return jho.getStringBuffer();
  }

  /**
   * Add Auction by identifier
   *
   * @param identifier - the Id of the auction to be added
   * @return Redisplay of the auctions XML.
   */
  public StringBuffer addAuction(String identifier) {
    //Add new Auction to Auction Manager
    AuctionEntry auctionEntry = EntryFactory.getInstance().conditionallyAddEntry(false, identifier, null);

    //show Overview
    AuctionsManager.getInstance().saveAuctions();
    return checkError(AuctionTransformer.outputHTML(JConfig.queryConfiguration("savefile", "auctions.xml")));
  }

  public StringBuffer doSnipe(String auctionId, String snipeAmount) {
    AuctionEntry ae = AuctionEntry.findByIdentifier(auctionId);
    Currency snipeValue = Currency.getCurrency(ae.getCurBid().fullCurrencyName(), snipeAmount);

    JConfig.log().logDebug("Remote-controlled snipe activated against auction " + auctionId + " for " + snipeValue);
    ae.prepareSnipe(snipeValue);
    MQFactory.getConcrete("redraw").enqueue(ae.getIdentifier());

    JHTMLOutput jho = new JHTMLOutput("Activated snipe!",
        "Remote-controlled snipe activated on: " +
            auctionId + " for " + snipeValue +
            messageFinisher);
    return jho.getStringBuffer();
  }

  public StringBuffer cancelSnipe(String identifier) {
    Snipeable ae = AuctionEntry.findByIdentifier(identifier);

    if (ae != null) {
      ae.cancelSnipe(false);
      MQFactory.getConcrete("redraw").enqueue(ae.getIdentifier());
      return new JHTMLOutput("Snipe canceled!", "Cancellation of snipe successful." +
          messageFinisher).getStringBuffer();
    }
    return new JHTMLOutput("Could not find auction!",
        "Cancellation of snipe failed, could not find auction " + identifier + '!' +
            messageFinisher).getStringBuffer();
  }

  private StringBuffer getAuctionHTMLFromServer(AuctionEntry ae) {
    StringBuffer sbOut = new StringBuffer("<html><head><title>").append(ae.getTitle()).append("<link rel=\"shortcut icon\" href=\"/favicon.ico\"/></head><body><b>JBidwatcher View</b><br>");
    sbOut.append("Click here for the <a href=\"").append(ae.getBrowseableURL()).append("\">current page</a>.<br>");

    sbOut.append("<hr><br>");
    AuctionServerInterface aucServ = ae.getServer();
    try {
      sbOut.append(checkError(aucServ.getAuction(StringTools.getURLFromString(ae.getBrowseableURL()))));
    } catch (FileNotFoundException ignored) {
      sbOut.append("<b><i>Item no longer appears on the server.</i></b><br>\n");
    }
    return sbOut;
  }

  private DateFormat df = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z");

  private StringBuffer genItems(String s) {
    StringBuffer sb = new StringBuffer(1500);
    List<AuctionEntry> allEnded = null;

    if(s.equals("ended")) {
      allEnded = AuctionEntry.findRecentlyEnded(Constants.SYNDICATION_ITEM_COUNT);
    } else if(s.equals("ending")) {
      allEnded = AuctionEntry.findEndingSoon(Constants.SYNDICATION_ITEM_COUNT);
    } else if(s.equals("bid")) {
      allEnded = AuctionEntry.findBidOrSniped(Constants.SYNDICATION_ITEM_COUNT);
    }

    //  If no valid RSS feed type was given, return an empty feed.
    if(allEnded == null) allEnded = new ArrayList<AuctionEntry>();

    for(AuctionEntry ae : allEnded) {
      sb.append("<item>\n");
      sb.append("<title><![CDATA[");
      sb.append(StringTools.stripHigh(ae.getTitle()));
      sb.append("]]></title>\n");

      sb.append("<link><![CDATA[");
      sb.append(ae.getBrowseableURL());
      sb.append("]]></link>\n");

      sb.append("<pubDate>");
      sb.append(df.format(ae.getEndDate()));
      sb.append("</pubDate>");

      sb.append("<description><![CDATA[");
      sb.append(ae.getPresenter().buildInfo(true));
      sb.append("]]></description>\n</item>\n");
    }

    return sb;
  }

  Map<String, String> labelToDescription;

  {
    labelToDescription = new HashMap<String, String>();
    labelToDescription.put("ended", "List of items ended recently.");
    labelToDescription.put("ending", "List of items ending soon.");
    labelToDescription.put("bid", "List of items being bid/sniped on.");
  }
}
