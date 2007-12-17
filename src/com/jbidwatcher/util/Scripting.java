package com.jbidwatcher.util;

import org.apache.bsf.BSFEngine;
import org.apache.bsf.BSFException;
import org.apache.bsf.BSFManager;

/**
 * User: Morgan
 * Date: Dec 16, 2007
 * Time: 6:53:02 PM
 *
 * Scripting interface so things can call Ruby methods easily.
 */
public class Scripting {
  private static BSFEngine sRuby;

  static {
    try {
      BSFManager.registerScriptingEngine("ruby", "org.jruby.javasupport.bsf.JRubyEngine", new String[]{"rb"});
      BSFManager ruby = new BSFManager();
      Scripting.sRuby = ruby.loadScriptingEngine("ruby");
    } catch (BSFException e) {
      ErrorManagement.handleException("Couldn't load ruby interpreter!", e);
    }
  }

  public static void ruby(String command) {
    ErrorManagement.logDebug("Executing: " + command);

    try {
      sRuby.exec("ruby", 1, 1, command);
    } catch (BSFException e) {
      ErrorManagement.handleException("Error executing ruby code!", e);
    }
  }

  public static Object rubyMethod(String method, Object... method_params) {
    ErrorManagement.logDebug("Executing: " + method + " with (" + StringTools.comma(method_params) + ")");

    try {
      return sRuby.call(null, method, method_params);
    } catch (BSFException e) {
      ErrorManagement.handleException("Failed to execute: " + method, e);
    }

    return null;
  }
}
