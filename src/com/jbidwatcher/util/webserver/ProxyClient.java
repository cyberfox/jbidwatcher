package com.jbidwatcher.util.webserver;
/*
 * Copyright (c) 2000-2007, CyberFOX Software, Inc. All Rights Reserved.
 *
 * Developed by mrs (Morgan Schweers)
 */

import com.jbidwatcher.util.config.JConfig;

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
        bw.close();
      }

      clientSock.close();
    } catch(IOException e) {
      JConfig.log().handleException("Failed during communication with exception.", e);
    }
  }
}
