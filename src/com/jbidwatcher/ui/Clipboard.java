package com.jbidwatcher.ui;

import com.jbidwatcher.util.config.JConfig;

import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.DataFlavor;
import java.util.Arrays;

/**
 * Created by IntelliJ IDEA.
 * User: Morgan
 * Date: Jun 20, 2008
 * Time: 11:07:14 AM
 *
 * Utility class for dealing with the system clipboard.
 */
public class Clipboard {
  private static JDropListener sJDL = new JDropListener(null); //  This would fail miserably if we called drop()...

  public static void setClipboardString(String saveString) {
    java.awt.datatransfer.Clipboard sysClip = Toolkit.getDefaultToolkit().getSystemClipboard();
    StringSelection t = new StringSelection(saveString);

    sysClip.setContents(t, t);
  }

  public static String getClipboardString() {
    java.awt.datatransfer.Clipboard sysClip = Toolkit.getDefaultToolkit().getSystemClipboard();
    Transferable t = sysClip.getContents(null);

    JConfig.log().logDebug("Clipboard: " + sysClip.getName() + ", valid flavors: " + Arrays.toString(t.getTransferDataFlavors()));

    StringBuffer stBuff = sJDL.getTransferData(t);
    String clipString;
    if (stBuff == null) {
      try {
        clipString = (String) t.getTransferData(DataFlavor.stringFlavor);
      } catch (Exception e) {
        //  Nothing really to do here...
        clipString = null;
      }
    } else {
      clipString = stBuff.toString();
    }

    return clipString;
  }
}
