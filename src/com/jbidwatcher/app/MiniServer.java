package com.jbidwatcher.app;

import com.jbidwatcher.util.webserver.AbstractMiniServer;
import com.jbidwatcher.util.Currency;
import com.jbidwatcher.util.ToolInterface;
import com.jbidwatcher.util.config.JConfig;
import com.jbidwatcher.auction.*;
import com.jbidwatcher.util.xml.XMLInterface;
import com.jbidwatcher.util.xml.XMLSerialize;

import java.io.FileNotFoundException;
import java.net.Socket;
import java.util.regex.Pattern;

/**
 * User: mrs
 * Date: Nov 16, 2008
 * Time: 1:34:30 AM
 *
 * This is a simple, small server, used by the JBTool to listen for simple commands.
 */
public class MiniServer extends AbstractMiniServer {
  private ToolInterface mTool;

  public MiniServer(Socket talkSock) {
    super(talkSock);
  }

  public MiniServer(Socket talkSock, ToolInterface tool) {
    super(talkSock);
    mTool = tool;
  }

  protected boolean handleAuthorization(String inAuth) { return true; }
  protected boolean needsAuthorization(String reqFile) { return false; }
  protected StringBuffer buildHeaders(String whatDocument, byte[][] buf) throws FileNotFoundException { return null; }

  private static Object[][] sRoutes = {
      //  /390005676820
      {"showItem", Pattern.compile("^(\\d+)$")},
      //  /show/390005676820
      {"showItem", Pattern.compile("^show/(\\d+)$")},
      //  /buy/390005676820
      {"buy", Pattern.compile("^buy/(\\d+)(?:/(\\d+))?$")},
      //  /bid/390005676820/8.27 {or} /bid/390005676820/8,27
      {"bid", Pattern.compile("^bid/(\\d+)/(\\d+[,.]?\\d*)(?:/(\\d+))?$")},
      {"shutdown", Pattern.compile("^shutdown$")},
      {"login", Pattern.compile("^login$")}
  };

  @Override
  protected Object[][] getRoutes() {
    return sRoutes;
  }

  public StringBuffer login() {
    mTool.forceLogin();
    return new StringBuffer("<response>\n  <success><![CDATA[Login requested]]></success>\n</response>\n");
  }

  public StringBuffer shutdown() {
    JConfig.log().logDebug("Shutting down.");
    mTool.done();

    return new StringBuffer("Shutting down.\n");
  }

  public StringBuffer showItem(String identifier) {
    JConfig.log().logDebug("Retrieving auction: " + identifier);
    XMLSerialize xmlable = EntryFactory.getInstance().constructEntry(identifier);
    if(xmlable != null) {
      XMLInterface xe = xmlable.toXML();
      return xe.toStringBuffer();
    }
    return null;
  }

  public StringBuffer buy(String identifier, String howMany) {
    int quantity = getQuantity(howMany);

    AuctionEntry ae = EntryFactory.getInstance().constructEntry(identifier);
    EntryCorral.getInstance().put(ae);
    AuctionBuy ab = new AuctionBuy(ae, null, quantity);
    return fireAction(ae, ab);
  }

  private int getQuantity(String howMany) {
    int quantity = 1;
    if(howMany != null) {
      quantity = Integer.parseInt(howMany);
    }
    return quantity;
  }

  public StringBuffer bid(String identifier, String howMuch, String howMany) {
    int quantity = getQuantity(howMany);
    AuctionEntry ae = EntryFactory.getInstance().constructEntry(identifier);
    AuctionBid ab = new AuctionBid(ae, Currency.getCurrency(ae.getCurBid().getCurrencySymbol(), howMuch), quantity);
    return fireAction(ae, ab);
  }

  private StringBuffer fireAction(AuctionEntry entry, AuctionAction action) {
    String result = action.activate();
    if(result != null) {
      StringBuffer sb = new StringBuffer("<result>\n");
      int numericResult = action.getResult();
      if(numericResult == AuctionServerInterface.BID_BOUGHT_ITEM ||
          numericResult == AuctionServerInterface.BID_SELFWIN ||
          numericResult == AuctionServerInterface.BID_WINNING) {
        sb.append("  <success><![CDATA[").append(result).append("]]></success>\n");
      } else {
        sb.append("  <error><![CDATA[").append(result).append("]]></error>\n");
        if(entry.getErrorPage() != null) {
          sb.append("  <page><![CDATA[").append(entry.getErrorPage()).append("]]></page>\n");
        }
      }

      sb.append("</result>\n");
      return sb;
    } else {
      return null;
    }
  }

}
