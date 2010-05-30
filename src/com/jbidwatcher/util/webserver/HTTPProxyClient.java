package com.jbidwatcher.util.webserver;
/*
 * Copyright (c) 2000-2007, CyberFOX Software, Inc. All Rights Reserved.
 *
 * Developed by mrs (Morgan Schweers)
 */

import com.jbidwatcher.util.Constants;

import java.net.*;
import java.io.FileNotFoundException;

public abstract class HTTPProxyClient extends ProxyClient {
  private final static String AccessDenied =
    "<html>\n" +
    "<head><title>Access denied!</title></head>\n" +
    "<body>Access to this server is denied without an account.</body>\n" +
    "</html>\n";

  private String _serverName;
  protected boolean authorized = false;
  protected String requestedFile = null;

  protected HTTPProxyClient(Socket talkSock) {
    super(talkSock);
    _serverName = "Generic HTTP Proxy Server/0.2.0alpha (Java)";
  }

  private final static String authTitle = "Authorization: Basic ";
  protected abstract boolean handleAuthorization(String inAuth);

  protected void handleLine(String inLine) {
    if(inLine.startsWith("GET ")) {
      int spaceLocation = inLine.lastIndexOf(' ');
      if(spaceLocation == -1) spaceLocation = inLine.length();
      requestedFile = inLine.substring(4, spaceLocation);
      authorized = !needsAuthorization(requestedFile);
    }

    if(!authorized) {
      if(inLine.startsWith(authTitle)) {
        String authString = inLine.substring(authTitle.length());

        try {
          authorized = handleAuthorization(authString);
        } catch (Exception e) {
          authorized = false;
        }
      }
    }
  }

  protected abstract boolean needsAuthorization(String reqFile);

  protected boolean isDone(String inLine) {
    return inLine == null || (inLine.length() == 0);
  }

  protected abstract StringBuffer buildHeaders(String whatDocument, byte[][] buf) throws FileNotFoundException;

  protected String getServerName() {
    return _serverName;
  }

  protected void setServerName(String servName) {
    _serverName = servName;
  }

  protected StringBuffer buildHTML(String whatDocument) throws FileNotFoundException {
    StringBuffer outBuf = new StringBuffer(15000);

    outBuf.append("<html>\n");
    outBuf.append("<head><title>Default proxy handler document request</title></head>\n");
    outBuf.append("<body>\n");
    outBuf.append("Requesting: <b>");
    outBuf.append(whatDocument);
    outBuf.append("</b>.\n");
    outBuf.append("</body>\n");
    outBuf.append("</html>\n");

    return(outBuf);
  }

  protected String anyResponse(byte[][] buf) {
    StringBuffer totalResponse = new StringBuffer(15000);

    try {
      if (authorized) {
        StringBuffer headerAddons = buildHeaders(requestedFile, buf);
        StringBuffer builtDocument = buildHTML(requestedFile);

        totalResponse.append("HTTP/1.1 200 OK\n");
        totalResponse.append("Server: ");
        totalResponse.append(getServerName());
        totalResponse.append('\n');
        if (headerAddons != null) {
          totalResponse.append(headerAddons);
        } else {
          totalResponse.append("Content-Type: text/html; charset=UTF-8\n");
          if(builtDocument != null) totalResponse.append("Content-Length: ").append(builtDocument.length()).append('\n');
        }
        if (builtDocument != null) {
          totalResponse.append('\n');
          totalResponse.append(builtDocument);
          totalResponse.append('\n');
        } else {
          totalResponse.append('\n');
        }

        authorized = false;
      } else {
        totalResponse.append("HTTP/1.1 401 Authorization Required\n");
        totalResponse.append("Server: ");
        totalResponse.append(getServerName());
        totalResponse.append('\n');
        totalResponse.append("WWW-Authenticate: Basic realm=\"");
        totalResponse.append(Constants.PROGRAM_NAME);
        totalResponse.append('\"').append('\n');
        totalResponse.append("Content-Type: text/html; charset=UTF-8\n");
        totalResponse.append('\n');
        totalResponse.append(AccessDenied);
        totalResponse.append('\n');
      }
    } catch(FileNotFoundException fnfe) {
      totalResponse.append("HTTP/1.1 404 File Not Found\n");
      totalResponse.append("Server: ");
      totalResponse.append(getServerName());
      totalResponse.append('\n');
      totalResponse.append("Content-Type: text/html; charset=UTF-8\n");
      totalResponse.append("Content-Length: ").append(fnfe.getMessage().length()+1).append('\n');
      totalResponse.append('\n');
      totalResponse.append(fnfe.getMessage());
      totalResponse.append('\n');
    }
    return (totalResponse.toString());
  }
}
