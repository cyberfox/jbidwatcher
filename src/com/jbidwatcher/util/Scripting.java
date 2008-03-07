package com.jbidwatcher.util;

import org.jruby.RubyInstanceConfig;
import org.jruby.Ruby;
import org.jruby.internal.runtime.ValueAccessor;
import org.jruby.javasupport.JavaUtil;
import org.jruby.javasupport.JavaEmbedUtils;
import org.jruby.runtime.builtin.IRubyObject;

import java.io.PipedInputStream;
import java.io.PrintStream;
import java.io.OutputStream;
import java.io.IOException;
import java.util.ArrayList;

import com.jbidwatcher.util.config.ErrorManagement;

/**
 * User: Morgan
 * Date: Dec 16, 2007
 * Time: 6:53:02 PM
 *
 * Scripting interface so things can call Ruby methods easily.
 */
public class Scripting {
  private static Ruby sRuby = null;
  private static FauxOutputStream mOutput = new FauxOutputStream();
  private static IRubyObject sJBidwatcher = null;

  private Scripting() { }

  public static Ruby getRuntime() { return sRuby; }
  public static void setOutput(OutputStream stream) { mOutput.setOutput(stream); }

  public static void initialize() {
    final PipedInputStream pipeIn = new PipedInputStream();
    final RubyInstanceConfig config = new RubyInstanceConfig() {
      {
        setInput(pipeIn);
        setOutput(new PrintStream(mOutput));
        setError(new PrintStream(mOutput));
        setObjectSpaceEnabled(false);
      }
    };
    final Ruby runtime = Ruby.newInstance(config);

    String[] args = new String[0];
    IRubyObject argumentArray = runtime.newArrayNoCopy(JavaUtil.convertJavaArrayToRuby(runtime, args));
    runtime.defineGlobalConstant("ARGV", argumentArray);
    runtime.getGlobalVariables().defineReadonly("$*", new ValueAccessor(argumentArray));
    runtime.getGlobalVariables().defineReadonly("$$", new ValueAccessor(runtime.newFixnum(System.identityHashCode(runtime))));
    runtime.getLoadService().init(new ArrayList());

    runtime.evalScriptlet("require 'builtin/javasupport.rb'; require 'jbidwatcher/utilities';");

    sRuby = runtime;
  }

  private static class FauxOutputStream extends OutputStream {
    private OutputStream mOut = System.out;

    public void write(int b) throws IOException { mOut.write(b); }

    public OutputStream setOutput(OutputStream newOutput) {
      OutputStream old = mOut;
      mOut = newOutput;
      return old;
    }
  }

  public static Object ruby(String command) {
//    ErrorManagement.logDebug("Executing: " + command);

    if(sRuby != null) {
      OutputStream old = mOutput.setOutput(System.out);
      Object rval = sRuby.evalScriptlet(command);
      mOutput.setOutput(old);
      return rval;
    } else {
      ErrorManagement.logDebug("Calling ruby script with '" + command + "' before scripting enabled!");
      return null;
    }
  }

  public static Object rubyMethod(String method, Object... method_params) {
//    ErrorManagement.logDebug("Executing: " + method + " with (" + StringTools.comma(method_params) + ")");

    if (sRuby == null) {
      ErrorManagement.logDebug("Calling ruby method '" + method + "' before scripting enabled!");
      return null;
    }

    if (sJBidwatcher == null) {
      sJBidwatcher = (IRubyObject)ruby("JBidwatcher");
    }

    OutputStream old = mOutput.setOutput(System.out);
    Object rval = JavaEmbedUtils.invokeMethod(sRuby, sJBidwatcher, method, method_params, Object.class);
    mOutput.setOutput(old);
    return rval;
  }
}
