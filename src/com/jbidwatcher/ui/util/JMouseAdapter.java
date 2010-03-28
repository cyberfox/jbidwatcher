package com.jbidwatcher.ui.util;
/*
 * Copyright (c) 2000-2007, CyberFOX Software, Inc. All Rights Reserved.
 *
 * Developed by mrs (Morgan Schweers)
 */

//
//  History:
//  mrs: 24-July-1999 12:26 - Renamed class.
//  mrs: 15-July-1999 01:14 - Removed printouts unless debugging...

import com.jbidwatcher.util.config.JConfig;

import java.awt.*;
import java.awt.event.MouseEvent;
import javax.swing.event.MouseInputAdapter;
import java.awt.event.ActionListener;
import javax.swing.*;

public class JMouseAdapter extends MouseInputAdapter implements ActionListener {
  protected JPopupMenu localPopup;
  private int x = 0;
  private int y = 0;

  public JMouseAdapter(JPopupMenu showPopup) {
    localPopup = showPopup;
  }

  public JMouseAdapter() {
    localPopup = new JPopupMenu();
  }

  public int getPopupX() { return x; }
  public int getPopupY() { return y; }

  private void evaluatePopup(MouseEvent e) {
    if(e.isPopupTrigger()){
      if(localPopup != null) {
        x = e.getX();
        y = e.getY();
        beforePopup(localPopup, e);
        internalPopupMenu(e);
        afterPopup(localPopup, e);
      }
    }
  }

  public void mouseReleased(MouseEvent event) {
    super.mouseReleased(event);
    evaluatePopup(event);
  }

  public void mousePressed(MouseEvent event) {
    super.mousePressed(event);
    evaluatePopup(event);
  }

  public void mouseClicked(MouseEvent e) {
    super.mouseClicked(e);
    //  Right click should call up a popup menu...
    if(e.isPopupTrigger()) {
      evaluatePopup(e);
    } else {
      //  Double click should do *something*...
      if(e.getClickCount() == 2) {
        beforePopup(localPopup, e);
        internalDoubleClick(e);
      }
    }
  }

  // helper function to move a rectangle onto the screen
  private static Rectangle ensureRectIsVisible(Rectangle bounds) {
    Rectangle screen = getUsableScreenBounds();
    return new Rectangle(Math.max(screen.x, Math.min( (screen.width + screen.x) - bounds.width, bounds.x)),
        Math.max(screen.y, Math.min((screen.height + screen.y) - bounds.height, bounds.y)),
        bounds.width, bounds.height);
  }

  //  The following two functions are stubs, generally to be
  //  overridden if you want to do anything particularly useful.
  protected void beforePopup(JPopupMenu inPopup, MouseEvent e) { }
  @SuppressWarnings({"UnusedDeclaration", "UnusedDeclaration"})
  protected void afterPopup(JPopupMenu inPopup, MouseEvent e) { }

  private void internalPopupMenu(MouseEvent e) {
    Component activeComp = (Component)e.getSource();

    localPopup.show(activeComp, x, y);

    // determine boundaries
    Point point = localPopup.getLocationOnScreen();
    Dimension size = localPopup.getSize();
    Rectangle oldRect = new Rectangle(point.x, point.y, size.width, size.height);

    // helper function to move oldRect completely
    // onto screen (desktop) if necessary
    Rectangle newRect = ensureRectIsVisible(oldRect);

    // rects differ, need moving
    if(!oldRect.equals(newRect)) {
//      Window window = SwingUtilities.getWindowAncestor(localPopup);
//      if(window != null){
//        window.setLocation(newRect.x, newRect.y);
//      }
      localPopup.setLocation(newRect.x, newRect.y);
    }
  }

  protected void internalDoubleClick(MouseEvent e) {
  }

  public void actionPerformed(java.awt.event.ActionEvent ae) {
  }

  /**
   * Finds out the monitor where the user currently has the input focus.
   * This method is usually used to help the client code to figure out on
   * which monitor it should place newly created windows/frames/dialogs.
   *
   * @return the GraphicsConfiguration of the monitor which currently has the
   *         input focus
   */
  public static GraphicsConfiguration getCurrentGraphicsConfiguration() {
    Component focusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
    if (focusOwner != null) {
      Window w = SwingUtilities.getWindowAncestor(focusOwner);
      if (w != null) {
        return w.getGraphicsConfiguration();
      }
    }

    return GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice().getDefaultConfiguration();
  }

  /**
   * Returns the usable area of the screen where applications can place its
   * windows.  The method subtracts from the screen the area of taskbars,
   * system menus and the like.  The screen this method applies to is the one
   * which is considered current, ussually the one where the current input
   * focus is.
   *
   * @return the rectangle of the screen where one can place windows
   * @since 2.5
   */
  public static Rectangle getUsableScreenBounds() {
    return getUsableScreenBounds(getCurrentGraphicsConfiguration());
  }

  /**
   * Returns the usable area of the screen where applications can place its
   * windows.  The method subtracts from the screen the area of taskbars,
   * system menus and the like.
   *
   * @param gconf the GraphicsConfiguration of the monitor
   * @return the rectangle of the screen where one can place windows
   * @since 2.5
   */
  public static Rectangle getUsableScreenBounds(GraphicsConfiguration gconf) {
    if (gconf == null) {
      gconf = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice().getDefaultConfiguration();
    }

    Rectangle bounds = new Rectangle(gconf.getBounds());

    try {
      Toolkit toolkit = Toolkit.getDefaultToolkit();
      Insets insets = toolkit.getScreenInsets(gconf);
      bounds.y += insets.top;
      bounds.x += insets.left;
      bounds.height -= (insets.top + insets.bottom);
      bounds.width -= (insets.left + insets.right);
    } catch (Exception ex) {
      JConfig.log().handleException("There was a problem getting screen-related information.", ex);
    }

    return bounds;
  }
}
