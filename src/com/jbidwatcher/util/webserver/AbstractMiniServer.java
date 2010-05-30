package com.jbidwatcher.util.webserver;

import com.jbidwatcher.util.config.JConfig;

import java.io.FileNotFoundException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.Socket;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * User: mrs
 * Date: May 29, 2010
 * Time: 2:48:03 PM
 * 
 * A generic web server with a surprisingly clean routing system.  Extend this, and implement
 * getRoutes() to return an array of method names (as string) and a Regex.  The method will be
 * called with N parameters where N is the number of matched groups in the regex.  It makes
 * parsing web requests easy!  You may also need to override buildHeaders if you are returning
 * non-HTML data.  N.b. It only processes routes based on the _last_ segment of the path.
 */
public abstract class AbstractMiniServer extends HTTPProxyClient {
  public AbstractMiniServer(Socket talkSock) { super(talkSock); }

  protected abstract Object[][] getRoutes();

  protected StringBuffer buildHTML(String whatDocument) throws FileNotFoundException {
    if(whatDocument.indexOf("/") != -1) {
      whatDocument = whatDocument.substring(whatDocument.indexOf("/") +1);
    }

    StringBuffer sb = processRoutes(whatDocument);

    if(sb == null) throw new FileNotFoundException(whatDocument);
    if(sb.length()==0) return null;

    return sb;
  }

  private StringBuffer processRoutes(String whatDocument) {
    for(Object[] route : getRoutes()) {
      Pattern routePattern;
      if(route[1] instanceof Pattern) {
        routePattern = (Pattern) route[1];
      } else {
        routePattern = Pattern.compile(route[1].toString());
      }
      String method = (String) route[0];
      Matcher match = routePattern.matcher(whatDocument);

      if(match.find()) {
        int count = match.groupCount();
        Object[] matched = new Object[count];
        Class[] matchedClass = new Class[count];
        for(int i=1; i<= count; i++) {
          matched[i-1] = match.group(i);
          matchedClass[i-1] = String.class;
        }

        try {
          Method m = getClass().getMethod(method, matchedClass);
          return (StringBuffer)m.invoke(this, matched);
        } catch (NoSuchMethodException e) {
          JConfig.log().handleException("Failed to resolve route method for " + route[0], e);
        } catch (IllegalAccessException e) {
          JConfig.log().handleException("Security prevented running route method " + route[0], e);
        } catch (InvocationTargetException e) {
          JConfig.log().handleException("Invokation of route method " + route[0] + " failed.", e);
        }
      }
    }

    return null;
  }
}
