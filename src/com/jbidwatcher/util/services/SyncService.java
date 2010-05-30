package com.jbidwatcher.util.services;

import com.jbidwatcher.util.config.JConfig;
import com.orbus.mahalo.Mahalo;
import com.orbus.mahalo.ServiceInfo;

import java.io.IOException;
import java.net.*;
import java.util.Enumeration;

/**
 * Code to support advertising an auction sync service.
 *
 * Usage:<pre><code>
 *   service = new SyncService(9099); // Create a new service
 *   service.advertise();             //  advertise on port 9099 (passed in above)
 *   service.stopAdvertising();       //  Stop advertising, but leave the service running
 *   service.stop();                  //  Stop all advertising, and close the service.
 *</code>
 * @author mrs
 * @date May 28, 2010
 * @time 3:23:00 PM
 *
 */
public class SyncService {
  private Mahalo mDNS;
  private String hostName;
  private String hostIP;
  private String serviceURL;
  private int servicePort;
  private ServiceInfo mService;

  private static InetAddress getFirstNonLoopbackAddress(boolean preferIpv4, boolean preferIPv6) throws SocketException {
    Enumeration en = NetworkInterface.getNetworkInterfaces();
    while (en.hasMoreElements()) {
      NetworkInterface i = (NetworkInterface) en.nextElement();
      for (Enumeration en2 = i.getInetAddresses(); en2.hasMoreElements();) {
        InetAddress addr = (InetAddress) en2.nextElement();
        if (!addr.isLoopbackAddress()) {
          if (addr instanceof Inet4Address) {
            if (preferIPv6) {
              continue;
            }
            return addr;
          }
          if (addr instanceof Inet6Address) {
            if (preferIpv4) {
              continue;
            }
            return addr;
          }
        }
      }
    }
    return null;
  }

  public SyncService(int port) {
    try {
      InetAddress ia = getFirstNonLoopbackAddress(true, false);
      hostIP = ia.getHostAddress();
      hostName = ia.getCanonicalHostName();
      if(hostName.matches("([1-9][0-9]*\\.){3}[1-9][0-9]*") /* || hostIP.equals(hostName) */) {
        String username = System.getProperty("user.name");
        username = username.replaceAll("[ '\"!@#$%^&*()=+:;\\[\\]{}\\|<>,.?/]", "_");
        hostName = username + "_jbidwatcher";
      }
      servicePort = port;
      serviceURL = "http://" + hostIP + ":" + servicePort;
      JConfig.setConfiguration("tmp.service.url", serviceURL);
      mDNS = new Mahalo(null, hostName);
      mDNS.start();
    } catch (Exception ignored) {
      JConfig.log().handleException("Failed to register mDNS", ignored);
    }
  }

  public boolean advertise() {
    if(mDNS == null) return false;
    mService = new ServiceInfo("_auction._tcp.local.", hostName, servicePort, serviceURL);
    try {
      mDNS.registerService(mService);
    } catch(IOException ioe) {
      return false;
    }
    return true;
  }

  public void stopAdvertising() {
    if (mDNS == null) return;

    mDNS.unregisterService(mService);
  }

  public void stop() {
    mDNS.close();
  }
}
