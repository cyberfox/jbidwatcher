package com.jbidwatcher.ui.config;
/*
 * Copyright (c) 2000-2007, CyberFOX Software, Inc. All Rights Reserved.
 *
 * Developed by mrs (Morgan Schweers)
 */

import com.cyberfox.util.platform.Path;
import com.jbidwatcher.ui.util.JPasteListener;
import com.jbidwatcher.util.config.JConfig;

import java.awt.*;
import java.awt.event.*;
import java.io.*;
import javax.swing.*;

public class JConfigFilePathTab extends JConfigTab {
  JTextField filePath;

  public String getTabName() { return("Paths"); }
  public void cancel() { }

  public void apply() {
    JConfig.setConfiguration("savefile", filePath.getText());

  }

  public void updateValues() {
    filePath.setText(JConfig.queryConfiguration("savefile", Path.getCanonicalFile("auctions.xml", "jbidwatcher", false)));
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
            jfc.setCurrentDirectory(new File(JConfig.getHomeDirectory()));
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
