package com.jbidwatcher.ui.config;
/*
 * Copyright (c) 2000-2007, CyberFOX Software, Inc. All Rights Reserved.
 *
 * Developed by mrs (Morgan Schweers)
 */

import com.jbidwatcher.ui.util.AutoCompletion;
import com.jbidwatcher.ui.util.JPasteListener;
import com.jbidwatcher.util.config.JConfig;

import java.util.*;
import javax.swing.*;
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;
import java.awt.BorderLayout;

/**
 * Created by IntelliJ IDEA.
 * User: Morgan Schweers
 * Date: Oct 9, 2005
 * Time: 10:14:38 PM
 *
 */
public class JConfigAdvancedTab extends JConfigTab {
  JComboBox configKey = null;
  JTextField configValue = null;
  private JButton setButton = null;
  private JButton delButton = null;

  public String getTabName() { return("Advanced"); }
  public void cancel() { }

  public void apply() {
    JConfig.setConfiguration((String)configKey.getSelectedItem(), configValue.getText());

  }

  public void updateValues() {
    buildNewConfigList(configKey, configValue);
    String gotVal = (String)configKey.getSelectedItem();
    if(gotVal == null || gotVal.length() == 0) {
      configValue.setText("");
    } else {
      configValue.setText(JConfig.queryConfiguration(gotVal, ""));
    }
  }

  private static HashSet<JComboBox> boxSet = new HashSet<JComboBox>();

  private void buildNewConfigList(final JComboBox box, final JTextField value) {
    box.removeAllItems();
    box.setEditable(true);
    box.addItem("");

    List<String> cfgKeys = JConfig.getAllKeys();
    for (Object cfgKey : cfgKeys) {
      String s = (String) cfgKey;
      if (s.indexOf("password") == -1) box.addItem(s);
    }

    if(!boxSet.contains(box)) {
      box.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          if(value != null && box != null) {
            String selected = (String)box.getSelectedItem();
            if(selected != null) {
              boolean isPassword = selected.indexOf("password") != -1;
              if(selected.length() == 0 || isPassword) {
                value.setEnabled(false);
                if(isPassword) {
                  value.setText("********");
                } else {
                  value.setText("");
                }
                setButton.setEnabled(false);
                delButton.setEnabled(false);
              } else {
                value.setEnabled(true);
                value.setText(JConfig.queryConfiguration(selected, ""));
                setButton.setEnabled(true);
                delButton.setEnabled(true);
              }
            }
          }
        }
      });
      AutoCompletion.enable(box);
      boxSet.add(box);
    }

    box.requestFocus();
  }

  private JPanel buildAdvancedConfiguration(JPasteListener pasteListener) {
    JPanel tp = new JPanel();

    setButton = new JButton("Set...");
    delButton = new JButton("Delete");

    tp.setBorder(BorderFactory.createTitledBorder("Advanced Configuration Editor"));
    tp.setLayout(new BoxLayout(tp, BoxLayout.Y_AXIS));

    configKey = new JComboBox();
    tp.add(new JLabel("Configuration Key"));

    configValue = new JTextField();
    configValue.addMouseListener(pasteListener);
    configValue.setToolTipText("The associated configuration value for the entered key.");
    configValue.setEditable(true);
    configValue.getAccessibleContext().setAccessibleName("The configuration value for the entered key.");
    JLabel jl = new JLabel("Configuration Value");

    buildNewConfigList(configKey, configValue);

    tp.add(configKey);
    tp.add(jl);

    updateValues();

    JPanel qp = new JPanel();
    Box pairBox = Box.createHorizontalBox();

    qp.setLayout(new BoxLayout(qp, BoxLayout.Y_AXIS));
    qp.add(configValue);
    pairBox.add(setButton);
    pairBox.add(Box.createHorizontalStrut(30));
    pairBox.add(delButton);
    qp.add(pairBox);

    setButton.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent ae) {
          if(ae.getActionCommand().equals("Set...")) {
            if( ((String)configKey.getSelectedItem()).length() != 0) {
              JConfig.setConfiguration((String)configKey.getSelectedItem(), configValue.getText());
              updateValues();
            }
          }
        }
      });
    delButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent ae) {
        if(ae.getActionCommand().equals("Delete")) {
          JConfig.kill((String)configKey.getSelectedItem());
          updateValues();
        }
      }
    });
    tp.add(qp, BorderLayout.SOUTH);

    return tp;
  }

  public JConfigAdvancedTab(JPasteListener pasteListener) {
    setLayout(new BorderLayout());
    add(panelPack(buildAdvancedConfiguration(pasteListener)), BorderLayout.NORTH);
  }
}
