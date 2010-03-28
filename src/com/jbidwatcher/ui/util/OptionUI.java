package com.jbidwatcher.ui.util;
/*
 * Copyright (c) 2000-2007, CyberFOX Software, Inc. All Rights Reserved.
 *
 * Developed by mrs (Morgan Schweers)
 */

import com.jbidwatcher.util.config.JConfig;

import java.awt.event.*;
import java.awt.*;
import javax.swing.*;
import javax.swing.text.JTextComponent;
import java.util.List;

public class OptionUI {
  /**
   * @brief Add the ability to past into a text field using a few different methods.
   *
   * @param jtc - The text field to be able to past into.
   * @param preFill - The initial text of the field.
   */
  private void addPasting(JTextField jtc, String preFill) {
    jtc.addMouseListener(JPasteListener.getInstance());
    jtc.setText(preFill);

    ActionListener doDefault = new ActionListener() {
        public void actionPerformed(ActionEvent ae) {
          if(ae.getActionCommand().equals("Escape")) {
            if(ae.getSource() instanceof JTextField) {
              JTextField clearMe = (JTextField)ae.getSource();
              clearMe.setText("");
            }
          }
          JComponent source = (JComponent)ae.getSource();
          JButton defButton = source.getRootPane().getDefaultButton();

          if(defButton != null) {
            defButton.doClick();
          }
        }
      };

    jtc.addActionListener(doDefault);
    jtc.registerKeyboardAction(doDefault, "Escape", KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0, false),
                               JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
  }

  private boolean handleDialogResult(JOptionPane jopPrompt, String endResult) {
    Integer buttonChoiceObject;
    Object result;
    int whatButton;

    result = jopPrompt.getValue();

    if(endResult == null || endResult.equals("") ||
       result == null || result.equals(JOptionPane.UNINITIALIZED_VALUE)) {
      buttonChoiceObject = JOptionPane.CANCEL_OPTION;
    } else {
      if(result.getClass() == String.class && result.equals("")) {
        buttonChoiceObject = JOptionPane.OK_OPTION;
      } else {
        buttonChoiceObject = new Integer(result.toString());
      }
    }

    boolean is_cancelled = false;
    //  Did they click cancel?
    whatButton = buttonChoiceObject;
    if(whatButton == JOptionPane.CANCEL_OPTION) {
      is_cancelled = true;
    }
    return(is_cancelled);
  }

  public String promptString(Component parent, String prePrompt, String preTitle) {
    return(promptString(parent, prePrompt, preTitle, ""));
  }

  public String promptString(Component parent, String prePrompt, String preTitle, String preFill) {
    String[] result = promptString(parent, prePrompt, preTitle, preFill, null, null);

    if(result == null) return null;

    return result[0];
  }

  public String[] promptString(Component parent, String prePrompt, String preTitle, String preFill, String postPrompt, String postFill) {
    final Object[] myComponents;

    if(postPrompt != null) {
      myComponents = new Object[4];
      myComponents[2] = "Quantity";
      myComponents[3] = new JTextField();
    } else {
      myComponents = new Object[2];
    }
    myComponents[0] = prePrompt;
    myComponents[1] = new JTextField();

    JOptionPane jopPrompt = new JOptionPane(myComponents, JOptionPane.PLAIN_MESSAGE, JOptionPane.OK_CANCEL_OPTION);

    addPasting((JTextField)myComponents[1], preFill);

    if(postPrompt != null) {
      addPasting((JTextField)myComponents[3], postFill);
    }

    JDialog jdInput = jopPrompt.createDialog(parent, preTitle);
    jdInput.addComponentListener(new ComponentAdapter() {
      public void componentShown(ComponentEvent event) {
        super.componentShown(event);
        ((JTextField) myComponents[1]).requestFocus();
      }
    });
    jdInput.addWindowListener(new WindowAdapter() {
        public void windowActivated(WindowEvent ev) {
          super.windowActivated(ev);
          ((JTextField) myComponents[1]).requestFocus();
        }
        public void windowDeactivated(WindowEvent ev) {
          super.windowDeactivated(ev);
          ev.getWindow().toFront();
        }
      });
    Rectangle rec = OptionUI.findCenterBounds(jdInput.getPreferredSize());
    jdInput.setLocation(rec.x, rec.y);

    ((JTextField) myComponents[1]).requestFocus();
    jdInput.setVisible(true);

    //    endResult = (String)jopPrompt.getInputValue();
    String endResult = ((JTextComponent) myComponents[1]).getText();

    boolean is_cancelled = handleDialogResult(jopPrompt, endResult);
    if(is_cancelled) return null;

    String[] results = new String[2];
    results[0] = endResult;
    if(postPrompt != null) {
      results[1] = ((JTextComponent) myComponents[3]).getText();
    }
    return results;
  }

