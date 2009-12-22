package com.jbidwatcher.platform;

/**
 * Created by IntelliJ IDEA.
 * User: mrs
 * Date: Nov 21, 2009
 * Time: 3:05:38 PM
 *
 * Activates the Sparkle Framework
 */

public class Sparkle {
  /**
   * Native method declaration
   */
  public native static void initSparkle(String pathToSparkleFramework,
                                        boolean updateAtStartup,
                                        int checkInterval);

  /**
   * Whether updates are checked at startup
   */
  private boolean updateAtStartup = true;

  /**
   * Check interval period, in seconds
   */
  private int checkInterval = 86400;  // 1 day

  /**
   * Dynamically loads the JNI object. Will
   * fail if it is launched on an non-MacOSX system
   * or when libinit_sparkle.dylib is outside of the
   * LD_LIBRARY_PATH
   */
  static {
    try {
      System.loadLibrary("sparkle_init");
    } catch(UnsatisfiedLinkError ule) {
      //  Non-mac or pre-10.5 version
    }
  }

  /**
   * Initialize and start Sparkle
   *
   * @throws Exception - If anything goes wrong.
   */
  public void start() throws Exception {
    initSparkle(System.getProperty("user.dir")
        + "/../../Frameworks/Sparkle.framework",
        updateAtStartup, checkInterval);
  }
}