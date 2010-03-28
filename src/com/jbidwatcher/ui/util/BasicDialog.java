package com.jbidwatcher.ui.util;

import javax.swing.*;
import java.awt.Frame;
import java.awt.GraphicsConfiguration;
import java.awt.BorderLayout;
import java.awt.event.*;

public abstract class BasicDialog extends JDialog {
  private boolean cancelled=false;
  private JPanel basicContentPane;
  private JButton buttonOK;
  private JButton buttonCancel;

  public BasicDialog(Frame frame, String s, boolean b, GraphicsConfiguration currentGraphicsConfiguration) {
    super(frame, s, b, currentGraphicsConfiguration);
    establishBasicUI();
  }

  public BasicDialog() {
    super();
    establishBasicUI();
  }

  private void establishBasicUI() {
    buttonOK = new JButton();
    buttonOK.setText("OK");

    buttonCancel = new JButton();
    buttonCancel.setText("Cancel");
  }

  protected void addBehavior() {
    setContentPane(getBasicContentPane());
    setLocationRelativeTo(null);
    getRootPane().setDefaultButton(getButtonOK());

    getButtonOK().addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        cancelled = false;
        onOK();
      }
    });

    getButtonCancel().addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        cancelled = true;
        onCancel();
      }
    });

    // call onCancel() when cross is clicked
    setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
    addWindowListener(new WindowAdapter() {
      public void windowClosing(WindowEvent e) {
        cancelled = true;
        onCancel();
      }
    });

    // call onCancel() on ESCAPE
    getBasicContentPane().registerKeyboardAction(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        cancelled = true;
        onCancel();
      }
    }, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
  }

  public boolean isCancelled() {
    return cancelled;
  }

  abstract protected void onOK();
  abstract protected void onCancel();

  protected JButton getButtonOK() {
    return buttonOK;
  }

  protected void setButtonOK(JButton ok) {
    buttonOK = ok;
  }

  protected JButton getButtonCancel() {
    return buttonCancel;
  }

  protected void setButtonCancel(JButton cancel) {
    buttonCancel = cancel;
  }

  public JPanel getBasicContentPane() {
    if(basicContentPane == null) basicContentPane = new JPanel(new BorderLayout());
    return basicContentPane;
  }

  public void setBasicContentPane(JPanel contentPane) {
    basicContentPane = contentPane;
  }
}
