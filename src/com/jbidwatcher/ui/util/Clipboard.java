package com.jbidwatcher.ui.util;

import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;

/**
 * Created by IntelliJ IDEA.
 * User: Morgan
 * Date: Jun 20, 2008
 * Time: 11:07:14 AM
 *
 * Utility class for dealing with the system clipboard.
 */
public class Clipboard {
  public static void setClipboardString(String saveString) {
    java.awt.datatransfer.Clipboard sysClip = Toolkit.getDefaultToolkit().getSystemClipboard();
    StringSelection t = new StringSelection(saveString);

    sysClip.setContents(t, t);
  }
}
