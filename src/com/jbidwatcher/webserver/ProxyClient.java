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

import com.jbidwatcher.util.ErrorManagement;

import java.net.*;
import java.io.*;

public abstract class ProxyClient extends Thread {
  Socket clientSock = null;

  protected ProxyClient(Socket talkSock) {
    setSocket(talkSock);
  }

  private void setSocket(Socket talkSock) {
    clientSock = talkSock;
  }

  protected abstract void handleLine(String inLine);

  protected abstract boolean isDone(String inLine);

  protected abstract String anyResponse(byte[][] buf);

  public void run() {
    try {
      InputStreamReader isr = new InputStreamReader(clientSock.getInputStream());
      BufferedReader br = new BufferedReader(isr);
      String justOneLine;

      do {
        justOneLine = br.readLine();
        if(justOneLine != null) {
          handleLine(justOneLine);
        }
      } while(justOneLine != null && !isDone(justOneLine));

      byte[][] buf = new byte[1][];
      String responseString = anyResponse(buf);
      if(responseString != null) {
        OutputStream os = clientSock.getOutputStream();
        OutputStreamWriter osw = new OutputStreamWriter(os);
        BufferedWriter bw = new BufferedWriter(osw);
        bw.write(responseString);
        bw.flush();
        if(buf[0] != null) {
          os.write(buf[0], 0, buf[0].length);
        }
        bw.flush();
      }

      clientSock.close();
    } catch(IOException e) {
      ErrorManagement.handleException("Failed during communication with exception.", e);
    }
  }
}
