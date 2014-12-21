/*
 * Created by IntelliJ IDEA.
 * User: mrs
 * Date: 12/20/14
 * Time: 10:45 PM
 */
package com.jbidwatcher.app;

import com.jbidwatcher.util.ToolInterface;
import com.jbidwatcher.util.webserver.AbstractMiniServer;
import com.jbidwatcher.util.webserver.ProxyClient;

import java.net.Socket;

public interface MiniServerFactory {
  AbstractMiniServer create(Socket talkSock);
  AbstractMiniServer create(Socket talkSock, ToolInterface tool);
  AbstractMiniServer create(Socket talkSock, Object param);
}
