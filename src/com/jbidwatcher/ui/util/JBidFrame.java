package com.jbidwatcher.ui.util;

import com.cyberfox.util.platform.Platform;

import javax.swing.*;

/**
 * Created by IntelliJ IDEA.
 * User: mrs
 * Date: Mar 6, 2008
 * Time: 3:30:20 PM
 * 
 * Provide a basic JFrame descendant that automatically sets its menu bar on the mac.
 */
public class JBidFrame extends JFrame
{
  private static JMenuBar sMenuBar = null;
  private JEditorPane mEditorPane = null;

  public JEditorPane getEditor() {
    return mEditorPane;
  }

  public void setEditor(JEditorPane editor) {
    mEditorPane = editor;
  }

  public static void setDefaultMenuBar(JMenuBar def) {
    sMenuBar = def;
  }

  public static JMenuBar getDefaultMenuBar() {
    return sMenuBar;
  }

  /**
   * @brief In order to support the everpresent top-of-screen menu bar
   * on the Mac, we need this assistance function to add a menu bar to
   * any frames we create.
   *
   * @param frameName - The name of this frame, to which we will add the standard menu bar to.
   */
  public JBidFrame(String frameName) {
    super(frameName);
    if(Platform.isMac()) {
      setJMenuBar(sMenuBar);
    }
  }
}
