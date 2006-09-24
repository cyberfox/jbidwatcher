package com.jbidwatcher.webserver;
/*
 * Copyright (c) 2000-2005 CyberFOX Software, Inc. All Rights Reserved.
 *
 * This library is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Library General Public License as published
 * by the Free Software Foundation; either version 2 of the License, or (at
 * your option) any later version.
 *
 * This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Library General Public License for more
 * details.
 *
 * You should have received a copy of the GNU Library General Public License
 * along with this library; if not, write to the
 *  Free Software Foundation, Inc.
 *  59 Temple Place
 *  Suite 330
 *  Boston, MA 02111-1307
 *  USA
 */

import com.jbidwatcher.Constants;

import java.net.*;

public abstract class HTTPProxyClient extends ProxyClient {
  private final static String AccessDenied =
    "<html>\n" +
    "<head><title>Access denied!</title></head>\n" +
    "<body>Access to this server is denied without an account.</body>\n" +
    "</html>\n";

  private String _serverName;
  protected boolean authorized = false;
  protected String requestedFile = null;
  protected boolean firstBlank = true;

  protected HTTPProxyClient(Socket talkSock) {
    super(talkSock);
    _serverName = "Generic HTTP Proxy Server/0.1.0alpha (Java)";
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

        authorized = handleAuthorization(authString);
      }
    }
  }

  protected abstract boolean needsAuthorization(String reqFile);

  protected boolean isDone(String inLine) {
    if(inLine != null) {
      return(inLine.length() == 0);
    }

    return true;
  }

  protected abstract StringBuffer buildHeaders(String whatDocument, byte[][] buf);

  protected String getServerName() {
    return _serverName;
  }

  protected void setServerName(String servName) {
    _serverName = servName;
  }

  protected StringBuffer buildHTML(String whatDocument) {
    StringBuffer outBuf = new StringBuffer(15000);

    outBuf.append("<HTML>\n");
    outBuf.append("<HEAD><TITLE>Default proxy handler document request</TITLE></HEAD>\n");
    outBuf.append("<BODY>\n");
    outBuf.append("Requesting: <B>");
    outBuf.append(whatDocument);
    outBuf.append("</B>.\n");
    outBuf.append("</BODY>\n</HTML>\n");

    return(outBuf);
  }

  protected String anyResponse(byte[][] buf) {
    StringBuffer totalResponse = new StringBuffer(15000);

    if(authorized) {
      StringBuffer headerAddons = buildHeaders(requestedFile, buf);
      StringBuffer builtDocument = buildHTML(requestedFile);

      totalResponse.append("HTTP/1.1 200 OK\n");
      totalResponse.append("Server: ");
      totalResponse.append(getServerName());
      totalResponse.append('\n');
      if(headerAddons != null) {
        totalResponse.append(headerAddons);
      } else {
        totalResponse.append("Content-Type: text/html; charset=UTF-8\n");
      }
      totalResponse.append('\n');
      if(builtDocument != null) {
        totalResponse.append(builtDocument);
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

    return(totalResponse.toString());
  }
}
