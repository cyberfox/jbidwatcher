package com.jbidwatcher.app;

import com.jbidwatcher.webserver.HTTPProxyClient;
import com.jbidwatcher.util.StringTools;
import com.jbidwatcher.util.config.ErrorManagement;
import com.jbidwatcher.auction.AuctionEntry;

import java.net.Socket;
import java.io.FileNotFoundException;

/**
 * Created by IntelliJ IDEA.
* User: mrs
* Date: Nov 16, 2008
* Time: 1:34:30 AM
* To change this template use File | Settings | File Templates.
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

  protected boolean handleAuthorization(String inAuth) {
    return true;
  }

  protected boolean needsAuthorization(String reqFile) {
    return false;
  }

  protected StringBuffer buildHeaders(String whatDocument, byte[][] buf) throws FileNotFoundException {
    return null;
  }

  protected StringBuffer buildHTML(String whatDocument) {
    if(whatDocument.indexOf("/") != -1) {
      whatDocument = whatDocument.substring(whatDocument.indexOf("/") +1);
    }
    if(StringTools.isNumberOnly(whatDocument)) {
      ErrorManagement.logDebug("Retrieving auction: " + whatDocument);
      return AuctionEntry.retrieveAuctionXML(whatDocument);
    } else if(whatDocument.equals("SHUTDOWN")) {
      ErrorManagement.logDebug("Shutting down.");
      mTool.done();
    }
    return new StringBuffer();
  }
}
