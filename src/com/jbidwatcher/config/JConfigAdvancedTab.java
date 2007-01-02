package com.jbidwatcher.config;
/*
 * Copyright (c) 2000-2005 CyberFOX Software, Inc. All Rights Reserved.
 *
 * This library is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Library General Public License as published
 * by the Free Software Foundation; either version 2 of the License, or (at
 * your option) any later version.
 *
 * This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Library General Public License for more
 * details.
 *
 * You should have received a copy of the GNU Library General Public License
 * along with this library; if not, write to the
 *  Free Software Foundation, Inc.
 *  59 Temple Place
 *  Suite 330
 *  Boston, MA 02111-1307
 *  USA
 */

import com.jbidwatcher.ui.AutoCompletion;
import com.jbidwatcher.ui.JPasteListener;
import com.jbidwatcher.util.html.JHTML;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;
import java.util.HashSet;

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

  public boolean apply() {
    JConfig.setConfiguration((String)configKey.getSelectedItem(), configValue.getText());

    return true;
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

    java.util.List cfgKeys = JConfig.getAllKeys();
    for (Object cfgKey : cfgKeys) {
      String s = (String) cfgKey;
      if (s.indexOf(JHTML.Form.FORM_PASSWORD) == -1) box.addItem(s);
    }

    if(!boxSet.contains(box)) {
      box.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          if(configValue != null && configKey != null) {
            String selected = (String)configKey.getSelectedItem();
            if(selected != null) {
              boolean isPassword = selected.indexOf(JHTML.Form.FORM_PASSWORD) != -1;
              if(selected.length() == 0 || isPassword) {
                configValue.setEnabled(false);
                if(isPassword) {
                  configValue.setText("********");
                } else {
                  configValue.setText("");
                }
                setButton.setEnabled(false);
                delButton.setEnabled(false);
              } else {
                configValue.setEnabled(true);
                configValue.setText(JConfig.queryConfiguration(selected, ""));
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

  private JPanel buildAdvancedConfiguration() {
    JPanel tp = new JPanel();

    setButton = new JButton("Set...");
    delButton = new JButton("Delete");

    tp.setBorder(BorderFactory.createTitledBorder("Advanced Configuration Editor"));
    tp.setLayout(new BoxLayout(tp, BoxLayout.Y_AXIS));

    configKey = new JComboBox();
    buildNewConfigList(configKey, configValue);
    tp.add(new JLabel("Configuration Key"));
    tp.add(configKey);

    configValue = new JTextField();
    configValue.addMouseListener(JPasteListener.getInstance());
    configValue.setToolTipText("The associated configuration value for the entered key.");
    configValue.setEditable(true);
    configValue.getAccessibleContext().setAccessibleName("The configuration value for the entered key.");
    JLabel jl = new JLabel("Configuration Value");
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

  public JConfigAdvancedTab() {
    setLayout(new BorderLayout());
    add(panelPack(buildAdvancedConfiguration()), BorderLayout.NORTH);
  }
}
