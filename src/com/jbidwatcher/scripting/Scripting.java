package com.jbidwatcher.scripting;

import com.jbidwatcher.util.config.JConfig;
import org.jruby.RubyInstanceConfig;
import org.jruby.Ruby;
import org.jruby.internal.runtime.GlobalVariable;
import org.jruby.internal.runtime.ValueAccessor;
import org.jruby.javasupport.JavaEmbedUtils;
import org.jruby.javasupport.JavaUtil;

import java.io.*;
import java.util.HashMap;
import java.util.Map;

/**
 * User: Morgan
 * Date: Dec 16, 2007
 * Time: 6:53:02 PM
 *
 * Scripting interface so things can call Ruby methods easily.
 */
public class Scripting {
  private static Object sRuby = null;
  private static FauxOutputStream sOutput;
  private static FauxInputStream sInput;

  private static class FauxOutputStream extends OutputStream {
    private OutputStream mOut = System.out;

    public void write(int b) throws IOException { mOut.write(b); }
    public void write(byte[] bs) throws IOException { mOut.write(bs); }
    public void write(byte[] bs, int offset, int length) throws IOException { mOut.write(bs, offset, length); }

    public OutputStream setOutput(OutputStream newOutput) {
      OutputStream old = mOut;
      mOut = newOutput;
      return old;
    }
  }

  private static class FauxInputStream extends InputStream {
    private InputStream mIn = System.in;

    public int read() throws IOException {
      return mIn.read();
    }

    public InputStream setInput(InputStream newInput) {
      InputStream old = mIn;
      mIn = newInput;
      return old;
    }
  }

  private Scripting() { }

  public static Ruby getRuntime() { return (Ruby)sRuby; }

  public static void setOutput(OutputStream stream) { sOutput.setOutput(stream); }
  public static void setInput(InputStream stream) { sInput.setInput(stream); }

  public static void initialize() throws ClassNotFoundException {
    //  Test for JRuby's presence
    Class.forName("org.jruby.RubyInstanceConfig", true, Thread.currentThread().getContextClassLoader());

    sOutput = new FauxOutputStream();
    sInput = new FauxInputStream();
    final RubyInstanceConfig config = new RubyInstanceConfig() {
      {
        String[] args = new String[3];
        args[0] = "--readline";
        args[1] = "--prompt-mode";
        args[2] = "default";
        setInput(sInput);
        setOutput(new PrintStream(sOutput));
        setError(new PrintStream(sOutput));
        setArgv(args);
      }
    };

    final Ruby runtime = Ruby.newInstance(config);

    runtime.getGlobalVariables().defineReadonly("$$", new ValueAccessor(runtime.newFixnum(System.identityHashCode(runtime))), GlobalVariable.Scope.GLOBAL);

    if(JConfig.queryConfiguration("platform.path") != null) {
      runtime.getLoadService().addPaths(JConfig.queryConfiguration("platform.path"));
    }

    runtime.getLoadService().addPaths("lib/jbidwatcher", "lib/jbidwatcher/nokogiri-1.5.2-java/lib");

    sRuby = runtime;
  }

  public static void require(String file) {
    getRuntime().evalScriptlet("require '" + file + "';");
  }

  public static void setGlobalVariable(String variable, Object value) {
    getRuntime().getGlobalVariables().defineReadonly(variable, new ValueAccessor(JavaUtil.convertJavaToRuby(getRuntime(), value)), GlobalVariable.Scope.GLOBAL);
  }

  public static Object ruby(String command) {
    if(sRuby != null) {
      return ((Ruby)sRuby).evalScriptlet(command);
    } else {
      return null;
    }
  }

  public static Object rubyMethod(String method, Object... method_params) {
    return doRuby("JBidwatcher", method, method_params);
  }

  private static Map<String, Object> expressionCache = new HashMap<String, Object>();

  /**
   * Forget a cached expression result for doRuby's use.
   *
   * @param what The expression to un-cache.
   */
  public static void forget(String what) {
    expressionCache.remove(what);
  }

  /**
   * Execute a ruby method on an arbitrary Ruby object.  The ruby expression
   * to call the method on is cached, so later calls to execute methods on the
   * same object won't have to look up the object first.  If you need to clear
   * the cache, @see Scripting.forget.
   *
   * @param on An expression that results in a Ruby object.
   * @param method The method name to call on the Ruby object.
   * @param method_params Any parameters to pass to the method.
   *
   * @return Whatever return value the method returns.
   */
  public static Object doRuby(String on, String method, Object... method_params) {
    if (sRuby == null) return null;
    Object actOn = expressionCache.get(on);
    if(actOn == null) {
      actOn = ruby(on);
      if(actOn != null) {
        expressionCache.put(on, actOn);
      }
    }
    if(actOn != null) {
      return JavaEmbedUtils.invokeMethod((Ruby)sRuby, actOn, method, method_params, Object.class);
    }
    return null;
  }
}