  /**
   * @brief Get a basic editor pane for text/html, that listens for
   * hyperlinks properly, and chains to the Hyperactive module.
   *
   * @param sb - The StringBuffer to fill in as the text.
   * @param inSize - The preferred size for the editor.
   * @param fixed - Whether it's fixed in position or not.
   * @param html - True if this is HTML data.
   *
   * @return - A JBEditorPane to be embedded in a frame.
   */
  public JBEditorPane getBasicEditor(StringBuffer sb, Dimension inSize, boolean fixed, boolean html) {
    JBEditorPane jep;

    jep = new JBEditorPane(html ? "text/html" : "text/plain", sb.toString());
    jep.setEditable(false);
    jep.addHyperlinkListener(new Hyperactive(jep));

    if(fixed) {
      jep.setPreferredSize(inSize);
      jep.setMaximumSize(inSize);
      jep.setMinimumSize(inSize);
    }

    return jep;
  }

  /**
   * @brief Get a basic editor pane for text/html, that listens for
   * hyperlinks properly, and chains to the Hyperactive module.
   *
   * @param s - The String to fill in as the text.
   *
   * @return - A JBEditorPane to be embedded in a frame.
   */
  public static JBEditorPane getHTMLLabel(String s) {
    JBEditorPane jep;

    jep = new JBEditorPane("text/html", s);
    jep.setEditable(false);
    jep.setOpaque(false);
    jep.addHyperlinkListener(new Hyperactive(jep));

    return jep;
  }

  /**
   * @brief Get the upper left point of a box which would be centered
   * exactly, given the provided dimensions.
   *
   * @param inSize - The dimensions of the rectangle to place.
   *
   * @return - The upper left corner to place a window at.
   */
  public Point getCenter(Dimension inSize) {
    Rectangle centerBounds = findCenterBounds(inSize);
    Point screenCenter;
    screenCenter = new Point( (centerBounds.width / 2) + centerBounds.x, (centerBounds.height / 2)+centerBounds.y);
    screenCenter.x -= inSize.width/2;
    screenCenter.y -= inSize.height/2;
    return screenCenter;
  }

  /**
   * Helps client code place components on the center of the screen.  It
   * handles multiple monitor configuration correctly
   *
   * @param componentSize the size of the component
   * @return bounds of the centered component
   * @since 2.5
   */
  public static Rectangle findCenterBounds(Dimension componentSize) {
    return findCenterBounds(JMouseAdapter.getCurrentGraphicsConfiguration(), componentSize);
  }

  /**
   * Helps client code place components on the center of the screen.  It
   * handles multiple monitor configuration correctly
   *
   * @param gconf         the GraphicsConfiguration of the monitor
   * @param componentSize the size of the component
   * @return bounds of the centered component
   */
  private static Rectangle findCenterBounds(GraphicsConfiguration gconf, Dimension componentSize) {
    if (gconf == null) {
      gconf = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice().getDefaultConfiguration();
    }

    Rectangle bounds = gconf.getBounds();

    return new Rectangle(
        bounds.x + ((bounds.width - componentSize.width) / 2),
        bounds.y + ((bounds.height - componentSize.height) / 2), componentSize.width, componentSize.height
    );
  }

  public JFrame showTextDisplay(StringBuffer inSB, Dimension inSize, String frameName) {
    return showTextDisplay(inSB, inSize, frameName, true);
  }

