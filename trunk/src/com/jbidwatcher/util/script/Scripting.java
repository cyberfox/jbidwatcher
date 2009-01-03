package com.jbidwatcher.util.script;

import org.jruby.RubyInstanceConfig;
import org.jruby.Ruby;
import org.jruby.internal.runtime.ValueAccessor;
import org.jruby.javasupport.JavaUtil;
import org.jruby.javasupport.JavaEmbedUtils;
import org.jruby.runtime.builtin.IRubyObject;

import java.util.ArrayList;

/**
 * User: Morgan
 * Date: Dec 16, 2007
 * Time: 6:53:02 PM
 *
 * Scripting interface so things can call Ruby methods easily.
 */
public class Scripting {
  private static Object sRuby = null;
  private static Object sJBidwatcher = null;

  private Scripting() { }

  public static Ruby getRuntime() { return (Ruby)sRuby; }

  public static void initialize() throws ClassNotFoundException {
//    System.err.println("Before: " + System.currentTimeMillis());
    //  Test for JRuby's presence
    Class.forName("org.jruby.RubyInstanceConfig", true, Thread.currentThread().getContextClassLoader());

    final RubyInstanceConfig config = new RubyInstanceConfig();
    final Ruby runtime = Ruby.newInstance(config);

    String[] args = new String[0];
    IRubyObject argumentArray = runtime.newArrayNoCopy(JavaUtil.convertJavaArrayToRuby(runtime, args));
    runtime.defineGlobalConstant("ARGV", argumentArray);
    runtime.getGlobalVariables().defineReadonly("$*", new ValueAccessor(argumentArray));
    runtime.getGlobalVariables().defineReadonly("$$", new ValueAccessor(runtime.newFixnum(System.identityHashCode(runtime))));
    runtime.getLoadService().init(new ArrayList());

//    System.err.println("Middle: " + System.currentTimeMillis());
    runtime.evalScriptlet("require 'builtin/javasupport.rb'; require 'jbidwatcher/utilities';");
//    System.err.println("After : " + System.currentTimeMillis());

    sRuby = runtime;
  }

  public static Object ruby(String command) {
    if(sRuby != null) {
      return ((Ruby)sRuby).evalScriptlet(command);
    } else {
      return null;
    }
  }

  public static Object rubyMethod(String method, Object... method_params) {
    if (sRuby == null) {
      return null;
    }

    if (sJBidwatcher == null) {
      sJBidwatcher = ruby("JBidwatcher");
    }

    return JavaEmbedUtils.invokeMethod((Ruby)sRuby, sJBidwatcher, method, method_params, Object.class);
  }
}
