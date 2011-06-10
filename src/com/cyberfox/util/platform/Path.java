package com.cyberfox.util.platform;

import com.cyberfox.util.config.JConfig;

import java.io.File;

/**
 * Created by IntelliJ IDEA.
 * User: mrs
 * Date: Jan 30, 2010
 * Time: 6:45:49 PM
 *
 * Platform-specific path operations
 */
@SuppressWarnings({"UtilityClass"})
public class Path {
  private static String sHomeDirectory = null;

  private Path() {}

  public static void setHome(String newHome) {
    sHomeDirectory = newHome;
  }

  public static String getHome() {
    if (sHomeDirectory == null) {
      return System.getProperty("user.home");
    }
    return sHomeDirectory;
  }

  public static void setHomeDirectory(String dirname) {
    JConfig.setHomeDirectory(getHomeDirectory(dirname));
  }

  /**
   * @param dirname - The directory to add to the app-specific location.
   * @return - A String containing the OS-specific place to put our files.
   * @brief Gets a path to the 'optimal' place to put application-specific files.
   */
  public static String getHomeDirectory(String dirname) {
    String sep = System.getProperty("file.separator");
    String homePath;

    if (Platform.isRawMac()) {
      homePath = getMacHomeDirectory(dirname);
    } else {
      homePath = getHome() + sep + '.' + dirname;
    }

    File fp = new File(homePath);
    if (!fp.exists()) fp.mkdirs();

    return homePath;
  }

  public static String getMacHomeDirectory(String dirname) {
    String sep = System.getProperty("file.separator");
    if (dirname.equals("jbidwatcher")) dirname = "JBidwatcher";
    return getHome() + sep + "Library" + sep + "Preferences" + sep + dirname;
  }

  /**
   * @param fname     - The file name to hunt for.
   * @param dirname   - The ending directory for this application.
   * @param mustExist - false if we just want to find out the best place to put it.
   * @return - A string containing the 'best' version of a given file.
   * @brief Find the 'best' location for a file.
   * <p/>
   * If the file has a path, presume it's correct.
   * If it's just a filename, try to find it at the users (application) home directory.
   * If it's not there, just load it from the current directory.
   */
  public static String getCanonicalFile(String fname, String dirname, boolean mustExist) {
    String outName = fname;
    String pathSeparator = System.getProperty("file.separator");

    //  Is it a path?  If so, we don't want to override it!
    if (fname.indexOf(pathSeparator) == -1) {
      String configPathFile = getHomeDirectory(dirname) + pathSeparator + fname;

      if (mustExist) {
        File centralConfig = new File(configPathFile);

        if (centralConfig.exists() && centralConfig.isFile()) {
          outName = configPathFile;
        }
      } else {
        outName = configPathFile;
      }
    }

    return outName;
  }

  public static String makeStandardDirectory(String inPath, String defaultSubdir, String defaultDirectory) {
    String outPath = inPath;

    if(outPath != null) {
      File fp_test = new File(outPath);
      if(!fp_test.exists()) {
        if(!fp_test.mkdirs()) {
          outPath = null;
        }
      }
    }

    if(outPath == null) {
      String directoryPath = getCanonicalFile(defaultSubdir, defaultDirectory, false);
      File fp = new File(directoryPath);

      if(fp.exists()) {
        outPath = fp.getAbsolutePath();
      } else {
        if(!fp.mkdirs()) JConfig.log().logDebug("Couldn't mkdir " + directoryPath);
        outPath = fp.getAbsolutePath();
      }
    }

    return outPath;
  }
}