  /**
   * @brief Show a big HTML-formatted text display.
   *
   * @param inSB - The data to show.
   * @param inSize - The size to show it at.
   * @param frameName - The name of the frame to show.
   * @param isHTML - Is the StringBuffer
   *
   * @return - The JFrame of the display.
   */
  public JFrame showTextDisplay(StringBuffer inSB, Dimension inSize, String frameName, boolean isHTML) {
    JFrame otherFrame = getTextDisplay(inSB, inSize, frameName, isHTML);
    otherFrame.pack();
    otherFrame.setSize(inSize.width, inSize.height);
    otherFrame.setVisible(true);

    return otherFrame;
  }

  public JFrame getTextDisplay(StringBuffer inSB, Dimension inSize, String frameName, boolean isHTML) {
    JFrame otherFrame;
    JBEditorPane jep;
    JScrollPane jsp;

    jep = getBasicEditor(inSB, inSize, false, isHTML);

    otherFrame = new JBidFrame(frameName);

    jsp = new JScrollPane(jep);
    jsp.getVerticalScrollBar().setValue(0);
    otherFrame.getContentPane().add(jsp);
    jep.setCaretPosition(0);
    otherFrame.setLocation(getCenter(inSize));
    return otherFrame;
  }

  public JFrame showTextDisplayWithButtons(StringBuffer inSB, Dimension inSize, String frameName, final String config, final String buttonText1, final String value1, final String buttonText2, final String value2) {
    final JBEditorPane jep = getBasicEditor(inSB, inSize, false, true);
    final JFrame otherFrame = new JBidFrame(frameName);
    final JScrollPane jsp;

    otherFrame.getContentPane().setLayout(new BorderLayout());
    JPanel buttonPanel = new JPanel(new BorderLayout());
    JButton button1 = new JButton(buttonText1);
    button1.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        if(e.getActionCommand().equals(buttonText1)) {
          JConfig.setConfiguration(config, value1);
          otherFrame.setVisible(false);
        }
      }
    });

    JButton button2 = new JButton(buttonText2);
    button2.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        if(e.getActionCommand().equals(buttonText2)) {
          JConfig.setConfiguration(config, value2);
          otherFrame.setVisible(false);
        }
      }
    });

    buttonPanel.add(button1, BorderLayout.WEST);
    buttonPanel.add(button2, BorderLayout.EAST);
    otherFrame.getContentPane().add(buttonPanel, BorderLayout.SOUTH);

    jsp = new JScrollPane(jep);
    jsp.getVerticalScrollBar().setValue(0);
    otherFrame.getContentPane().add(jsp, BorderLayout.CENTER);
    jep.setCaretPosition(0);
    otherFrame.setLocation(getCenter(inSize));
    otherFrame.pack();
    otherFrame.setSize(inSize.width, inSize.height);
    otherFrame.setVisible(true);

    return otherFrame;
  }

  /**
   * @brief Show a large HTML-formatted text display, with buttons
   * below, to select what to do.
   *
   * @param inSB - The data to show in the buffer.
   * @param inSize - The size to show it at.
   * @param frameName - The name of the frame to show.
   * @param choices - The array of choices to show.
   * @param borderTitle - The title to surround the panel with.
   * @param al - Who to notify that a choice was made.
   *
   * @return - The JFrame of the display.
   */
  public JFrame showChoiceTextDisplay(StringBuffer inSB, Dimension inSize, String frameName, List<String> choices, String borderTitle, ActionListener al) {
    JBEditorPane jep = getBasicEditor(inSB, inSize, true, true);

    JFrame otherFrame = new JBidFrame(frameName);

    JPanel insidePanel = new JPanel(new BorderLayout());

    JScrollPane jsp = new JScrollPane(jep);
    jsp.getVerticalScrollBar().setValue(0);
    otherFrame.setPreferredSize(inSize);
    otherFrame.setMaximumSize(inSize);
    otherFrame.setMinimumSize(inSize);
    otherFrame.getContentPane().add(insidePanel);
    insidePanel.add(jsp, BorderLayout.CENTER);
    if(borderTitle != null) {
      insidePanel.setBorder(BorderFactory.createTitledBorder(borderTitle));
    } else {
      insidePanel.setBorder(BorderFactory.createEmptyBorder());
    }
    JPanel bottomPanel = new JPanel();
    bottomPanel.setLayout(new BoxLayout(bottomPanel, BoxLayout.X_AXIS));

    boolean isFirst = true;
    JButton firstButton = null;
    for (String info : choices) {
      if (info.startsWith("CHECK")) {
        JCheckBox tmpCheck = new JCheckBox(info.substring(6));
        tmpCheck.addActionListener(al);
        bottomPanel.add(tmpCheck);
      } else if (info.startsWith("TEXT")) {
        JLabel tmpLabel = new JLabel(info.substring(5));
        bottomPanel.add(tmpLabel);
      } else {
        JButton step_button = new JButton(info);
        step_button.addActionListener(al);
        bottomPanel.add(step_button);
        if (isFirst) {
          isFirst = false;
          firstButton = step_button;
        }
      }
    }
    insidePanel.add(bottomPanel, BorderLayout.SOUTH);
    jep.setCaretPosition(0);
    otherFrame.setLocation(getCenter(inSize));
    otherFrame.pack();
    otherFrame.setVisible(true);

    if(firstButton != null) firstButton.requestFocusInWindow();

    return otherFrame;
  }

  public int promptWithCheckbox(Component parent, String message, String title, String tf_config, int optionType, int defaultOption) {
    return promptWithCheckbox(parent, message, title, tf_config, null, optionType, defaultOption);
  }

  public int promptWithCheckbox(Component parent, String message, String title, String tf_config) {
    return promptWithCheckbox(parent, message, title, tf_config, null, JOptionPane.OK_CANCEL_OPTION, JOptionPane.OK_OPTION);
  }

  /**
   * This function puts up a message, allows buttons to be clicked, and a
   * checkbox to be set (most often for "don't ever show me this again."),
   * and it will set the configuration appropriately.
   *
   * @param parent - The higher level component to become a child of; this is to attempt to handle modal blocking dialog issues.
   * @param message - The message to display.
   * @param title - The window title.
   * @param tf_config - The configuration value that will be set to true if
   *                    the checkbox is selected, or false otherwise.
   * @param val_config - the configuration value that will be set to the
   *                     result of their choice (OK or Cancel, for
   *                     instance).
   * @param optionType - The prompt type of the dialog box, for instance JOptionPane.OK_CANCEL_OPTION.
   * @param defaultOption - The default option to be returned if the button
   *                        to not show this dialog was clicked.  This only
   *                        applies if val_config is null.
   *
   * @return - The button value selected, usually of
   *           JOptionPane.CANCEL_OPTION or OK_OPTION, or the val_config
   *           stored value, or the defaultOption.
   */
  public int promptWithCheckbox(Component parent, String message, String title, String tf_config, String val_config, int optionType, int defaultOption) {
    Integer buttonChoiceObject;
    Object[] myComponents;
    JCheckBox dontShowBox;
    JOptionPane jopPrompt;
    JDialog jdInput;
    Object result;

    //  If we were marked in the past as 'don't show this box'...
    if(JConfig.queryConfiguration(tf_config, "false").equals("true")) {
      if(val_config != null) {
        String cfg_val = JConfig.queryConfiguration(val_config, null);

        if(cfg_val != null) {
          return Integer.parseInt(cfg_val);
        }
      }
      return defaultOption;
    }

    dontShowBox = new JCheckBox("Don't show this dialog again.");

    myComponents = new Object[2];
    myComponents[0] = message;
    myComponents[1] = dontShowBox;

    jopPrompt = new JOptionPane(myComponents, JOptionPane.QUESTION_MESSAGE, optionType);

    jdInput = jopPrompt.createDialog(parent, title);
    jdInput.addWindowListener(new WindowAdapter() {
        public void windowDeactivated(WindowEvent ev) {
          ev.getWindow().toFront();
        }
      });
    jdInput.setVisible(true);

    result = jopPrompt.getValue();

    if(result == null || result.toString().equals("") || result.equals(JOptionPane.UNINITIALIZED_VALUE)) {
      buttonChoiceObject = JOptionPane.CANCEL_OPTION;
    } else {
      buttonChoiceObject = new Integer(result.toString());
    }

    if(tf_config != null) {
      JConfig.setConfiguration(tf_config, dontShowBox.isSelected() ? "true" : "false");
    }
    if(val_config != null) {
      JConfig.setConfiguration(val_config, buttonChoiceObject.toString());
    }

    return buttonChoiceObject;
  }
}
