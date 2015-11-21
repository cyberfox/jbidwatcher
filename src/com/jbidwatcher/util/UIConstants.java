package com.jbidwatcher.util;

import com.jbidwatcher.util.config.JConfig;

import javax.swing.*;

/**
 * Created by Morgan Schweers (cyberfox) on 11/20/15.
 *
 * Hold constants in a central place, so that other parts of the application can rely on them without importing the UI code directly
 * and violating the nested architecture.
 */
public class UIConstants {
  public static final ImageIcon redStatus = new ImageIcon(JConfig.getResource("/icons/status_red.png"));
  public static final ImageIcon redStatus16 = new ImageIcon(JConfig.getResource("/icons/status_red_16.png"));
  public static final ImageIcon greenStatus = new ImageIcon(JConfig.getResource("/icons/status_green.png"));
  public static final ImageIcon greenStatus16 = new ImageIcon(JConfig.getResource("/icons/status_green_16.png"));
  public static final ImageIcon yellowStatus = new ImageIcon(JConfig.getResource("/icons/status_yellow.png"));
  public static final ImageIcon yellowStatus16 = new ImageIcon(JConfig.getResource("/icons/status_yellow_16.png"));

  public final static String QUIT_MSG = "QUIT"; // Shut down the program.
  public final static String HIDE_MSG = "HIDE";
  public final static String RESTORE_MSG = "RESTORE";
  public final static String VISIBILITY_MSG = "VISIBILITY";
  public final static String NEWVERSION_MSG = "NEWVERSION"; // Show an announcement about the new version!
  public final static String NO_NEWVERSION_MSG = "NO_NEWVERSION"; // Note that no new version is ready.
  public final static String BAD_NEWVERSION_MSG = "BAD_NEWVERSION";
  public final static String VALID_LOGIN_MSG = "VALID_LOGIN";
  public final static String START_UPDATING = "ALLOW_UPDATES";
  public final static String SMALL_USERINFO = "TOGGLE_SMALL";
  public final static String SNIPE_ALTERED_MSG = "SNIPECHANGED";
  public final static String TOOLBAR_MSG = "TOOLBAR";
  public final static String HEADER_MSG = "HEADER"; // Draw text on the header (site time)
  public final static String LOGIN_STATUS_MSG = "LOGINSTATUS";
  public final static String LINK_MSG = "LINK"; // Identify whether the link is up/down
  public final static String ERROR_MSG = "ERROR"; // Show an error message.
  public final static String ALERT_MSG = "ALERT";
  public final static String NOTIFY_MSG = "NOTIFY";
  public final static String IGNORABLE_MSG = "IGNORE";
  public final static String INVALID_LOGIN_MSG = "INVALID_LOGIN";
  public final static String NOACCOUNT_MSG = "NOACCOUNT";
  public final static String PRICE = "PRICE";
  public final static String DEVICE_REGISTRATION = "SECURITY";
}
