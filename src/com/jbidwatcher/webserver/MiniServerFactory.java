/*
 * Created by IntelliJ IDEA.
 * User: mrs
 * Date: 12/20/14
 * Time: 10:45 PM
 */
package com.jbidwatcher.webserver;

import com.jbidwatcher.util.ToolInterface;

import java.net.Socket;

public interface MiniServerFactory {
  AbstractMiniServer create(Socket talkSock);
  AbstractMiniServer create(Socket talkSock, ToolInterface tool);
  AbstractMiniServer create(Socket talkSock, Object param);
}
