package com.jbidwatcher.ui;

import com.jbidwatcher.config.JConfigTab;

import javax.swing.*;
import java.awt.event.*;
import java.awt.*;

public class SnipeDialog extends JDialog {
  private JPanel contentPane;
  private JButton buttonOK;
  private JButton buttonCancel;
  private JTextField quantityField, snipeAmount;
  private JCheckBox subtractShipping;
  private JLabel auctionInfo;
  private boolean cancelled=false;
  private JLabel quantityLabel;

  public SnipeDialog() {
    super((Frame)null, "Sniping", true, JMouseAdapter.getCurrentGraphicsConfiguration());
    setupUI();

    setContentPane(contentPane);
    getRootPane().setDefaultButton(buttonOK);
    setLocationRelativeTo(null);

    buttonOK.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        onOK();
      }
    });

    buttonCancel.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        onCancel();
      }
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
    cancelled = false;
    dispose();
  }

  public boolean isCancelled() { return cancelled; }
  public String getQuantity() { if(quantityField.isEnabled()) return quantityField.getText(); else return "1"; }
  public String getAmount() { return snipeAmount.getText().replace(',','.'); }
  public boolean subtractShipping() { return subtractShipping.isSelected(); }

  private void onCancel() {
    cancelled = true;
    setVisible(false);
  }

  public void useQuantity(boolean enable) {
    quantityLabel.setEnabled(enable);
    quantityField.setEnabled(enable);
  }

  public void setPrompt(String prompt) {
    auctionInfo.setText(prompt);
    auctionInfo.setVisible(true);
    auctionInfo.invalidate();
    contentPane.validate();
    validate();
  }

  public void clear() {
    getRootPane().setDefaultButton(buttonOK);
    quantityField.setText("1");
    snipeAmount.setText("");
  }

  private void setupUI() {
    contentPane = new JPanel(new SpringLayout());

    auctionInfo = new JLabel();
    auctionInfo.setText("Auction Information");
    //  The top section is the auction info.
    contentPane.add(auctionInfo);

    JPanel inputPane = new JPanel();
    inputPane.setLayout(new BoxLayout(inputPane, BoxLayout.Y_AXIS));

    snipeAmount = new JTextField(10);
    snipeAmount.setText("");

    quantityField = new JTextField(10);
    quantityField.setEnabled(false);
    quantityField.setText("1");

    subtractShipping = new JCheckBox();
    subtractShipping.setText("Auto-subtract shipping and insurance (p/p)");

    JPanel promptPane = new JPanel(new SpringLayout());
    JLabel snipeLabel;
    promptPane.add(snipeLabel = new JLabel("How much do you wish to snipe?", JLabel.TRAILING));
    snipeLabel.setLabelFor(snipeAmount);
    promptPane.add(snipeAmount);
    promptPane.add(quantityLabel = new JLabel("Quantity?", JLabel.TRAILING));
    quantityLabel.setLabelFor(quantityField);
    promptPane.add(quantityField);
    SpringUtilities.makeCompactGrid(promptPane, 2, 2, 6, 6, 6, 3);
    //inputPane.add(JConfigTab.makeLine(new JLabel("How much do you wish to snipe?"), snipeAmount));
    //inputPane.add(JConfigTab.makeLine(quantityLabel = new JLabel("Quantity?"), quantityField));
    //inputPane.add(subtractShipping);
    //contentPane.add(JConfigTab.panelPack(inputPane));
    contentPane.add(promptPane);
    contentPane.add(subtractShipping);

    buttonOK = new JButton();
    buttonOK.setText("OK");

    buttonCancel = new JButton();
    buttonCancel.setText("Cancel");

    JPanel bottomPanel = new JPanel();
    bottomPanel.setLayout(new BorderLayout());
    bottomPanel.add(JConfigTab.makeLine(buttonOK, buttonCancel), BorderLayout.EAST);
    contentPane.add(bottomPanel);
    SpringUtilities.makeCompactGrid(contentPane, 4, 1, 6, 6, 6, 6);
  }
}
