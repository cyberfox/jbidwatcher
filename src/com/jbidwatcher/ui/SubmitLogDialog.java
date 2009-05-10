package com.jbidwatcher.ui;

import com.jbidwatcher.my.MyJBidwatcher;

import javax.swing.*;
import java.awt.event.*;
import java.awt.Dimension;

public class SubmitLogDialog extends JDialog {
  private JPanel contentPane;
  private JButton buttonOK;
  private JButton buttonCancel;
  private JTextField mEmailAddressTextField;
  private JTextArea mProblemTextArea;

  public SubmitLogDialog() {
    setContentPane(contentPane);
    contentPane.setMinimumSize(new Dimension(442, 338));
    setModal(true);
    getRootPane().setDefaultButton(buttonOK);

    buttonOK.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {onOK();}
    });

    buttonCancel.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {onCancel();}
    });

// call onCancel() when cross is clicked
    setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
    addWindowListener(new WindowAdapter() {
      public void windowClosing(WindowEvent e) {
        onCancel();
      }
    });

// call onCancel() on ESCAPE
    contentPane.registerKeyboardAction(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        onCancel();
      }
    }, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
  }

  private void onOK() {
    MyJBidwatcher.getInstance().sendLogFile();
    dispose();
  }

  private void onCancel() {
// add your code here if necessary
    dispose();
  }

  public static void main(String[] args) {
    SubmitLogDialog dialog = new SubmitLogDialog();
    dialog.pack();
    dialog.setVisible(true);
    System.exit(0);
  }
}
