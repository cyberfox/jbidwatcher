package com.jbidwatcher.webserver;
/*
 * Copyright (c) 2000-2007, CyberFOX Software, Inc. All Rights Reserved.
 *
 * Developed by mrs (Morgan Schweers)
 */

import com.jbidwatcher.util.config.JConfig;
import com.jbidwatcher.util.ToolInterface;

import java.net.*;
import java.io.*;

@SuppressWarnings({"ClassExplicitlyExtendsThread"})
public class SimpleProxy extends Thread {
  private int mSocketNumber;
  private MiniServerFactory mSubProxy;
  private ServerSocket mServerSock = null;
  private boolean mHalted = false;
  private boolean mRunning = false;
  private Object mObjectToPass = null;

  private void makeProxyServer(int sockNum, MiniServerFactory minorProxyFactory) {
    mSocketNumber = sockNum;
    mSubProxy = minorProxyFactory;
  }

  public void halt() {
    if(mServerSock != null) {
      try {
        mHalted = true;
        mServerSock.close();
        mServerSock = null;
      } catch(IOException e) {
        //  We don't care if an error occurs on close.
      }
    }
  }

  public void go() {
    if(!isDaemon()) setDaemon(true);
    if(mServerSock == null) {
      try {
        mServerSock = new ServerSocket(mSocketNumber);
      } catch(IOException e) {
        JConfig.log().handleException("Server socket open failed", e);
      }
    }
    if(mHalted) {
      mHalted = false;
      this.interrupt();
    } else {
      if(!mRunning) {
        this.start();
      }
    }
  }

  public SimpleProxy(int sockNum, MiniServerFactory minorProxyFactory) {
    makeProxyServer(sockNum, minorProxyFactory);
  }

  public SimpleProxy(int sockNum, MiniServerFactory minorProxyFactory, Object paramObj) {
    setName("SimpleProxy");
    makeProxyServer(sockNum, minorProxyFactory);
    mObjectToPass = paramObj;
  }

  public void run() {
    mRunning = true;
    boolean done = false;
    Socket acceptedSock = null;

    while(!done) {
      try {
        if(mServerSock != null) {
          acceptedSock = mServerSock.accept();
        }
      } catch(IOException e) {
        if(!mHalted) {
          JConfig.log().handleException("Exception raised during server accept.", e);
        }
      }
      try {
        synchronized(this) {
          while(mHalted) {
            acceptedSock = null;
            wait();
          }
        }
      } catch(InterruptedException e) {
        // We've already dealt with clearing acceptedSock, so we're
        // basically done.
        done = true;
      }

      if(acceptedSock != null) {
        ProxyClient pc;
        if(mObjectToPass == null) {
          pc = mSubProxy.create(acceptedSock);
        } else {
          if(mObjectToPass instanceof ToolInterface) {
            pc = mSubProxy.create(acceptedSock, (ToolInterface) mObjectToPass);
          } else {
            pc = mSubProxy.create(acceptedSock, mObjectToPass);
          }
        }

        pc.start();
      }
    }
  }
}
