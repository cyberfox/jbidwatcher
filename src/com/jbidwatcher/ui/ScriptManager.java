package com.jbidwatcher.ui;

import com.jbidwatcher.util.Scripting;

import javax.swing.*;
import java.awt.Dimension;
import java.awt.BorderLayout;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;

public class ScriptManager {
  private JPanel mPanel1;
  private JButton mExecuteButton;
  private JEditorPane mEditorPane1;
  private JTextArea mTextArea1;
  private static ScriptManager mSM = null;
  private JFrame mFrame = null;

  public static JFrame getNewScriptManager() {
    JFrame frame = new JFrame("JRuby Example");
    frame.setContentPane((mSM = new ScriptManager()).getPanel());
    frame.setMinimumSize(new Dimension(650, 400));
    frame.setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
    frame.pack();
    frame.setVisible(true);

    mSM.mFrame = frame;
    return frame;
  }

  public void show() { mFrame.setVisible(true); }

  public void hide() { mFrame.setVisible(false); }

  public static ScriptManager getScriptManager() { return mSM; }

  public ScriptManager() {
    prepUI();
//    Scripting.setGlobal("output", mTextArea1);

    Scripting.ruby("def set_output(x)\n  $output = x\nend");
    Scripting.rubyMethod("set_output", mTextArea1);
    mEditorPane1.addKeyListener(new KeyAdapter() {
      public void keyPressed(KeyEvent e) {
        if (e.isControlDown() && e.getKeyCode() == KeyEvent.VK_ENTER) executeEditor();
        else super.keyPressed(e);
      }
    });
    mExecuteButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        executeEditor();
      }
    });
  }

  private void executeEditor() {
    String code = mEditorPane1.getSelectedText();

    if (code != null) {
      Scripting.ruby("$output.setText((" + code + ").inspect)");
    } else {
      code = mEditorPane1.getText();
      Scripting.ruby("$output.setText((" + code + ").inspect)");
    }
  }

  public JPanel getPanel() {
    return mPanel1;
  }

  private void prepUI() {
    mPanel1 = new JPanel();
    mPanel1.setLayout(new BorderLayout(0, 0));
    final JSplitPane splitPane1 = new JSplitPane();
    splitPane1.setOrientation(0);
    mPanel1.add(splitPane1, BorderLayout.CENTER);
    final JPanel panel1 = new JPanel();
    panel1.setLayout(new BorderLayout(0, 0));
    splitPane1.setLeftComponent(panel1);
    final JPanel panel2 = new JPanel();
    panel2.setLayout(new BorderLayout(0, 0));
    panel1.add(panel2, BorderLayout.SOUTH);
    mExecuteButton = new JButton();
    mExecuteButton.setHorizontalAlignment(0);
    mExecuteButton.setName("Execute");
    mExecuteButton.setText("Execute");
    mExecuteButton.setMnemonic('E');
    mExecuteButton.setDisplayedMnemonicIndex(0);
    panel2.add(mExecuteButton, BorderLayout.EAST);
    final JScrollPane scrollPane1 = new JScrollPane();
    panel1.add(scrollPane1, BorderLayout.CENTER);
    mEditorPane1 = new JEditorPane();
    mEditorPane1.setName("Editor");
    mEditorPane1.setMinimumSize(new Dimension(500, 110));
    scrollPane1.setViewportView(mEditorPane1);
    mTextArea1 = new JTextArea();
    mTextArea1.setName("Output");
    splitPane1.setRightComponent(mTextArea1);
  }
}
