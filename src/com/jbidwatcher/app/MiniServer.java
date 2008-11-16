package com.jbidwatcher.app;

import com.jbidwatcher.webserver.HTTPProxyClient;
import com.jbidwatcher.util.StringTools;
import com.jbidwatcher.util.Currency;
import com.jbidwatcher.util.config.ErrorManagement;
import com.jbidwatcher.auction.*;

import java.net.Socket;
import java.io.FileNotFoundException;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.lang.reflect.*;

/**
 * User: mrs
 * Date: Nov 16, 2008
 * Time: 1:34:30 AM
 *
 * This is a simple, small server, used by the JBTool to listen for simple commands.
 */
public class MiniServer extends HTTPProxyClient {
  private JBTool mTool;

  public MiniServer(Socket talkSock) {
    super(talkSock);
  }

  public MiniServer(Socket talkSock, JBTool tool) {
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
      {"shutdown", Pattern.compile("^shutdown$")}
  };

  public StringBuffer shutdown() {
    ErrorManagement.logDebug("Shutting down.");
    mTool.done();

    return new StringBuffer("Shutting down.");
  }

  public StringBuffer showItem(String identifier) {
    ErrorManagement.logDebug("Retrieving auction: " + identifier);
    return AuctionEntry.retrieveAuctionXML(identifier);
  }

  public StringBuffer buy(String identifier, String howMany) {
    int quantity = getQuantity(howMany);

    AuctionEntry ae = AuctionEntry.construct(identifier);
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
    AuctionEntry ae = AuctionEntry.construct(identifier);

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

  protected StringBuffer buildHTML(String whatDocument) throws FileNotFoundException {
    if(whatDocument.indexOf("/") != -1) {
      whatDocument = whatDocument.substring(whatDocument.indexOf("/") +1);
    }

    StringBuffer sb = processRoutes(whatDocument);

    if(sb == null) throw new FileNotFoundException(whatDocument);

    return sb;
  }

  private StringBuffer processRoutes(String whatDocument) {
    for(Object[] route : sRoutes) {
      Pattern routePattern = (Pattern) route[1];
      String method = (String) route[0];
      Matcher match = routePattern.matcher(whatDocument);

      if(match.find()) {
        int count = match.groupCount();
        Object[] matched = new Object[count];
        Class[] matchedClass = new Class[count];
        for(int i=1; i<= count; i++) {
          matched[i-1] = match.group(i);
          matchedClass[i-1] = String.class;
        }

        try {
          Method m = getClass().getMethod(method, matchedClass);
          return (StringBuffer)m.invoke(this, matched);
        } catch (NoSuchMethodException e) {
          ErrorManagement.handleException("Failed to resolve route method for " + route[0], e);
        } catch (IllegalAccessException e) {
          ErrorManagement.handleException("Security prevented running route method " + route[0], e);
        } catch (InvocationTargetException e) {
          ErrorManagement.handleException("Invokation of route method " + route[0] + " failed.", e);
        }
      }
    }

    return null;
  }
}
