package com.jbidwatcher.scripting;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.jbidwatcher.util.config.JConfig;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;

/**
* Created by mschweers on 8/1/14.
*/
@Singleton
public class JRubyPreloader implements Runnable {
  private final Object syncObject;

  @Inject
  public JRubyPreloader(Object scriptSync) { syncObject = scriptSync; }

  public void run() {
    synchronized(syncObject) {
      try {
        preloadLibrary();
        Scripting.initialize();
        JConfig.enableScripting();
        JConfig.log().logMessage("Scripting is enabled.");
      } catch (NoClassDefFoundError ncdfe) {
        JConfig.log().logMessage("Scripting is not enabled.");
      } catch (Throwable e) {
        JConfig.log().logMessage("Error setting up scripting: " + e.toString());
        JConfig.disableScripting();
      }
    }
  }

  private void preloadLibrary() {
    String jrubyFile = JConfig.queryConfiguration("platform.path") + File.separator + "jruby-incomplete.jar";
    File fp = new File(jrubyFile);

    if (fp.exists()) {
      try {
        URL srcJar = fp.toURI().toURL();
        URLClassLoader myCL;
        try {
          ClassLoader cl = Thread.currentThread().getContextClassLoader();
          if(cl instanceof URLClassLoader) {
            myCL = (URLClassLoader) cl;
          } else {
            myCL = (URLClassLoader) cl.getParent();
          }
        } catch(ClassCastException cce) {
          throw new RuntimeException("Can't locate a valid class loader to bring in the scripting library.");
        }
        Class sysClass = URLClassLoader.class;
        Method sysMethod = sysClass.getDeclaredMethod("addURL", new Class[]{URL.class});
        sysMethod.setAccessible(true);
        sysMethod.invoke(myCL, srcJar);
      } catch (NoSuchMethodException ignored) {
      } catch (MalformedURLException ignored) {
      } catch (InvocationTargetException ignored) {
      } catch (IllegalAccessException ignored) {
        //  All these possible failures are ignored, it just means the scripting class won't be loaded.
      }
    }
  }
}
