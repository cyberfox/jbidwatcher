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

import com.jbidwatcher.ui.JPasteListener;

import java.awt.*;
import java.awt.event.*;
import java.io.*;
import javax.swing.*;

public class JConfigFilePathTab extends JConfigTab {
  JTextField filePath;

  public String getTabName() { return("Paths"); }
  public void cancel() { }

  public boolean apply() {
    JConfig.setConfiguration("savefile", filePath.getText());

    return true;
  }

  public void updateValues() {
    filePath.setText(JConfig.queryConfiguration("savefile", JConfig.getCanonicalFile("auctions.xml", "jbidwatcher", false)));
  }

  private JPanel buildFilePathSettings() {
    JPanel tp = new JPanel();
	JLabel jl = new JLabel("What is the path to the auctions save file:");

    tp.setBorder(BorderFactory.createTitledBorder("Save File Path"));
    tp.setLayout(new BorderLayout());

    filePath = new JTextField();
    filePath.addMouseListener(JPasteListener.getInstance());
	filePath.setToolTipText("Full path and filename to load auctions save file from.");

    updateValues();

    filePath.setEditable(true);
    filePath.getAccessibleContext().setAccessibleName("Full path and filename to load auctions save file from.");
	tp.add(jl, BorderLayout.NORTH);

    JPanel qp = new JPanel();
    JButton browseButton = new JButton("Browse...");
    qp.setLayout(new BoxLayout(qp, BoxLayout.Y_AXIS));
    qp.add(filePath);
    qp.add(browseButton);

    browseButton.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent ae) {
          if(ae.getActionCommand().equals("Browse...")) {
            JFileChooser jfc = new JFileChooser();
            jfc.setCurrentDirectory(new File(JConfig.getHomeDirectory("jbidwatcher")));
            jfc.setApproveButtonText("Choose");
            int rval = jfc.showOpenDialog(null);
            if(rval == JFileChooser.APPROVE_OPTION) {
              try {
                filePath.setText(jfc.getSelectedFile().getCanonicalPath());
              } catch(IOException ioe) {
                filePath.setText(jfc.getSelectedFile().getAbsolutePath());
              }
            }
          }
        }
      });

    tp.add(qp, BorderLayout.SOUTH);

    return tp;
  }

  public JConfigFilePathTab() {
    super();
    this.setLayout(new BorderLayout());
    this.add(panelPack(buildFilePathSettings()), BorderLayout.NORTH);
  }
}
