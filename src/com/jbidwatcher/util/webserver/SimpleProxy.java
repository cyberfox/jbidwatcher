package com.jbidwatcher.util.webserver;
/*
 * Copyright (c) 2000-2007, CyberFOX Software, Inc. All Rights Reserved.
 *
 * Developed by mrs (Morgan Schweers)
 */

import com.jbidwatcher.util.config.JConfig;
import com.jbidwatcher.util.ToolInterface;

import java.net.*;
import java.io.*;
import java.lang.reflect.Constructor;

@SuppressWarnings({"ClassExplicitlyExtendsThread"})
public class SimpleProxy extends Thread {
  private int mSocketNumber;
  private Class mSubProxy = ProxyClient.class;
  private ServerSocket mServerSock = null;
  private boolean mHalted = false;
  private boolean mRunning = false;
  private Object mObjectToPass = null;

  private void makeProxyServer(int sockNum, Class minorProxyClass) {
    mSocketNumber = sockNum;
    mSubProxy = minorProxyClass;
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

  public SimpleProxy(int sockNum) {
    makeProxyServer(sockNum, ProxyClient.class);
  }

  public SimpleProxy(int sockNum, Class minorProxyClass) {
    makeProxyServer(sockNum, minorProxyClass);
  }

  public SimpleProxy(int sockNum, Class minorProxyClass, Object paramObj) {
    setName("SimpleProxy");
    makeProxyServer(sockNum, minorProxyClass);
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
        Class[] subProxyParamClasses;
        Object[] subProxyParamObjects;

        if(mObjectToPass == null) {
          Class[] paramClasses = { acceptedSock.getClass() };
          Object[] paramObjects = { acceptedSock };

          subProxyParamClasses = paramClasses;
          subProxyParamObjects = paramObjects;
        } else {
          Class[] paramClasses = { acceptedSock.getClass(), (mObjectToPass instanceof ToolInterface) ? ToolInterface.class : mObjectToPass.getClass() };
          Object[] paramObjects = { acceptedSock, mObjectToPass};

          subProxyParamClasses = paramClasses;
          subProxyParamObjects = paramObjects;
        }

        try {
          Constructor maker = mSubProxy.getConstructor(subProxyParamClasses);
          ProxyClient pc = (ProxyClient) maker.newInstance(subProxyParamObjects);
          pc.start();
        } catch(Exception e) {
          JConfig.log().handleException("Serious failure trying to create a ProxyClient object.", e);
        }
      }
    }
  }
}
