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
import java.lang.reflect.Constructor;

public class SimpleProxy extends Thread {
  private int _socketNumber;
  private Class _subProxy = ProxyClient.class;
  private ServerSocket _serverSock = null;
  private boolean _halted = false;
  private boolean _running = false;
  private Object _objectToPass = null;

  private void makeProxyServer(int sockNum, Class minorProxyClass) {
    _socketNumber = sockNum;
    _subProxy = minorProxyClass;
  }

  public void halt() {
    if(_serverSock != null) {
      try {
        _halted = true;
        _serverSock.close();
        _serverSock = null;
      } catch(IOException e) {
        //  We don't care if an error occurs on close.
      }
    }
  }

  public void go() {
    if(_serverSock == null) {
      try {
        _serverSock = new ServerSocket(_socketNumber);
      } catch(IOException e) {
        ErrorManagement.handleException("Server socket open failed", e);
      }
    }
    if(_halted) {
      _halted = false;
      this.interrupt();
    } else {
      if(!_running) {
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
    _objectToPass = paramObj;
  }

  public void run() {
    ProxyClient pc;
    Socket acceptedSock = null;

    _running = true;
    boolean done = false;
	while(!done) {
      try {
        if(_serverSock != null) {
          acceptedSock = _serverSock.accept();
        }
      } catch(IOException e) {
        if(!_halted) {
          ErrorManagement.handleException("Exception raised during server accept.", e);
        }
      }
      try {
        synchronized(this) {
          while(_halted) {
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

        if(_objectToPass == null) {
          Class[] paramClasses = { acceptedSock.getClass() };
          Object[] paramObjects = { acceptedSock };

          subProxyParamClasses = paramClasses;
          subProxyParamObjects = paramObjects;
        } else {
          Class[] paramClasses = { acceptedSock.getClass(), _objectToPass.getClass() };
          Object[] paramObjects = { acceptedSock, _objectToPass };

          subProxyParamClasses = paramClasses;
          subProxyParamObjects = paramObjects;
        }

        try {
          Constructor maker;

          maker = _subProxy.getConstructor(subProxyParamClasses);
          pc = (ProxyClient)maker.newInstance(subProxyParamObjects);
          pc.start();
        } catch(Exception e) {
          ErrorManagement.handleException("Serious failure trying to create a ProxyClient object.", e);
        }
      }
    }
  }
}
