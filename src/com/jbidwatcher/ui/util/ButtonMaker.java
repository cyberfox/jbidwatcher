package com.jbidwatcher.ui.util;

import com.cyberfox.util.platform.Platform;
import com.jbidwatcher.util.config.JConfig;

import javax.swing.*;
import java.awt.event.ActionListener;
import java.awt.Dimension;
import java.net.URL;

/**
 * Created by IntelliJ IDEA.
 * User: Morgan
 * Date: Jun 20, 2008
 * Time: 10:17:49 AM
 *
 * Utility class to provide button-making tools.
 */
public class ButtonMaker {
  /**
   * @brief Add a toolbar button to the display, with a particular action, name, image, and tooltip.
   *
   * @param jtb - The toolbar to add to.
   * @param inAction - The ActionListener who will listen for actions on this button.
   * @param buttonName - The action name that will be sent to the action listener when the button is pressed.
   * @param buttonImage - The image to use for the button.
   * @param buttonTip - The tooltip to pop up for the button.
   */
  public static JButton addbutton(JToolBar jtb, ActionListener inAction, String buttonName, String buttonImage, String buttonTip) {
    final JButton newButton = makeButton(buttonImage, buttonTip, buttonName, inAction, false);

    if(Platform.isMac()) {
      newButton.setBorder(null);
      newButton.setBorderPainted(false);
      newButton.setContentAreaFilled(false);
      newButton.setRolloverEnabled(true);
      newButton.putClientProperty("Quaqua.Button.style", "toolBarRollover");
    }

    newButton.setFocusable(false);
    jtb.add(newButton);
    return newButton;
  }

  public static JButton makeButton(String buttonImage, String buttonTip, String buttonName, ActionListener inAction, boolean shrink) {
    JButton newButton = new JButton();
    URL iconRes = JConfig.getResource(buttonImage);
    ImageIcon newImage = new ImageIcon(iconRes);

    newButton.setIcon(newImage);
    if(shrink) {
      Dimension size = new Dimension(newImage.getIconWidth(), newImage.getIconHeight());
      newButton.setSize(size);
      newButton.setMaximumSize(size);
      newButton.setMinimumSize(size);
      newButton.setPreferredSize(size);
    }
    newButton.setToolTipText(buttonTip);
    newButton.setActionCommand("BT-" + buttonName);
    newButton.addActionListener(inAction);
    return newButton;
  }
}
